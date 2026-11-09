package br.com.jhcomp.planner.video;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Roda um motor externo (FFmpeg, voz, sincronia, transcrição) e espera o arquivo de saída.
 *
 * <p>É a única porta entre o Java e os motores. Cada execução é um processo que nasce e
 * morre: ao sair, o sistema devolve toda a VRAM, sem modelo residente para descarregar.
 * Trocar processo por chamada HTTP — motores no host, backend em contêiner — é trocar
 * esta classe, e mais nada.</p>
 */
@Component
public class EngineRunner {

    /** O fim da saída é onde o motor diz por que falhou; o começo é barra de progresso. */
    private static final int LOG_TAIL = 8_000;

    /** Falha de um motor, com o que ele escreveu antes de parar. */
    public static class EngineException extends RuntimeException {
        private final String log;

        public EngineException(String message, String log) {
            super(message);
            this.log = log;
        }

        public String log() {
            return log;
        }
    }

    /**
     * @param onStart recebe o processo assim que ele sobe, para quem quiser cancelá-lo
     * @return o fim da saída do motor
     */
    public String run(List<String> command, Path workDir, Path expectedOutput, Duration timeout,
                      Consumer<Process> onStart) {
        Path logFile;
        try {
            logFile = Files.createTempFile("jhcomp-engine-", ".log");
        } catch (IOException ex) {
            throw new EngineException("Não foi possível criar o arquivo de log do motor.", null);
        }

        try {
            Process process;
            try {
                process = new ProcessBuilder(command)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        // Para arquivo, não para um pipe: um pipe cheio travaria o motor.
                        .redirectOutput(logFile.toFile())
                        .start();
            } catch (IOException ex) {
                throw new EngineException("Não foi possível iniciar '" + command.get(0)
                        + "'. Confira se está instalado e no PATH.", ex.getMessage());
            }
            onStart.accept(process);

            boolean terminou;
            try {
                terminou = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                kill(process);
                Thread.currentThread().interrupt();
                throw new EngineException("A etapa foi interrompida.", tail(logFile));
            }
            if (!terminou) {
                kill(process);
                throw new EngineException("A etapa passou do tempo limite de "
                        + timeout.toMinutes() + " min e foi encerrada.", tail(logFile));
            }

            String log = tail(logFile);
            if (process.exitValue() != 0) {
                throw new EngineException("O motor terminou com erro (código " + process.exitValue() + ").", log);
            }
            if (!temConteudo(expectedOutput)) {
                throw new EngineException("O motor terminou sem gerar o arquivo de saída.", log);
            }
            return log;
        } finally {
            try {
                Files.deleteIfExists(logFile);
            } catch (IOException ignored) {
                // Arquivo temporário: o sistema limpa.
            }
        }
    }

    /** Mata o processo e os filhos: "wsl ..." e scripts .bat deixam o motor de verdade num filho. */
    public static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** O executável existe e responde? Usado para dizer à tela se o render está disponível. */
    public boolean responds(String executable, String versionFlag) {
        try {
            Process process = new ProcessBuilder(executable, versionFlag)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                kill(process);
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean temConteudo(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String tail(Path logFile) {
        try {
            String text = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            return text.length() <= LOG_TAIL ? text : text.substring(text.length() - LOG_TAIL);
        } catch (IOException ex) {
            return "";
        }
    }
}
