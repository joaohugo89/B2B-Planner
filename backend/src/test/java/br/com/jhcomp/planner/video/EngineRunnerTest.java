package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.video.EngineRunner.EngineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O EngineRunner é a fronteira com programas que o backend não controla. O que importa é
 * que toda forma de dar errado — código de saída, saída ausente, travamento — vire um erro
 * com explicação, e não um job eternamente "executando".
 */
class EngineRunnerTest {

    private final EngineRunner runner = new EngineRunner();

    @TempDir
    Path dir;

    private List<String> motor(String modo) {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "-cp", System.getProperty("java.class.path"),
                FakeEngine.class.getName(), modo, "saida.bin");
    }

    private String rodar(String modo, Duration limite) {
        return runner.run(motor(modo), dir, dir.resolve("saida.bin"), limite, processo -> { }, porcento -> { });
    }

    @Test
    @DisplayName("motor que termina bem deixa o arquivo e devolve o que escreveu")
    void sucesso() {
        String log = rodar("ok", Duration.ofSeconds(30));

        assertThat(dir.resolve("saida.bin")).exists();
        assertThat(log).contains("motor falso: ok");
    }

    @Test
    @DisplayName("código de saída diferente de zero vira erro com o log do motor")
    void codigoDeErro() {
        assertThatThrownBy(() -> rodar("fail", Duration.ofSeconds(30)))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("código 3")
                .satisfies(ex -> assertThat(((EngineException) ex).log()).contains("modelo nao encontrado"));
    }

    @Test
    @DisplayName("motor que termina bem sem gerar o arquivo também é erro")
    void semSaida() {
        assertThatThrownBy(() -> rodar("empty", Duration.ofSeconds(30)))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("sem gerar o arquivo");
    }

    @Test
    @DisplayName("motor travado é encerrado no tempo limite")
    void tempoLimite() {
        AtomicReference<Process> processo = new AtomicReference<>();

        assertThatThrownBy(() -> runner.run(motor("slow"), dir, dir.resolve("saida.bin"),
                Duration.ofSeconds(2), processo::set, porcento -> { }))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("tempo limite");

        assertThat(processo.get().isAlive()).isFalse();
    }

    @Test
    @DisplayName("executável que não existe vira erro claro, não exceção de sistema")
    void executavelAusente() {
        assertThatThrownBy(() -> runner.run(List.of("motor-que-nao-existe-jhcomp"), dir,
                dir.resolve("saida.bin"), Duration.ofSeconds(5), p -> { }, porcento -> { }))
                .isInstanceOf(EngineException.class)
                .hasMessageContaining("instalado");

        assertThat(runner.responds("motor-que-nao-existe-jhcomp", "-version")).isFalse();
    }

    @Test
    @DisplayName("a porcentagem escrita pelo motor chega a quem acompanha, enquanto ele roda")
    void progressoAoVivo() {
        List<Integer> vistos = new java.util.concurrent.CopyOnWriteArrayList<>();

        runner.run(motor("progress"), dir, dir.resolve("saida.bin"), Duration.ofSeconds(30),
                processo -> { }, vistos::add);

        assertThat(vistos).isNotEmpty().isSubsetOf(40, 80);
    }

    @Test
    @DisplayName("o diretório de trabalho é a pasta do projeto: nomes relativos caem nela")
    void diretorioDeTrabalho() throws Exception {
        rodar("ok", Duration.ofSeconds(30));

        assertThat(Files.readString(dir.resolve("saida.bin"))).isEqualTo("conteudo");
    }
}
