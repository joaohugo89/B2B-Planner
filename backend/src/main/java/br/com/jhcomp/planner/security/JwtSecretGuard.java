package br.com.jhcomp.planner.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Impede que a aplicação suba em produção com o segredo JWT de desenvolvimento.
 * No perfil dev o default continua valendo, apenas com um aviso no log.
 */
@Component
public class JwtSecretGuard {

    /** Valor que vem no application.yml quando JWT_SECRET não é definida. */
    public static final String DEV_SECRET = "jhcomp-engineering-system-dev-secret-key-change-me";

    private static final int MIN_LENGTH = 32;
    private static final Logger log = LoggerFactory.getLogger(JwtSecretGuard.class);

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretGuard(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        String secret = properties.getSecret() == null ? "" : properties.getSecret().trim();
        boolean dev = isDevProfile();

        if (DEV_SECRET.equals(secret) || secret.isEmpty()) {
            if (!dev) {
                throw new IllegalStateException(
                        "JWT_SECRET é obrigatória fora do perfil dev: o segredo padrão de desenvolvimento "
                                + "ainda está em uso (jhcomp.jwt.secret). Defina JWT_SECRET com pelo menos "
                                + MIN_LENGTH + " caracteres aleatórios.");
            }
            log.warn("Usando o segredo JWT de desenvolvimento. Defina JWT_SECRET antes de ir a produção.");
            return;
        }

        if (secret.length() < MIN_LENGTH) {
            throw new IllegalStateException("JWT_SECRET precisa de pelo menos " + MIN_LENGTH
                    + " caracteres para assinar tokens HS256 (recebidos: " + secret.length() + ").");
        }
    }

    private boolean isDevProfile() {
        List<String> active = List.of(environment.getActiveProfiles());
        List<String> profiles = active.isEmpty() ? List.of(environment.getDefaultProfiles()) : active;
        return profiles.contains("dev") || profiles.contains("test");
    }
}
