package br.com.jhcomp.planner.video.motores;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Servidor de motores: roda no Windows e executa a voz, a sincronia labial e a transcrição
 * a pedido do backend, que fica no Docker.
 *
 * <p>Existe porque um contêiner não consegue iniciar programas do Windows nem do WSL. Não
 * tem regra de negócio, banco nem login de usuário: recebe "rode a etapa X do projeto N com
 * estes arquivos", executa o comando configurado para X e conta como foi. Fila, histórico,
 * progresso e cancelamento continuam no backend.</p>
 *
 * <p>Só usa o JDK, para rodar direto do arquivo-fonte, sem build:
 * {@code java ServidorMotores.java} (ver scripts/motores.ps1).</p>
 *
 * <p>Protocolo (toda chamada leva o cabeçalho {@code X-Motores-Chave}):</p>
 * <ul>
 *   <li>{@code GET /saude} — etapas com motor configurado;</li>
 *   <li>{@code POST /jobs?etapa=VOZ&projeto=7&text=roteiro.txt&out=audio-….wav} — inicia;</li>
 *   <li>{@code GET /jobs/{id}} — estado, código de saída e o fim da saída do motor;</li>
 *   <li>{@code DELETE /jobs/{id}} — encerra o motor e os processos filhos;</li>
 *   <li>{@code POST /ativo?valor=false} — desliga pelo site (true liga de novo).</li>
 * </ul>
 */
public final class ServidorMotores {

    static final String CABECALHO_CHAVE = "X-Motores-Chave";
    static final List<String> ETAPAS = List.of("VOZ", "LIPSYNC", "LEGENDAS");

    /** Nomes que o próprio backend gera: prefixo + UUID + extensão, ou o roteiro.txt. */
    private static final Pattern NOME_ARQUIVO = Pattern.compile(
            "[a-z]+(-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?\\.[a-z0-9]{2,4}");
    private static final Set<String> ENTRADAS = Set.of("text", "audio", "video");
    private static final int FIM_DO_LOG = 8_000;

    private final Path pasta;
    private final byte[] chave;
    private final Map<String, String> modelos;
    private final Duration limite;

    private record Job(String id, Process processo, Path log) {
    }

    /** O último job: um só por vez, porque os motores dividem a mesma placa de vídeo. */
    private Job atual;

    /**
     * Desligado pelo botão do site: o processo continua de pé (sem ele não haveria quem
     * recebesse o "ligar" de volta), mas recusa trabalho novo. Parado, quase não custa nada:
     * cada motor é um processo que nasce no pedido e devolve a placa de vídeo ao terminar.
     */
    private volatile boolean ativo = true;

    public ServidorMotores(Path pasta, String chave, Map<String, String> modelos, Duration limite) {
        this.pasta = pasta.toAbsolutePath().normalize();
        this.chave = chave.getBytes(StandardCharsets.UTF_8);
        this.modelos = Map.copyOf(modelos);
        this.limite = limite;
    }

    public static void main(String[] args) throws IOException {
        String chave = ambiente("MOTORES_CHAVE", "");
        if (chave.length() < 16) {
            System.err.println("Defina MOTORES_CHAVE com pelo menos 16 caracteres (a mesma VIDEO_ENGINE_KEY do .env).");
            System.exit(1);
        }
        Map<String, String> modelos = new LinkedHashMap<>();
        for (String etapa : ETAPAS) {
            String modelo = ambiente("MOTOR_" + etapa, "");
            if (!modelo.isBlank()) {
                modelos.put(etapa, modelo);
            }
        }
        int porta = Integer.parseInt(ambiente("MOTORES_PORTA", "8095"));
        ServidorMotores servidor = new ServidorMotores(Path.of(ambiente("MOTORES_PASTA", "uploads/video")),
                chave, modelos, Duration.ofMinutes(Long.parseLong(ambiente("MOTORES_LIMITE_MIN", "120"))));
        servidor.iniciar(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta));
        System.out.println("Motores no ar em 127.0.0.1:" + porta + " — etapas " + modelos.keySet()
                + ", pasta " + servidor.pasta);

