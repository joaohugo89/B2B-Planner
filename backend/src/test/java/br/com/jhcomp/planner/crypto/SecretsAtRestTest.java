package br.com.jhcomp.planner.crypto;

import br.com.jhcomp.planner.instagram.InstagramAccount;
import br.com.jhcomp.planner.instagram.InstagramAccountRepository;
import br.com.jhcomp.planner.settings.SettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que token do Instagram e chaves do painel nunca ficam em claro no banco:
 * a coluna crua e lida por SQL nativo, sem passar pelo conversor.
 */
@DataJpaTest
@Import(SettingsService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class SecretsAtRestTest {

    private static final String TOKEN = "IGQVJ-token-de-acesso-do-instagram-em-claro";
    private static final String API_KEY = "sk-ant-chave-secreta-de-teste";

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private EntityManager em;

    @Autowired
    private InstagramAccountRepository accounts;

    @Autowired
    private SettingsService settings;

    private final CryptoService crypto = new CryptoService("chave-de-teste-com-tamanho-suficiente");

    @BeforeEach
    void wireConverter() {
        EncryptedStringConverter.useCrypto(crypto);
    }

    /** O conversor e um bean de teste porque @DataJpaTest nao carrega o CryptoService real. */
    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        CryptoService cryptoService() {
            return new CryptoService("chave-de-teste-com-tamanho-suficiente");
        }
    }

    private String rawColumn(String sql) {
        return (String) em.createNativeQuery(sql).getSingleResult();
    }

    @Test
    @DisplayName("access_token do Instagram fica cifrado na coluna e volta em claro pela entidade")
    void instagramTokenIsEncryptedAtRest() {
        InstagramAccount saved = accounts.saveAndFlush(new InstagramAccount("17841400000000000", TOKEN));
        entities.clear();

        String stored = rawColumn("SELECT access_token FROM instagram_accounts WHERE id = " + saved.getId());

        assertThat(stored).doesNotContain(TOKEN).startsWith(CryptoService.PREFIX);
        assertThat(crypto.decrypt(stored)).isEqualTo(TOKEN);
        assertThat(accounts.findById(saved.getId()))
                .hasValueSatisfying(account -> assertThat(account.getAccessToken()).isEqualTo(TOKEN));
    }

    @Test
    @DisplayName("ai_api_key e instagram_app_secret ficam cifrados em app_settings")
    void sensitiveSettingsAreEncryptedAtRest() {
        settings.save(SettingsService.AI_API_KEY, API_KEY);
        settings.save(SettingsService.INSTAGRAM_APP_SECRET, "segredo-do-app");
        entities.flush();
        entities.clear();

        String storedKey = rawColumn(
                "SELECT setting_value FROM app_settings WHERE setting_key = '" + SettingsService.AI_API_KEY + "'");
        String storedSecret = rawColumn(
                "SELECT setting_value FROM app_settings WHERE setting_key = '"
                        + SettingsService.INSTAGRAM_APP_SECRET + "'");

        assertThat(storedKey).doesNotContain(API_KEY).startsWith(CryptoService.PREFIX);
        assertThat(storedSecret).doesNotContain("segredo-do-app").startsWith(CryptoService.PREFIX);

        // O painel continua lendo o valor original.
        assertThat(settings.find(SettingsService.AI_API_KEY)).contains(API_KEY);
        assertThat(settings.valueOr(SettingsService.INSTAGRAM_APP_SECRET, "")).isEqualTo("segredo-do-app");
    }

    @Test
    @DisplayName("configuracao nao sensivel continua legivel no banco")
    void nonSensitiveSettingStaysPlain() {
        settings.save("global_hashtags", "#JHComp");
        entities.flush();

        assertThat(rawColumn("SELECT setting_value FROM app_settings WHERE setting_key = 'global_hashtags'"))
                .isEqualTo("#JHComp");
    }

    @Test
    @DisplayName("salvar vazio apaga a chave e volta a valer o ambiente")
    void blankClearsOverride() {
        settings.save(SettingsService.AI_API_KEY, API_KEY);
        settings.save(SettingsService.AI_API_KEY, "");
        entities.flush();

        assertThat(settings.find(SettingsService.AI_API_KEY)).isEmpty();
        assertThat(settings.valueOr(SettingsService.AI_API_KEY, "do-ambiente")).isEqualTo("do-ambiente");
    }
}
