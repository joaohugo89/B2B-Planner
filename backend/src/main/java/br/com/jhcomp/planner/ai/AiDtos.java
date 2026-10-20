package br.com.jhcomp.planner.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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
            @NotBlank(message = "Escreva um briefing antes de gerar o rascunho.")
            @Size(max = 2000, message = "O briefing deve ter no máximo 2000 caracteres: "
                    + "descreva o tema em poucas linhas, não cole o post pronto.")
            String brief,
            @Size(max = 24) String audience,
            @Size(max = 24) String format) {
    }

    /**
     * Pedido para transformar uma conversa em vários rascunhos de uma vez.
     *
     * <p>O chat e o gerador de posts são caminhos separados — o modelo não guarda memória de
     * um para o outro. Aqui a conversa viaja de propósito, como matéria-prima.</p>
     */
    public record DraftsFromChatRequest(
            @NotEmpty(message = "Converse com o assistente antes de transformar em posts.")
            @Size(max = 40, message = "Conversa longa demais: comece uma nova para gerar os posts.")
            List<@Valid ChatMessage> messages,

            @Min(value = 1, message = "Peça ao menos um post.")
            @Max(value = 5, message = "No máximo 5 posts por vez, para o custo não disparar.")
            int count) {
    }

    /** Vários rascunhos numa resposta só. */
    public record PostDraftBatch(
            @JsonPropertyDescription("Lista de posts completos, um por ideia distinta da conversa")
            List<PostDraft> drafts) {
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
