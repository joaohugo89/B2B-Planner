package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiDtos.DraftResponse;
import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import br.com.jhcomp.planner.ai.AiDtos.PostDraftBatch;
import br.com.jhcomp.planner.ai.provider.AiProvider;
import br.com.jhcomp.planner.ai.provider.AiProvider.AiReply;
import br.com.jhcomp.planner.ai.provider.AiProvider.AiRequest;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.repository.PostRepository;
import br.com.jhcomp.planner.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Planeja e redige posts da JH.COMP conversando com o serviço de IA escolhido no painel.
 * Nada é instanciado na subida: sem chave, a aplicação roda normalmente com o assistente desligado.
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    /** Níveis de esforço aceitos pelo painel. */
    public static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high");

    /** Serviços que o painel sabe configurar. */
    public static final List<String> PROVIDERS = List.of("anthropic", "openai");

    private final AiProperties properties;
    private final PostRepository posts;
    private final SettingsService settings;
    private final AiUsageService usage;
    private final DraftCache draftCache;
    private final Map<String, AiProvider> providers;

    public AiAssistantService(AiProperties properties, PostRepository posts, SettingsService settings,
                              AiUsageService usage, DraftCache draftCache, List<AiProvider> providers) {
        this.properties = properties;
        this.posts = posts;
        this.settings = settings;
        this.usage = usage;
        this.draftCache = draftCache;
        this.providers = providers.stream().collect(Collectors.toMap(AiProvider::id, Function.identity()));
    }

    /** Chave do painel do usuário; sem ela, a variável de ambiente. */
    public String apiKey() {
        return settings.valueOr(SettingsService.AI_API_KEY, properties.getApiKey());
    }

    /** Serviço escolhido no painel: "anthropic" ou "openai". */
    public String provider() {
        String value = settings.valueOr(SettingsService.AI_PROVIDER, properties.getProvider());
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return providers.containsKey(normalized) ? normalized : "anthropic";
    }

    /** URL base do provedor; vazia deixa cada um usar o endereço padrão dele. */
    public String baseUrl() {
        return settings.valueOr(SettingsService.AI_BASE_URL, properties.getBaseUrl());
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
        AiRequest request = request(systemPrompt() + """

                Responda em português do Brasil, de forma objetiva e no tom técnico-humano da marca.
                Quando o usuário pedir um post, entregue título, resumo, legenda completa e hashtags.
                """);

        AiReply<String> reply = current().chat(request, history);
        usage.record("chat", model(), reply.inputTokens(), reply.outputTokens(), effort());

        String text = reply.content() == null ? "" : reply.content().trim();
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

        AiReply<PostDraft> reply = current().structured(request(systemPrompt()), instruction, PostDraft.class);
        usage.record("draft", model(), reply.inputTokens(), reply.outputTokens(), effort());

        PostDraft draft = reply.content();
        if (draft == null) {
            throw new AiUnavailableException("O modelo não devolveu um rascunho utilizável.");
        }

        draftCache.put(cacheKey, draft);
        return new DraftResponse(draft, false);
    }

    /**
     * Lê uma conversa já acontecida e devolve vários posts prontos, um por ideia distinta.
     *
     * <p>Aqui não há cache: duas conversas nunca são iguais, e guardar o resultado só ocuparia
     * memória sem economizar chamada.</p>
     */
    public List<PostDraft> draftsFromConversation(List<ChatMessage> conversation, int count) {
        String transcricao = conversation.stream()
                .map(m -> ("assistant".equalsIgnoreCase(m.role()) ? "Assistente: " : "Você: ") + m.content())
                .collect(Collectors.joining("\n\n"));

        String instruction = """
                Abaixo está uma conversa de planejamento de conteúdo da JH.COMP.

                Escolha as %d ideias mais fortes e distintas que apareceram nela e transforme cada
                uma num post completo do Instagram. Ideias parecidas contam como uma só: prefira
                ângulos diferentes a variações do mesmo tema. Ignore trechos que não viram post,
                como cumprimentos e perguntas de esclarecimento.

                Conversa:
                %s
                """.formatted(count, transcricao);

        AiReply<PostDraftBatch> reply =
                current().structured(request(systemPrompt()), instruction, PostDraftBatch.class);
        usage.record("drafts", model(), reply.inputTokens(), reply.outputTokens(), effort());

        PostDraftBatch batch = reply.content();
        if (batch == null || batch.drafts() == null || batch.drafts().isEmpty()) {
            throw new AiUnavailableException(
                    "O modelo não encontrou ideias de post nesta conversa. Converse um pouco mais e tente de novo.");
        }

        // O modelo às vezes devolve a mais; o teto pedido é o que vale.
        return batch.drafts().stream().limit(count).toList();
    }

    private AiRequest request(String system) {
        return new AiRequest(apiKey(), baseUrl(), model(), effort(), properties.getMaxTokens(), system);
    }

    private AiProvider current() {
        if (!isEnabled()) {
            throw new AiUnavailableException(
                    "Assistente desativado: informe a chave de API na aba da sua conta para usá-lo.");
        }
        return providers.get(provider());
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
}
