package br.com.jhcomp.planner.settings;

import br.com.jhcomp.planner.crypto.CryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Configurações que podem ser editadas pelo painel do usuário.
 * O valor gravado no banco tem prioridade sobre a variável de ambiente.
 */
@Service
public class SettingsService {

    public static final String INSTAGRAM_APP_ID = "instagram_app_id";
    public static final String INSTAGRAM_APP_SECRET = "instagram_app_secret";
    public static final String INSTAGRAM_REDIRECT_URI = "instagram_redirect_uri";
    public static final String INSTAGRAM_PUBLIC_BASE_URL = "instagram_public_base_url";
    public static final String AI_API_KEY = "ai_api_key";
    public static final String AI_MODEL = "ai_model";
    /** Instruções extras da marca, somadas ao prompt do sistema. */
    public static final String AI_BRAND_INSTRUCTIONS = "ai_brand_instructions";
    /** Esforço do modelo: low, medium ou high. */
    public static final String AI_EFFORT = "ai_effort";

    /** Chaves cujo valor é cifrado em repouso. */
    private static final List<String> SENSITIVE_KEYS = List.of(INSTAGRAM_APP_SECRET, AI_API_KEY);

    private final AppSettingRepository repository;
    private final CryptoService crypto;

    public SettingsService(AppSettingRepository repository, CryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    public static boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key);
    }

    /** Valor salvo no banco ou, na falta dele, o que veio do ambiente. */
    @Transactional(readOnly = true)
    public String valueOr(String key, String fallback) {
        return read(key).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        return read(key);
    }

    private Optional<String> read(String key) {
        return repository.findById(key)
                .map(AppSetting::getValue)
                .filter(value -> !value.isBlank())
                .map(value -> isSensitive(key) ? crypto.decrypt(value) : value);
    }

    /** Grava o valor; string vazia apaga o override e volta a valer o ambiente. */
    @Transactional
    public void save(String key, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            repository.deleteById(key);
            return;
        }
        String stored = isSensitive(key) ? crypto.encrypt(trimmed) : trimmed;
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, stored));
        setting.setValue(stored);
        repository.save(setting);
    }
}
