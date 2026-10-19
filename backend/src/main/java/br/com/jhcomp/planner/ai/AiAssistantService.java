package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiDtos.DraftResponse;
import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.repository.PostRepository;
import br.com.jhcomp.planner.settings.SettingsService;
import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Conversa com o Claude para planejar e redigir posts da JH.COMP.
 * O cliente é criado sob demanda para que a aplicação suba sem chave de API.
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    /** Níveis de esforço aceitos pelo painel. */
    public static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high");

    private final AiProperties properties;
    private final PostRepository posts;
    private final SettingsService settings;
    private final AnthropicClients clients;
    private final AiUsageService usage;
    private final DraftCache draftCache;

    public AiAssistantService(AiProperties properties, PostRepository posts, SettingsService settings,
                              AnthropicClients clients, AiUsageService usage, DraftCache draftCache) {
        this.properties = properties;
        this.posts = posts;
        this.settings = settings;
        this.clients = clients;
        this.usage = usage;
        this.draftCache = draftCache;
    }

    /** Chave do painel do usuário; sem ela, a variável de ambiente. */
    public String apiKey() {
        return settings.valueOr(SettingsService.AI_API_KEY, properties.getApiKey());
    }

    public String model() {
        return settings.valueOr(SettingsService.AI_MODEL, properties.getModel());
    }

    /** Esforço do modelo (low/medium/high); valor inválido cai no padrão. */
    public String effort() {
        String value = settings.valueOr(SettingsService.AI_EFFORT, properties.getEffort());
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return EFFORT_LEVELS.contains(normalized) ? normalized : "medium";
    }

    /** Instruções extras da marca, editadas no painel. */
    public String brandInstructions() {
        return settings.valueOr(SettingsService.AI_BRAND_INSTRUCTIONS, "");
    }

    public boolean isEnabled() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    public String chat(List<ChatMessage> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model())
                .maxTokens(properties.getMaxTokens())
                .outputConfig(outputConfig())
                .system(systemPrompt() + """

                        Responda em português do Brasil, de forma objetiva e no tom técnico-humano da marca.
                        Quando o usuário pedir um post, entregue título, resumo, legenda completa e hashtags.
                        """);

        for (ChatMessage message : history) {
            if ("assistant".equalsIgnoreCase(message.role())) {
                builder.addAssistantMessage(message.content());
            } else {
                builder.addUserMessage(message.content());
            }
        }

        Message response = call(() -> client().messages().create(builder.build()));
        usage.record("chat", model(), response.usage().inputTokens(), response.usage().outputTokens(), effort());

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .trim();

        return text.isEmpty() ? "Não consegui gerar uma resposta desta vez. Tente reformular o pedido." : text;
    }

    /** Mesmo briefing, público e formato devolvem o rascunho guardado, sem nova chamada à API. */
    public DraftResponse draft(String brief, String audience, String format) {
        String cacheKey = DraftCache.key(brief, audience, format, model(), effort(), brandInstructions());
        Optional<PostDraft> cached = draftCache.find(cacheKey);
        if (cached.isPresent()) {
            log.debug("Rascunho servido do cache em memória");
            return new DraftResponse(cached.get(), true);
        }

        String instruction = """
                Crie um post completo para o Instagram da JH.COMP a partir do briefing abaixo.

                Briefing: %s
                Público preferencial: %s
                Formato preferencial: %s

                Escreva a legenda pronta para publicar, com quebras de linha e emojis técnicos usados com parcimônia.
                """.formatted(brief,
                audience == null || audience.isBlank() ? "decida pelo briefing" : audience,
                format == null || format.isBlank() ? "decida pelo briefing" : format);

        StructuredMessageCreateParams<PostDraft> params = withEffort(MessageCreateParams.builder()
                .model(model())
                .maxTokens(properties.getMaxTokens())
                .system(systemPrompt())
                .outputConfig(PostDraft.class)
                .addUserMessage(instruction)
                .build());

        StructuredMessage<PostDraft> response = call(() -> client().messages().create(params));
        usage.record("draft", model(), response.usage().inputTokens(), response.usage().outputTokens(), effort());

        PostDraft draft = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .findFirst()
                .orElseThrow(() -> new AiUnavailableException("O modelo não devolveu um rascunho utilizável."));

        draftCache.put(cacheKey, draft);
        return new DraftResponse(draft, false);
    }

    private OutputConfig outputConfig() {
        return OutputConfig.builder().effort(OutputConfig.Effort.of(effort())).build();
    }

    /**
     * outputConfig(PostDraft.class) monta um output_config só com o formato. Aqui o esforço
     * escolhido no painel é somado a esse formato, sem perder o structured output.
     */
    private StructuredMessageCreateParams<PostDraft> withEffort(StructuredMessageCreateParams<PostDraft> params) {
        MessageCreateParams raw = params.rawParams();
        OutputConfig.Builder config = OutputConfig.builder().effort(OutputConfig.Effort.of(effort()));
        raw.outputConfig()
                .flatMap(OutputConfig::format)
                .ifPresent(config::format);

        return new StructuredMessageCreateParams<>(
                params.outputType(),
                raw.toBuilder().outputConfig(config.build()).build());
    }

    /** Contexto de marca + o que já está no planner, para o modelo não repetir temas. */
    private String systemPrompt() {
        String existing = posts.findAllByOrderByCodeAsc().stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));

        String base = """
                Você é o estrategista de conteúdo da JH.COMP, empresa brasileira de tecnologia com duas frentes:
                assistência técnica especializada (computadores, redes, upgrades de hardware) e engenharia de
                software e sites sob medida para empresas (B2B).

                Tom da marca: técnico, direto, humano, sem jargão vazio e sem promessas exageradas. Autoridade
                vem de método e transparência. CTAs levam para WhatsApp, direct ou link da bio.

                Posts que já existem no planner (não repita o mesmo ângulo):
                %s
                """.formatted(existing.isBlank() ? "(nenhum)" : existing);

        String extra = brandInstructions();
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return base + """

                Instruções extras da marca (prevalecem sobre o que está acima):
                %s
                """.formatted(extra.trim());
    }

    private String describe(Post post) {
        return "- %s | %s | %s | %s".formatted(
                post.getCode(), post.getTitle(), post.getAudience(), post.getBrandPillar());
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (AnthropicServiceException ex) {
            log.warn("Falha na chamada à API da Anthropic", ex);
            throw new AiUnavailableException("A API do Claude recusou a requisição: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada ao falar com o Claude", ex);
            throw new AiUnavailableException("Não foi possível falar com o assistente agora.", ex);
        }
    }

    private AnthropicClient client() {
        if (!isEnabled()) {
            throw new AiUnavailableException(
                    "Assistente desativado: defina ANTHROPIC_API_KEY (ou jhcomp.ai.api-key) para usá-lo.");
        }
        return clients.forKey(apiKey());
    }
}
