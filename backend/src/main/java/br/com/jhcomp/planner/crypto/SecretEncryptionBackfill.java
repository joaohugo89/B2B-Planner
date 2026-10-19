package br.com.jhcomp.planner.crypto;

import br.com.jhcomp.planner.settings.SettingsService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cifra, no primeiro boot após a mudança, os segredos que ficaram em claro no banco.
 *
 * <p>Roda em SQL nativo porque os valores antigos não têm o prefixo {@code enc:v1:} e, vistos pelo
 * JPA, já "parecem" decifrados — um save pela entidade não marcaria a coluna como alterada.</p>
 */
@Configuration
public class SecretEncryptionBackfill {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionBackfill.class);

    private static final List<String> SENSITIVE_SETTINGS =
            List.of(SettingsService.INSTAGRAM_APP_SECRET, SettingsService.AI_API_KEY);

    @Bean
    public ApplicationRunner encryptStoredSecrets(Runner runner) {
        return args -> runner.run();
    }

    @org.springframework.stereotype.Component
    public static class Runner {

        private final EntityManager em;
        private final CryptoService crypto;

        public Runner(EntityManager em, CryptoService crypto) {
            this.em = em;
            this.crypto = crypto;
        }

        @Transactional
        public void run() {
            int settings = 0;
            for (String key : SENSITIVE_SETTINGS) {
                settings += encryptSetting(key);
            }
            int tokens = encryptAccessTokens();
            if (settings + tokens > 0) {
                log.info("Segredos cifrados em repouso: {} configuração(ões) e {} token(s) do Instagram.",
                        settings, tokens);
            }
        }

        private int encryptSetting(String key) {
            List<?> rows = em.createNativeQuery(
                            "SELECT setting_value FROM app_settings WHERE setting_key = :k")
                    .setParameter("k", key)
                    .getResultList();
            if (rows.isEmpty()) {
                return 0;
            }
            String value = (String) rows.get(0);
            if (value == null || value.isBlank() || CryptoService.isEncrypted(value)) {
                return 0;
            }
            em.createNativeQuery("UPDATE app_settings SET setting_value = :v WHERE setting_key = :k")
                    .setParameter("v", crypto.encrypt(value))
                    .setParameter("k", key)
                    .executeUpdate();
            return 1;
        }

        @SuppressWarnings("unchecked")
        private int encryptAccessTokens() {
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT id, access_token FROM instagram_accounts").getResultList();
            int changed = 0;
            for (Object[] row : rows) {
                String token = (String) row[1];
                if (token == null || token.isBlank() || CryptoService.isEncrypted(token)) {
                    continue;
                }
                em.createNativeQuery("UPDATE instagram_accounts SET access_token = :v WHERE id = :id")
                        .setParameter("v", crypto.encrypt(token))
                        .setParameter("id", ((Number) row[0]).longValue())
                        .executeUpdate();
                changed++;
            }
            return changed;
        }
    }
}
