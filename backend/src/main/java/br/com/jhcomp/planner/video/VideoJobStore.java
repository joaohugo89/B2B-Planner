package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.video.VideoJob.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * O estado dos jobs no banco. Fica separado do {@link VideoPipeline} pelo mesmo motivo do
 * PublishStateStore do Instagram: as escritas vêm da thread da fila, fora de qualquer
 * request, e cada uma precisa da sua própria transação.
 */
@Component
public class VideoJobStore {

    private static final Logger log = LoggerFactory.getLogger(VideoJobStore.class);

    static final List<State> ATIVOS = List.of(State.NA_FILA, State.EXECUTANDO);

    static final String INTERROMPIDO =
            "O servidor reiniciou antes de esta etapa terminar. Nada foi perdido — rode a etapa de novo.";

    private final VideoJobRepository jobs;
    private final VideoProjectRepository projects;

    public VideoJobStore(VideoJobRepository jobs, VideoProjectRepository projects) {
        this.jobs = jobs;
        this.projects = projects;
    }

    /** Um projeto roda uma etapa por vez: duas mexeriam nos mesmos arquivos. */
    @Transactional
    public VideoJob enqueue(Long projectId, VideoJob.Step step) {
        if (hasActive(projectId)) {
            throw new IllegalArgumentException("Este projeto já tem uma etapa em andamento. Espere ou cancele.");
        }
        return jobs.save(new VideoJob(projectId, step));
    }

    @Transactional(readOnly = true)
    public boolean hasActive(Long projectId) {
        return jobs.existsByProjectIdAndStateIn(projectId, ATIVOS);
    }

    /** @return false se o job foi cancelado enquanto esperava na fila */
    @Transactional
    public boolean start(Long jobId) {
        VideoJob job = job(jobId);
        if (job.getState() != State.NA_FILA) {
            return false;
        }
        job.setState(State.EXECUTANDO);
        job.setStartedAt(Instant.now());
        return true;
    }

    /**
     * Fecha o job e aponta o projeto para o arquivo gerado.
     *
     * @return os arquivos que deixaram de valer e podem sair do disco
     */
    @Transactional
    public List<String> succeed(Long jobId, String engineLog, VideoFile kind, String outputName) {
        VideoJob job = job(jobId);
        close(job, State.CONCLUIDO, null, engineLog);
        return project(job.getProjectId()).replace(kind, outputName);
    }

    @Transactional
    public void fail(Long jobId, State state, String error, String engineLog) {
        close(job(jobId), state, error, engineLog);
    }

    /** @return true se o job ainda estava na fila e foi cancelado aqui mesmo */
    @Transactional
    public boolean cancelQueued(Long jobId) {
        VideoJob job = job(jobId);
        if (job.getState() != State.NA_FILA) {
            return false;
        }
        close(job, State.CANCELADO, "Cancelado antes de começar.", null);
        return true;
    }

    /**
     * No boot, nenhum processo de motor sobreviveu ao backend anterior, e a fila em memória
     * foi embora com ele: o que ficou ativo no banco é órfão.
     */
    @Transactional
    public int failOrphaned() {
        List<VideoJob> orfaos = jobs.findByStateIn(ATIVOS);
        for (VideoJob job : orfaos) {
            log.warn("Job de vídeo {} ({}) estava {} quando o backend subiu; marcando como erro.",
                    job.getId(), job.getStep(), job.getState());
            close(job, State.ERRO, INTERROMPIDO, job.getLog());
        }
        return orfaos.size();
    }

    @Transactional(readOnly = true)
    public VideoJob job(Long jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job de vídeo", jobId));
    }

    @Transactional(readOnly = true)
    public VideoProject project(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new NotFoundException("Projeto de vídeo", projectId));
    }

    private void close(VideoJob job, State state, String error, String engineLog) {
        job.setState(state);
        job.setError(error);
        job.setLog(engineLog);
        job.setFinishedAt(Instant.now());
        jobs.save(job);
    }
}
