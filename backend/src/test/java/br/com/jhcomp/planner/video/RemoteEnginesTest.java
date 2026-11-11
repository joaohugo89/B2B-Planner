package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.video.EngineRunner.EngineException;
import br.com.jhcomp.planner.video.VideoJob.Step;
import br.com.jhcomp.planner.video.motores.ServidorMotores;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backend e servidor de motores conversando de verdade, por HTTP, com o FakeEngine no lugar
 * da voz e da sincronia. É o caminho do arranjo "backend no Docker, motores no Windows".
 */
class RemoteEnginesTest {

    private static final String CHAVE = "chave-de-teste-com-tamanho-ok";
    private static final String SAIDA = "audio-0f8fad5b-d9cb-469f-a165-70867728950e.wav";

    @TempDir
    Path pasta;

    private HttpServer servidor;
    private RemoteEngines remoto;
    private Path projeto;

    /** Aspas porque java.home e o classpath podem ter espaço ("Program Files", "B2B Planner"). */
    private static String motor(String modo) {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        return "\"" + java + "\" -cp \"" + System.getProperty("java.class.path") + "\" "
                + FakeEngine.class.getName() + " " + modo + " {out}";
    }

    @BeforeEach
    void subir() throws Exception {
        projeto = Files.createDirectories(pasta.resolve("7"));
        Files.writeString(projeto.resolve("roteiro.txt"), "Olá.");

        servidor = new ServidorMotores(pasta, CHAVE,
                Map.of("VOZ", motor("progress"), "LEGENDAS", motor("fail"), "LIPSYNC", motor("slow")),
                Duration.ofMinutes(5))
                .iniciar(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        remoto = cliente(CHAVE);
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    private RemoteEngines cliente(String chave) {
        VideoProperties properties = new VideoProperties();
        properties.setEngineUrl("http://127.0.0.1:" + servidor.getAddress().getPort());
        properties.setEngineKey(chave);
        return new RemoteEngines(properties, new ObjectMapper(), Duration.ofMillis(200));
    }

    private String rodar(Step step, Map<String, String> valores, Duration limite,
                         AtomicReference<Runnable> parar, List<Integer> progresso) {
        return remoto.run(step, 7L, valores, projeto.resolve(valores.getOrDefault("out", SAIDA)), limite,
                parar::set, progresso::add);
    }

    @Test
    @DisplayName("informa quais etapas têm motor do lado de lá")
    void saude() {
        assertThat(remoto.available())
                .containsEntry(Step.VOZ, true).containsEntry(Step.LEGENDAS, true).containsEntry(Step.LIPSYNC, true);
    }

    @Test
    @DisplayName("roda a etapa, repassa a porcentagem e devolve a saída do motor")
    void sucesso() {
        List<Integer> progresso = new CopyOnWriteArrayList<>();

        String log = rodar(Step.VOZ, Map.of("text", "roteiro.txt", "out", SAIDA), Duration.ofSeconds(30),
                new AtomicReference<>(), progresso);

        assertThat(projeto.resolve(SAIDA)).exists();
        assertThat(log).contains("motor falso: progress");
        assertThat(progresso).isNotEmpty().isSubsetOf(40, 80);
    }

    @Test
    @DisplayName("motor que falha volta como erro, com o que ele escreveu")
    void falha() {
        assertThatThrownBy(() -> rodar(Step.LEGENDAS,
                Map.of("audio", "roteiro.txt", "out", "subtitles-0f8fad5b-d9cb-469f-a165-70867728950e.srt"),
                Duration.ofSeconds(30), new AtomicReference<>(), new CopyOnWriteArrayList<>()))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("código 3")
                .satisfies(ex -> assertThat(((EngineException) ex).log()).contains("modelo nao encontrado"));
    }

    @Test
    @DisplayName("cancelar encerra o motor do lado de lá")
    void cancelamento() throws Exception {
        AtomicReference<Runnable> parar = new AtomicReference<>();
        Thread cancela = new Thread(() -> {
            while (parar.get() == null) {
                Thread.onSpinWait();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignorado) {
                return;
            }
            parar.get().run();
        });
        cancela.start();

        assertThatThrownBy(() -> rodar(Step.LIPSYNC,
                Map.of("video", "roteiro.txt", "audio", "roteiro.txt",
                        "out", "lipsync-0f8fad5b-d9cb-469f-a165-70867728950e.mp4"),
                Duration.ofSeconds(30), parar, new CopyOnWriteArrayList<>()))
                .isInstanceOf(EngineException.class);
        cancela.join();
    }

    @Test
    @DisplayName("nome de arquivo fora do padrão nunca chega a virar argumento")
    void recusaNomes() {
        for (String nome : List.of("../8/roteiro.txt", "x.txt & calc", "roteiro.txt\" -oops")) {
            assertThatThrownBy(() -> rodar(Step.VOZ, Map.of("text", nome, "out", SAIDA), Duration.ofSeconds(5),
                    new AtomicReference<>(), new CopyOnWriteArrayList<>()))
                    .isInstanceOf(EngineException.class)
                    .hasMessageContaining("recusado");
        }
        assertThatThrownBy(() -> rodar(Step.VOZ, Map.of("text", "audio.wav", "out", SAIDA), Duration.ofSeconds(5),
                new AtomicReference<>(), new CopyOnWriteArrayList<>()))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("não encontrado");
        assertThat(projeto.resolve(SAIDA)).doesNotExist();
    }

    @Test
    @DisplayName("um motor por vez: o segundo pedido espera a placa de vídeo liberar")
    void umPorVez() throws Exception {
        AtomicReference<Runnable> parar = new AtomicReference<>();
        Thread lento = new Thread(() -> {
            try {
                rodar(Step.LIPSYNC, Map.of("video", "roteiro.txt", "audio", "roteiro.txt",
                                "out", "lipsync-0f8fad5b-d9cb-469f-a165-70867728950e.mp4"),
                        Duration.ofSeconds(30), parar, new CopyOnWriteArrayList<>());
            } catch (EngineException esperado) {
                // cancelado no fim do teste
            }
        });
        lento.start();
        while (parar.get() == null) {
            Thread.onSpinWait();
        }

        assertThatThrownBy(() -> rodar(Step.VOZ, Map.of("text", "roteiro.txt", "out", SAIDA), Duration.ofSeconds(5),
                new AtomicReference<>(), new CopyOnWriteArrayList<>()))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("já está rodando");

        parar.get().run();
        lento.join();
    }

    @Test
    @DisplayName("chave errada e servidor desligado viram etapas indisponíveis, sem exceção na tela")
    void semAcesso() {
        assertThat(cliente("chave-errada-mas-comprida").available().values()).containsOnly(false);

        servidor.stop(0);
        assertThat(remoto.available().values()).containsOnly(false);
        assertThatThrownBy(() -> rodar(Step.VOZ, Map.of("text", "roteiro.txt", "out", SAIDA), Duration.ofSeconds(5),
                new AtomicReference<>(), new CopyOnWriteArrayList<>()))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("motores.ps1");
    }

    @Test
    @DisplayName("modelo com aspas mantém caminho com espaço num argumento só")
    void aspasNoModelo() {
        assertThat(ServidorMotores.expandir("\"C:\\Program Files\\x.exe\" {audio} {out}",
                Map.of("audio", "a b.wav", "out", "o.srt")))
                .containsExactly("C:\\Program Files\\x.exe", "a b.wav", "o.srt");
    }
}
