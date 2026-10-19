package br.com.jhcomp.planner.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    /** Uma fala da conversa. role = "user" ou "assistant". */
    public record ChatMessage(@NotBlank String role, @NotBlank String content) {
    }

    public record ChatRequest(@NotEmpty @Valid List<ChatMessage> messages) {
    }

    public record ChatResponse(String reply) {
    }

    public record DraftRequest(
            @NotBlank @Size(max = 2000) String brief,
            @Size(max = 24) String audience,
            @Size(max = 24) String format) {
    }

    /** Rascunho entregue ao frontend, com a marca de quando veio do cache em memória. */
    public record DraftResponse(PostDraft draft, boolean cached) {
    }

    /** Uma chamada registrada no log de consumo. */
    public record UsageEntry(
            java.time.Instant at,
            String operation,
            String model,
            long inputTokens,
            long outputTokens,
            String effort) {
    }

    /** Consumo do mês corrente e as últimas chamadas. */
    public record UsageReport(
            java.time.Instant monthStart,
            long calls,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            List<UsageEntry> recent) {
    }

    /** Estrutura que o Claude devolve via structured outputs. */
    public record PostDraft(
            @JsonPropertyDescription("Título curto e direto do post, no máximo 90 caracteres")
            String title,

            @JsonPropertyDescription("Pilar de conteúdo, ex.: Autoridade Técnica, Web Solutions B2B")
            String brandPillar,

            @JsonPropertyDescription("ESTATICO, CARROSSEL ou REELS")
            String format,

            @JsonPropertyDescription("B2B, B2C ou HIBRIDO")
            String audience,

            @JsonPropertyDescription("Número de slides; 1 para post estático")
            int slideCount,

            @JsonPropertyDescription("Resumo tático do post em até 2 frases")
            String summary,

            @JsonPropertyDescription("Legenda completa pronta para publicar, em português do Brasil")
            String caption,

            @JsonPropertyDescription("Linha única com 6 a 10 hashtags separadas por espaço")
            String hashtags,

            @JsonPropertyDescription("Chamada para ação principal do post")
            String ctaLabel) {
    }
}
