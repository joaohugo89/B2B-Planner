package br.com.jhcomp.planner.settings;

import br.com.jhcomp.planner.ai.AiAssistantService;
import br.com.jhcomp.planner.ai.provider.OpenAiCompatibleProvider;
import br.com.jhcomp.planner.instagram.InstagramService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chaves das integrações editáveis pelo painel do usuário.
 * Segredos nunca voltam em claro: a resposta diz apenas se estão definidos.
 */
@RestController
@RequestMapping("/api/settings/integrations")
@Tag(name = "Integrações", description = "Chaves do Instagram e do assistente de IA")
public class IntegrationSettingsController {

    public record IntegrationSettings(
            String instagramAppId,
            boolean instagramAppSecretDefined,
            String instagramRedirectUri,
            String instagramPublicBaseUrl,
            boolean aiApiKeyDefined,
            String aiProvider,
            String aiBaseUrl,
            String aiModel,
            String aiBrandInstructions,
            String aiEffort,
            boolean instagramReady,
            boolean aiReady) {
    }

    /** Campo ausente mantém o valor atual; string vazia apaga e volta a valer o ambiente. */
    public record UpdateRequest(
            @Size(max = 120) String instagramAppId,
            @Size(max = 200) String instagramAppSecret,
            @Size(max = 300) String instagramRedirectUri,
            @Size(max = 300) String instagramPublicBaseUrl,
            @Size(max = 200) String aiApiKey,
            @Size(max = 20) String aiProvider,
            @Size(max = 300) String aiBaseUrl,
            @Size(max = 80) String aiModel,
            @Size(max = 4000) String aiBrandInstructions,
            @Size(max = 10) String aiEffort) {
    }

    private final SettingsService settings;
    private final InstagramService instagram;
    private final AiAssistantService ai;

    public IntegrationSettingsController(SettingsService settings, InstagramService instagram, AiAssistantService ai) {
        this.settings = settings;
        this.instagram = instagram;
        this.ai = ai;
    }

    @GetMapping
    public IntegrationSettings get() {
        return current();
    }

    @PutMapping
    public IntegrationSettings update(@RequestBody UpdateRequest request) {
        settings.save(SettingsService.INSTAGRAM_APP_ID, request.instagramAppId());
        settings.save(SettingsService.INSTAGRAM_APP_SECRET, request.instagramAppSecret());
        settings.save(SettingsService.INSTAGRAM_REDIRECT_URI, request.instagramRedirectUri());
        settings.save(SettingsService.INSTAGRAM_PUBLIC_BASE_URL, request.instagramPublicBaseUrl());
        settings.save(SettingsService.AI_API_KEY, request.aiApiKey());
        settings.save(SettingsService.AI_PROVIDER, oneOf(request.aiProvider(), AiAssistantService.PROVIDERS));
        settings.save(SettingsService.AI_BASE_URL, checkBaseUrl(request.aiBaseUrl()));
        settings.save(SettingsService.AI_MODEL, request.aiModel());
        settings.save(SettingsService.AI_BRAND_INSTRUCTIONS, request.aiBrandInstructions());
        settings.save(SettingsService.AI_EFFORT, oneOf(request.aiEffort(), AiAssistantService.EFFORT_LEVELS));
        return current();
    }

    private IntegrationSettings current() {
        return new IntegrationSettings(
                instagram.appId(),
                !instagram.appSecret().isBlank(),
                instagram.redirectUri(),
                instagram.publicBaseUrl(),
                !ai.apiKey().isBlank(),
                ai.provider(),
                ai.baseUrl(),
                ai.model(),
                ai.brandInstructions(),
                ai.effort(),
                instagram.isConfigured(),
                ai.isEnabled());
    }

    /** URL torta e recusada na hora de salvar, em vez de falhar só quando o assistente é usado. */
    private String checkBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.trim();
        if (!OpenAiCompatibleProvider.isValidBaseUrl(trimmed)) {
            throw new IllegalArgumentException(
                    "URL base inválida. Use o endereço do serviço, como https://api.groq.com/openai/v1.");
        }
        return trimmed;
    }

    /**
     * Valor fora da lista não é gravado (null = não mexer): o assistente continua no provedor
     * e no esforço atuais em vez de ficar com uma opção que nenhum código entende.
     */
    private static String oneOf(String value, java.util.List<String> allowed) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() || allowed.contains(normalized) ? normalized : null;
    }
}
