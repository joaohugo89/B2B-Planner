package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.video.EngineRunner.EngineException;
import br.com.jhcomp.planner.video.VideoJob.State;
import br.com.jhcomp.planner.video.VideoJob.Step;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * A fila de produção: recebe o pedido de uma etapa, confere se dá para rodar e executa o
 * motor em segundo plano.
 *
 * <p>Cada etapa é um job à parte e só lê os arquivos que o projeto já tem. É o que permite
 * corrigir a legenda e renderizar de novo sem gerar voz nem sincronia outra vez.</p>
 */
@Service
public class VideoPipeline {

    private static final Logger log = LoggerFactory.getLogger(VideoPipeline.class);

    private final VideoJobStore store;
    private final VideoStorage storage;
    private final EngineRunner runner;
    private final VideoProperties properties;
    private final Executor executor;

    /** Processos em execução, para o cancelamento alcançá-los. */
    private final Map<Long, Process> running = new ConcurrentHashMap<>();
    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();

    public VideoPipeline(VideoJobStore store, VideoStorage storage, EngineRunner runner,
                         VideoProperties properties, @Qualifier("videoJobExecutor") Executor executor) {
        this.store = store;
        this.storage = storage;
        this.runner = runner;
        this.properties = properties;
        this.executor = executor;
    }

    /** O que uma etapa vai rodar e o arquivo que deve sair dela. */
    private record Plan(List<String> command, VideoFile produces, String output) {
    }

    /** Quais etapas têm motor nesta instalação; a tela esconde o botão das que não têm. */
    public Map<Step, Boolean> engines() {
        Map<Step, Boolean> engines = new EnumMap<>(Step.class);
        engines.put(Step.VOZ, !properties.getVoiceCommand().isBlank());
        engines.put(Step.LIPSYNC, !properties.getLipsyncCommand().isBlank());
        engines.put(Step.LEGENDAS, !properties.getTranscribeCommand().isBlank());
        engines.put(Step.RENDER, runner.responds(properties.getFfmpeg(), "-version"));
        return engines;
    }

    /** Recusa na hora o que não tem como dar certo, em vez de falhar minutos depois na fila. */
    public VideoJob submit(Long projectId, Step step) {
        plan(store.project(projectId), step);
        VideoJob job = store.enqueue(projectId, step);
        executor.execute(() -> run(job.getId()));
        return job;
    }

    public void cancel(Long jobId) {
        if (store.cancelQueued(jobId)) {
            return;
        }
        if (store.job(jobId).getState() == State.EXECUTANDO) {
            cancelled.add(jobId);
            Process process = running.get(jobId);
            if (process != null) {
                EngineRunner.kill(process);
            }
        }
    }

    private void run(Long jobId) {
        if (!store.start(jobId)) {
            return;
        }
        VideoJob job = store.job(jobId);
        Long projectId = job.getProjectId();
        String output = null;
        try {
            // Montado agora, não no pedido: o projeto pode ter mudado enquanto o job esperava.
            Plan plan = plan(store.project(projectId), job.getStep());
            output = plan.output();
            Path workDir = storage.dir(projectId);

            String engineLog = runner.run(plan.command(), workDir, workDir.resolve(output),
                    Duration.ofMinutes(properties.getStepTimeoutMinutes()),
                    process -> running.put(jobId, process));

            store.succeed(jobId, engineLog, plan.produces(), output)
                    .forEach(orfao -> storage.delete(projectId, orfao));
        } catch (EngineException ex) {
            storage.delete(projectId, output);
            boolean cancelado = cancelled.contains(jobId);
            store.fail(jobId, cancelado ? State.CANCELADO : State.ERRO,
                    cancelado ? "Cancelado." : ex.getMessage(), ex.log());
        } catch (RuntimeException ex) {
            log.warn("Job de vídeo {} falhou antes de o motor rodar", jobId, ex);
            storage.delete(projectId, output);
            store.fail(jobId, State.ERRO, ex.getMessage(), null);
        } finally {
            running.remove(jobId);
            cancelled.remove(jobId);
        }
    }

    private Plan plan(VideoProject project, Step step) {
        Long id = project.getId();
        return switch (step) {
            case VOZ -> {
                String template = required(properties.getVoiceCommand(), "voz");
                if (project.getScript() == null || project.getScript().isBlank()) {
                    throw new IllegalArgumentException("Escreva ou gere o roteiro antes de gerar a voz.");
                }
                // Por arquivo, não por argumento: roteiro tem aspas, quebras e tamanho livre.
                String text = storage.writeText(id, "roteiro.txt", project.getScript());
                String out = storage.newName(VideoFile.AUDIO, "wav");
                yield new Plan(VideoCommands.expand(template, Map.of("text", text, "out", out)),
                        VideoFile.AUDIO, out);
            }
            case LIPSYNC -> {
                String template = required(properties.getLipsyncCommand(), "sincronia labial");
                need(project, VideoFile.BASE, "Envie o vídeo-base antes de sincronizar.");
                need(project, VideoFile.AUDIO, "Envie ou gere o áudio antes de sincronizar.");
                String out = storage.newName(VideoFile.LIPSYNC, "mp4");
                yield new Plan(VideoCommands.expand(template, Map.of(
                        "video", project.file(VideoFile.BASE),
                        "audio", project.file(VideoFile.AUDIO),
                        "out", out)), VideoFile.LIPSYNC, out);
            }
            case LEGENDAS -> {
                String template = required(properties.getTranscribeCommand(), "transcrição");
                need(project, VideoFile.AUDIO, "Envie ou gere o áudio antes de transcrever.");
                String out = storage.newName(VideoFile.SUBTITLES, "srt");
                yield new Plan(VideoCommands.expand(template, Map.of(
                        "audio", project.file(VideoFile.AUDIO), "out", out)), VideoFile.SUBTITLES, out);
            }
            case RENDER -> {
                need(project, VideoFile.BASE, "Envie o vídeo-base antes de renderizar.");
                // Com sincronia feita, ela é a imagem; sem, o vídeo-base entra como está.
                String video = project.has(VideoFile.LIPSYNC)
                        ? project.file(VideoFile.LIPSYNC) : project.file(VideoFile.BASE);
                String out = storage.newName(VideoFile.FINAL, "mp4");
                yield new Plan(VideoCommands.render(properties.getFfmpeg(), video,
                        project.file(VideoFile.AUDIO), project.file(VideoFile.SUBTITLES), out),
                        VideoFile.FINAL, out);
            }
        };
    }

    private static String required(String template, String motor) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("O motor de " + motor + " não está configurado nesta instalação.");
        }
        return template;
    }

    private static void need(VideoProject project, VideoFile kind, String message) {
        if (!project.has(kind)) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Um motor não pode sobreviver ao backend segurando a GPU. */
    @PreDestroy
    void stopEngines() {
        running.values().forEach(EngineRunner::kill);
    }
}
