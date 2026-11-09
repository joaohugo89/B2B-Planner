package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/** Uma execução de uma etapa do pipeline. A lista de jobs de um projeto é o seu histórico. */
@Entity
@Table(name = "video_jobs")
public class VideoJob extends BaseEntity {

    public enum Step { VOZ, LIPSYNC, LEGENDAS, RENDER }

    public enum State {
        NA_FILA, EXECUTANDO, CONCLUIDO, ERRO, CANCELADO;

        public boolean ativo() {
            return this == NA_FILA || this == EXECUTANDO;
        }
    }

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Step step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private State state = State.NA_FILA;

    /** Fim da saída do motor: é o que explica um erro sem abrir o servidor. */
    @Column(columnDefinition = "text")
    private String log;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected VideoJob() {
    }

    public VideoJob(Long projectId, Step step) {
        this.projectId = projectId;
        this.step = step;
    }

    public Long getProjectId() { return projectId; }
    public Step getStep() { return step; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
