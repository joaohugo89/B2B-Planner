package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.video.EngineRunner.EngineException;
import br.com.jhcomp.planner.video.VideoJob.Step;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Os motores de IA que rodam no Windows, pelo servidor de motores (scripts/motores.ps1).
 *
 * <p>O backend fica no Docker e não consegue iniciar programas do Windows nem do WSL; em vez
 * de rodar a voz, a sincronia e a transcrição, ele pede ao servidor e acompanha. Os arquivos
 * não viajam pela rede: os dois lados enxergam a mesma pasta de uploads, e o pedido leva só
 * os nomes.</p>
 *
 * <p>Ligado quando VIDEO_ENGINE_URL está definida; sem ela, o backend roda os motores ele
 * mesmo, pelos comandos locais (EngineRunner).</p>
 */
@Component
public class RemoteEngines {

    private static final List<Step> ETAPAS = List.of(Step.VOZ, Step.LIPSYNC, Step.LEGENDAS);
    private static final String CABECALHO_CHAVE = "X-Motores-Chave";
    private static final String DESLIGADOS =
            "Os motores de IA não estão ligados no Windows. Rode scripts\\motores.ps1 e tente de novo.";

    private final VideoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Duration intervalo;

    @org.springframework.beans.factory.annotation.Autowired
    public RemoteEngines(VideoProperties properties, ObjectMapper mapper) {
        this(properties, mapper, Duration.ofSeconds(1));
    }

    /** Usado nos testes, para não esperar um segundo a cada leitura do andamento. */
    RemoteEngines(VideoProperties properties, ObjectMapper mapper, Duration intervalo) {
        this.properties = properties;
        this.mapper = mapper;
        this.intervalo = intervalo;
    }

    public boolean configured() {
        return !properties.getEngineUrl().isBlank();
    }

    /** noAr: o servidor responde. ligado: aceita trabalho (o botão do site não o desligou). */
    public record Estado(boolean noAr, boolean ligado) {
    }

    public Estado estado() {
        try {
            return new Estado(true, enviar(pedido("/saude").GET().timeout(Duration.ofSeconds(3)).build())
                    .path("ativo").asBoolean(true));
        } catch (EngineException desligado) {
            return new Estado(false, false);
        }
    }

    /** O botão do site. Com o servidor fora do ar, não há o que ligar: a tela explica. */
    public Estado ligar(boolean ligado) {
        enviar(pedido("/ativo?valor=" + ligado).POST(HttpRequest.BodyPublishers.noBody()).build());
        return estado();
    }

    /** Etapas com motor no Windows agora; tudo falso quando o servidor está desligado. */
    public Map<Step, Boolean> available() {
        Map<Step, Boolean> etapas = new EnumMap<>(Step.class);
        ETAPAS.forEach(etapa -> etapas.put(etapa, false));
        try {
            JsonNode saude = enviar(pedido("/saude").GET().timeout(Duration.ofSeconds(3)).build());
            boolean ativo = saude.path("ativo").asBoolean(true);
            ETAPAS.forEach(etapa -> etapas.put(etapa, ativo && saude.path(etapa.name()).asBoolean(false)));
        } catch (EngineException ignorado) {
            // Desligado ou chave errada: a tela mostra as etapas como indisponíveis.
        }
        return etapas;
    }

    /**
     * Roda a etapa no Windows e espera, com o mesmo contrato do EngineRunner: devolve o fim da
     * saída do motor ou lança EngineException com a explicação.
     *
     * @param onStart recebe a ação que cancela o motor, para quem quiser interrompê-lo
     */
    public String run(Step step, Long projectId, Map<String, String> values, Path expectedOutput,
                      Duration timeout, Consumer<Runnable> onStart, IntConsumer onProgress) {
        String consulta = "etapa=" + step.name() + "&projeto=" + projectId + "&" + values.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String id = enviar(pedido("/jobs?" + consulta).POST(HttpRequest.BodyPublishers.noBody()).build())
                .path("id").asText();
        Runnable cancelar = () -> cancelar(id);
        onStart.accept(cancelar);

        long limite = System.nanoTime() + timeout.toNanos();
        Progress progress = new Progress();
        Integer ultimo = null;
        String log = "";
        try {
            while (System.nanoTime() < limite) {
                Thread.sleep(intervalo.toMillis());
                JsonNode estado = enviar(pedido("/jobs/" + id).GET().build());
                log = estado.path("log").asText("");

                Integer atual = EngineRunner.medir(progress, log);
                if (atual != null && !atual.equals(ultimo)) {
                    ultimo = atual;
                    onProgress.accept(atual);
                }

                switch (estado.path("estado").asText()) {
                    case "CONCLUIDO" -> {
                        if (!temConteudo(expectedOutput)) {
                            throw new EngineException("O motor terminou sem gerar o arquivo de saída.", log);
                        }
                        return log;
                    }
                    case "ERRO" -> throw new EngineException("O motor terminou com erro (código "
                            + estado.path("codigo").asText("?") + ").", log);
                    default -> {
                        // ainda executando
                    }
                }
            }
        } catch (InterruptedException ex) {
            cancelar.run();
            Thread.currentThread().interrupt();
            throw new EngineException("A etapa foi interrompida.", log);
        } catch (EngineException ex) {
            // Perdeu o contato no meio: sem isto o motor seguiria rodando sem ninguém esperando.
            cancelar.run();
            throw ex.log() == null ? new EngineException(ex.getMessage(), log) : ex;
        }
        cancelar.run();
        throw new EngineException("A etapa passou do tempo limite de " + timeout.toMinutes()
                + " min e foi encerrada.", log);
    }

    private void cancelar(String id) {
        try {
            http.send(pedido("/jobs/" + id).DELETE().timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException ignorado) {
            // Servidor fora do ar: o limite do próprio servidor encerra o motor.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest.Builder pedido(String caminho) {
        String base = properties.getEngineUrl().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(base + caminho))
                .header(CABECALHO_CHAVE, properties.getEngineKey())
                .timeout(Duration.ofSeconds(15));
    }

    private JsonNode enviar(HttpRequest pedido) {
        HttpResponse<String> resposta;
        try {
            resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new EngineException(DESLIGADOS, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EngineException("A etapa foi interrompida.", null);
        }
        JsonNode corpo = ler(resposta.body());
        if (resposta.statusCode() >= 300) {
            throw new EngineException(corpo.path("erro").asText("O servidor de motores recusou o pedido (HTTP "
                    + resposta.statusCode() + ")."), null);
        }
        return corpo;
    }

    private JsonNode ler(String corpo) {
        try {
            return corpo == null || corpo.isBlank() ? mapper.createObjectNode() : mapper.readTree(corpo);
        } catch (IOException ex) {
            throw new EngineException("O servidor de motores respondeu em um formato inesperado.", corpo);
        }
    }

    private static boolean temConteudo(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException ex) {
            return false;
        }
    }
}
