package br.com.jhcomp.planner.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Uma chamada à API do Claude, para consulta de consumo no painel. */
@Entity
@Table(name = "ai_usage")
public class AiUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** "chat" ou "draft". */
    @Column(name = "operation", length = 20, nullable = false)
    private String operation;

    @Column(name = "model", length = 80, nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "effort", length = 10)
    private String effort;

    protected AiUsageRecord() {
    }

    public AiUsageRecord(String operation, String model, long inputTokens, long outputTokens, String effort) {
        this.operation = operation;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.effort = effort;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getOperation() { return operation; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public String getEffort() { return effort; }
}
