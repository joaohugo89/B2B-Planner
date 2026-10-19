package br.com.jhcomp.planner.settings;

import br.com.jhcomp.planner.ai.AiAssistantService;
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
        settings.save(SettingsService.AI_MODEL, request.aiModel());
        settings.save(SettingsService.AI_BRAND_INSTRUCTIONS, request.aiBrandInstructions());
        settings.save(SettingsService.AI_EFFORT, normalizeEffort(request.aiEffort()));
        return current();
    }

    private IntegrationSettings current() {
        return new IntegrationSettings(
                instagram.appId(),
                !instagram.appSecret().isBlank(),
                instagram.redirectUri(),
                instagram.publicBaseUrl(),
                ai.isEnabled(),
                ai.model(),
                ai.brandInstructions(),
                ai.effort(),
                instagram.isConfigured(),
                ai.isEnabled());
    }

    /** Nível desconhecido não é gravado: o assistente continua no esforço atual. */
    private String normalizeEffort(String effort) {
        if (effort == null) {
            return null;
        }
        String normalized = effort.trim().toLowerCase();
        if (normalized.isEmpty() || AiAssistantService.EFFORT_LEVELS.contains(normalized)) {
            return normalized;
        }
        return null;
    }
}
