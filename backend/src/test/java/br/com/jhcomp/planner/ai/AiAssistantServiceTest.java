package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiDtos.DraftResponse;
import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import br.com.jhcomp.planner.post.repository.PostRepository;
import br.com.jhcomp.planner.settings.SettingsService;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o cache de rascunhos e o registro de consumo com o cliente da Anthropic mockado:
 * nenhuma chave real é necessária.
 */
class AiAssistantServiceTest {

    private static final String DRAFT_JSON = """
            {"title":"Backup que funciona","brandPillar":"Autoridade Tecnica","format":"CARROSSEL",
             "audience":"B2B","slideCount":5,"summary":"resumo","caption":"legenda",
             "hashtags":"#jhcomp","ctaLabel":"Chame no direct"}
            """;

    private MessageService messages;
    private SettingsService settings;
    private AiUsageService usage;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        messages = mock(MessageService.class);
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(messages);

        settings = mock(SettingsService.class);
        // Sem override no banco, vale o que vem de AiProperties.
        when(settings.valueOr(any(), any())).thenAnswer(call -> call.getArgument(1));
        when(settings.valueOr(eq(SettingsService.AI_API_KEY), any())).thenReturn("sk-test");

        usage = mock(AiUsageService.class);

        AiProperties properties = new AiProperties();
        properties.setApiKey("sk-test");

        service = new AiAssistantService(
                properties,
                mock(PostRepository.class),
                settings,
                apiKey -> client,
                usage,
                new DraftCache(Duration.ofHours(1), Instant::now));
    }

    private static Message message(String text, long inputTokens, long outputTokens) {
        return Message.builder()
                .id("msg_1")
                .model("claude-opus-5-5")
                .content(List.of(ContentBlock.ofText(TextBlock.builder().text(text).citations(List.of()).build())))
                .stopDetails(Optional.empty())
                .stopReason(Optional.empty())
                .stopSequence(Optional.empty())
                .container(Optional.empty())
                .usage(Usage.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .cacheCreation(Optional.empty())
                        .cacheCreationInputTokens(Optional.empty())
                        .cacheReadInputTokens(Optional.empty())
                        .inferenceGeo(Optional.empty())
                        .serverToolUse(Optional.empty())
                        .serviceTier(Optional.empty())
                        .build())
                .build();
    }

    /**
     * {@code any(Class)} devolve o tipo cru e o compilador reclama de conversao insegura em
     * toda chamada; concentrar o cast aqui deixa os testes limpos.
     */
    @SuppressWarnings("unchecked")
    private static StructuredMessageCreateParams<PostDraft> anyDraftParams() {
        return any(StructuredMessageCreateParams.class);
    }

    @Test
    @DisplayName("o mesmo briefing nao chama a API duas vezes")
    void draftUsesCacheOnSecondCall() {
        StructuredMessage<PostDraft> response = new StructuredMessage<>(PostDraft.class, message(DRAFT_JSON, 1200, 340));
        when(messages.create(anyDraftParams())).thenReturn(response);

        DraftResponse first = service.draft("Backup em PME", "B2B", "CARROSSEL");
        DraftResponse second = service.draft(" backup em pme ", "b2b", "carrossel");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.draft().title()).isEqualTo(first.draft().title());
        verify(messages, times(1)).create(anyDraftParams());
    }

    @Test
    @DisplayName("briefing diferente volta a chamar a API")
    void differentBriefingCallsApiAgain() {
        StructuredMessage<PostDraft> response = new StructuredMessage<>(PostDraft.class, message(DRAFT_JSON, 10, 20));
        when(messages.create(anyDraftParams())).thenReturn(response);

        service.draft("Backup em PME", "B2B", "CARROSSEL");
        service.draft("Troca de SSD", "B2C", "REELS");

        verify(messages, times(2)).create(anyDraftParams());
    }

    @Test
    @DisplayName("o rascunho registra tokens de entrada e saida, e o cache nao registra de novo")
    void draftRecordsUsageOnce() {
        StructuredMessage<PostDraft> response = new StructuredMessage<>(PostDraft.class, message(DRAFT_JSON, 1200, 340));
        when(messages.create(anyDraftParams())).thenReturn(response);

        service.draft("Backup em PME", "B2B", "CARROSSEL");
        verify(usage).record("draft", "claude-opus-5-5", 1200L, 340L, "medium");

        service.draft("Backup em PME", "B2B", "CARROSSEL");
        verify(usage, times(1)).record(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("o chat registra o consumo e nunca usa cache")
    void chatRecordsUsage() {
        when(messages.create(any(MessageCreateParams.class))).thenReturn(message("resposta", 80, 15));

        String reply = service.chat(List.of(new ChatMessage("user", "me dá uma ideia de post")));

        assertThat(reply).isEqualTo("resposta");
        verify(usage).record("chat", "claude-opus-5-5", 80L, 15L, "medium");
    }

    @Test
    @DisplayName("sem chave, nem o cache nem a API sao usados")
    void withoutApiKeyFails() {
        when(settings.valueOr(eq(SettingsService.AI_API_KEY), any())).thenReturn("");
        AiProperties empty = new AiProperties();
        AiAssistantService disabled = new AiAssistantService(
                empty, mock(PostRepository.class), settings, apiKey -> mock(AnthropicClient.class),
                usage, new DraftCache(Duration.ofHours(1), Instant::now));

        assertThat(disabled.isEnabled()).isFalse();
        verify(usage, never()).record(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("esforco invalido no painel cai no padrao")
    void invalidEffortFallsBack() {
        when(settings.valueOr(eq(SettingsService.AI_EFFORT), any())).thenReturn("turbo");

        assertThat(service.effort()).isEqualTo("medium");
    }
}
