package br.com.jhcomp.planner.video;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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

    /** De quanto em quanto tempo a saída é relida em busca do progresso. */
    private static final long POLL_MS = 1_500;

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
     * @param onStart    recebe o processo assim que ele sobe, para quem quiser cancelá-lo
     * @param onProgress recebe a porcentagem (0 a 100) sempre que ela muda
     * @return o fim da saída do motor
     */
    public String run(List<String> command, Path workDir, Path expectedOutput, Duration timeout,
                      Consumer<Process> onStart, IntConsumer onProgress) {
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
                long limite = System.nanoTime() + timeout.toNanos();
                Progress progress = new Progress();
                Integer ultimo = null;
                while (!(terminou = process.waitFor(POLL_MS, TimeUnit.MILLISECONDS))
                        && System.nanoTime() < limite) {
                    Integer atual = medir(progress, logFile);
                    if (atual != null && !atual.equals(ultimo)) {
                        ultimo = atual;
                        onProgress.accept(atual);
                    }
                }
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

    /** A barra é enfeite: uma saída estranha do motor nunca pode derrubar a espera por ele. */
    static Integer medir(Progress progress, String saida) {
        try {
            return progress.update(saida);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Integer medir(Progress progress, Path logFile) {
        return medir(progress, tail(logFile));
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

    /** Só o fim do arquivo: uma barra de progresso de minutos escreve megabytes. */
    private static String tail(Path logFile) {
        try (SeekableByteChannel channel = Files.newByteChannel(logFile)) {
            long inicio = Math.max(0, channel.size() - LOG_TAIL);
            ByteBuffer buffer = ByteBuffer.allocate((int) (channel.size() - inicio));
            channel.position(inicio);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // lê até encher
            }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
