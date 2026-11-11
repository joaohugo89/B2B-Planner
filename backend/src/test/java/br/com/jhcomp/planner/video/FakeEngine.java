package br.com.jhcomp.planner.video;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Motor de mentira para o EngineRunnerTest: roda com o próprio java da JVM dos testes,
 * então se comporta igual no Windows e no Linux da imagem Docker, sem FFmpeg nem shell.
 *
 * <p>Uso: FakeEngine &lt;ok|fail|empty|slow|progress&gt; &lt;arquivo de saída&gt;</p>
 */
public final class FakeEngine {

    public static void main(String[] args) throws Exception {
        System.out.println("motor falso: " + args[0]);
        switch (args[0]) {
            case "ok" -> Files.writeString(Paths.get(args[1]), "conteudo");
            case "fail" -> {
                System.out.println("modelo nao encontrado");
                System.exit(3);
            }
            case "empty" -> { /* termina bem, sem escrever nada */ }
            case "slow" -> Thread.sleep(60_000);
            case "progress" -> {
                // Devagar o bastante para o runner ler ao menos um dos marcadores.
                System.out.println("PROGRESSO 40");
                Thread.sleep(2_500);
                System.out.println("PROGRESSO 80");
                Thread.sleep(2_500);
                Files.writeString(Paths.get(args[1]), "conteudo");
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