        int portaBackend = Integer.parseInt(ambiente("MOTORES_BACKEND_PORTA", "0"));
        if (portaBackend > 0) {
            servidor.vigiarBackend(portaBackend, Duration.ofSeconds(Long.parseLong(ambiente("MOTORES_VIGIA_SEG", "30"))));
        }
    }

    /**
     * Sai quando o backend some — o Docker Desktop foi fechado —, para não deixar memória presa
     * com ninguém para chamar. Duas checagens seguidas sem resposta, para um restart do
     * contêiner não derrubar os motores. Quem liga de novo é a checagem do motores.ps1.
     */
    void vigiarBackend(int portaBackend, Duration intervalo) {
        Thread vigia = new Thread(() -> {
            int falhas = 0;
            while (falhas < 2) {
                try {
                    Thread.sleep(intervalo.toMillis());
                } catch (InterruptedException ex) {
                    return;
                }
                falhas = respondeLocal(portaBackend) ? 0 : falhas + 1;
            }
            synchronized (this) {
                if (atual != null) {
                    encerrar(atual.processo());
                }
            }
            System.out.println("Backend fora do ar (Docker fechado?): motores desligados.");
            System.exit(0);
        }, "motor-vigia-backend");
        vigia.setDaemon(true);
        vigia.start();
    }

    private static boolean respondeLocal(int porta) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 2_000);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /** Só no loopback: o Docker chega por host.docker.internal; a rede local, não. */
    public HttpServer iniciar(InetSocketAddress endereco) throws IOException {
        HttpServer servidor = HttpServer.create(endereco, 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.start();
        return servidor;
    }

    private void atender(HttpExchange troca) throws IOException {
        try {
            String chaveRecebida = troca.getRequestHeaders().getFirst(CABECALHO_CHAVE);
            if (chaveRecebida == null
                    || !MessageDigest.isEqual(chave, chaveRecebida.getBytes(StandardCharsets.UTF_8))) {
                responder(troca, 401, erro("Chave dos motores inválida. Confira VIDEO_ENGINE_KEY no .env."));
                return;
            }
            String metodo = troca.getRequestMethod();
            String caminho = troca.getRequestURI().getPath();

            if (metodo.equals("GET") && caminho.equals("/saude")) {
                responder(troca, 200, saude());
            } else if (metodo.equals("POST") && caminho.equals("/ativo")) {
                ativo = !"false".equals(consulta(troca.getRequestURI().getRawQuery()).get("valor"));
                responder(troca, 200, saude());
            } else if (metodo.equals("POST") && caminho.equals("/jobs")) {
                iniciarJob(troca);
            } else if (metodo.equals("GET") && caminho.startsWith("/jobs/")) {
                estado(troca, caminho.substring("/jobs/".length()));
            } else if (metodo.equals("DELETE") && caminho.startsWith("/jobs/")) {
                cancelar(troca, caminho.substring("/jobs/".length()));
            } else {
                responder(troca, 404, erro("Rota desconhecida."));
            }
        } catch (IllegalArgumentException ex) {
            responder(troca, 400, erro(ex.getMessage()));
        } catch (RuntimeException ex) {
            responder(troca, 500, erro("Falha no servidor de motores: " + ex));
        } finally {
            troca.close();
        }
    }

    private String saude() {
        StringBuilder json = new StringBuilder("{\"ativo\":").append(ativo);
        for (String etapa : ETAPAS) {
            json.append(",\"").append(etapa).append("\":").append(modelos.containsKey(etapa));
        }
        return json.append('}').toString();
    }

    private void iniciarJob(HttpExchange troca) throws IOException {
        Map<String, String> parametros = consulta(troca.getRequestURI().getRawQuery());
        String etapa = parametros.remove("etapa");
        String projeto = parametros.remove("projeto");

        if (!ativo) {
            responder(troca, 409, erro("Os motores estão desligados. Ligue-os pelo Video Studio."));
            return;
        }
        String modelo = modelos.get(etapa);
        if (modelo == null) {
            throw new IllegalArgumentException("Esta máquina não tem motor de " + etapa + " configurado.");
        }
        if (projeto == null || !projeto.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Projeto inválido.");
        }
        Path diretorio = pasta.resolve(projeto).normalize();
        if (!diretorio.startsWith(pasta) || !Files.isDirectory(diretorio)) {
            throw new IllegalArgumentException("A pasta do projeto " + projeto + " não existe aqui: "
                    + "o backend e os motores precisam enxergar a mesma pasta de uploads.");
        }
        if (!parametros.containsKey("out")) {
            throw new IllegalArgumentException("Falta o arquivo de saída.");
        }
        // O que chega aqui vira argumento de um programa: só nomes gerados pelo backend, e as
        // entradas precisam existir dentro da pasta do projeto.
        for (var parametro : parametros.entrySet()) {
            String nome = parametro.getKey();
            String valor = parametro.getValue();
            if (!ENTRADAS.contains(nome) && !nome.equals("out")) {
                throw new IllegalArgumentException("Parâmetro desconhecido: " + nome);
            }
            if (!NOME_ARQUIVO.matcher(valor).matches()) {
                throw new IllegalArgumentException("Nome de arquivo recusado: " + valor);
            }
            if (ENTRADAS.contains(nome) && !Files.isRegularFile(diretorio.resolve(valor))) {
                throw new IllegalArgumentException("Arquivo de entrada não encontrado: " + valor);
            }
        }
        List<String> comando = expandir(modelo, parametros);

        synchronized (this) {
            if (atual != null && atual.processo().isAlive()) {
                responder(troca, 409, erro("Outro motor já está rodando: a placa de vídeo só comporta um por vez."));
                return;
            }
            if (atual != null) {
                Files.deleteIfExists(atual.log());
            }
            Path log = Files.createTempFile("jhcomp-motor-", ".log");
            Process processo;
            try {
                processo = new ProcessBuilder(comando)
                        .directory(diretorio.toFile())
                        .redirectErrorStream(true)
                        // Para arquivo, não para um pipe: um pipe cheio travaria o motor.
                        .redirectOutput(log.toFile())
                        .start();
            } catch (IOException ex) {
                Files.deleteIfExists(log);
                throw new IllegalArgumentException("Não foi possível iniciar '" + comando.get(0) + "': " + ex.getMessage());
            }
            atual = new Job(UUID.randomUUID().toString(), processo, log);
            vigiarLimite(processo);
            responder(troca, 202, "{\"id\":\"" + atual.id() + "\"}");
        }
    }

    /** Rede de segurança: se o backend sumir no meio do job, o motor não segura a GPU para sempre. */
    private void vigiarLimite(Process processo) {
        Thread vigia = new Thread(() -> {
            try {
                if (!processo.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
                    encerrar(processo);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "motor-limite");
        vigia.setDaemon(true);
        vigia.start();
    }

    private void estado(HttpExchange troca, String id) throws IOException {
        Job job = doId(id);
        if (job == null) {
            responder(troca, 404, erro("Job desconhecido."));
            return;
        }
        Process processo = job.processo();
        String estado = processo.isAlive() ? "EXECUTANDO" : processo.exitValue() == 0 ? "CONCLUIDO" : "ERRO";
        String codigo = processo.isAlive() ? "null" : String.valueOf(processo.exitValue());
        responder(troca, 200, "{\"estado\":\"" + estado + "\",\"codigo\":" + codigo
                + ",\"log\":" + texto(fimDoLog(job.log())) + "}");
    }

    private void cancelar(HttpExchange troca, String id) throws IOException {
        Job job = doId(id);
        if (job != null) {
            encerrar(job.processo());
        }
        responder(troca, 204, null);
    }

    private synchronized Job doId(String id) {
        return atual != null && atual.id().equals(id) ? atual : null;
    }

    /** "wsl ..." e scripts .bat deixam o motor de verdade num processo filho. */
    private static void encerrar(Process processo) {
        processo.descendants().forEach(ProcessHandle::destroyForcibly);
        processo.destroyForcibly();
    }

    /**
     * Troca os marcadores do modelo pelos nomes de arquivo. Aspas agrupam um argumento com
     * espaços ("C:\Program Files\..."); o valor de um marcador nunca é reinterpretado.
     */
    public static List<String> expandir(String modelo, Map<String, String> valores) {
        List<String> argumentos = new ArrayList<>();
        for (String token : separar(modelo)) {
            String argumento = token;
            for (var valor : valores.entrySet()) {
                argumento = argumento.replace("{" + valor.getKey() + "}", valor.getValue());
            }
            if (argumento.matches(".*\\{[a-z]+}.*")) {
                throw new IllegalArgumentException("O modelo do motor usa um marcador que o pedido não trouxe: " + token);
            }
            argumentos.add(argumento);
        }
        return argumentos;
    }

    private static List<String> separar(String modelo) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        boolean temParte = false;
        for (char c : modelo.toCharArray()) {
            if (c == '"') {
                entreAspas = !entreAspas;
                temParte = true;
            } else if (Character.isWhitespace(c) && !entreAspas) {
                if (temParte) {
                    partes.add(atual.toString());
                    atual.setLength(0);
                    temParte = false;
                }
            } else {
                atual.append(c);
                temParte = true;
            }
        }
        if (entreAspas) {
            throw new IllegalArgumentException("Aspas sem fechar no modelo do motor: " + modelo);
        }
        if (temParte) {
            partes.add(atual.toString());
        }
        return partes;
    }

    private static Map<String, String> consulta(String bruta) {
        Map<String, String> parametros = new LinkedHashMap<>();
        if (bruta == null || bruta.isBlank()) {
            return parametros;
        }
        for (String par : bruta.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                parametros.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return parametros;
    }

    /** Só o fim: uma barra de progresso de minutos escreve megabytes. */
    private static String fimDoLog(Path log) {
        try (SeekableByteChannel canal = Files.newByteChannel(log)) {
            long inicio = Math.max(0, canal.size() - FIM_DO_LOG);
            ByteBuffer buffer = ByteBuffer.allocate((int) (canal.size() - inicio));
            canal.position(inicio);
            while (buffer.hasRemaining() && canal.read(buffer) > 0) {
                // lê até encher
            }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static String erro(String mensagem) {
        return "{\"erro\":" + texto(mensagem) + "}";
    }

    /** String JSON com escape: o log do motor tem aspas, barras e caracteres de controle. */
    static String texto(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws IOException {
        if (corpo == null) {
            troca.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private static String ambiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
