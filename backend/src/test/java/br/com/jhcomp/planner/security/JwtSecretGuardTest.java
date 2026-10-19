package br.com.jhcomp.planner.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O segredo de desenvolvimento nao pode valer fora do perfil dev. */
class JwtSecretGuardTest {

    private static JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return properties;
    }

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    @DisplayName("boot falha com o segredo default fora de dev")
    void failsWithDefaultSecretOutsideDev() {
        JwtSecretGuard guard = new JwtSecretGuard(properties(JwtSecretGuard.DEV_SECRET), profile("prod"));

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("boot falha com segredo vazio fora de dev")
    void failsWithBlankSecretOutsideDev() {
        JwtSecretGuard guard = new JwtSecretGuard(properties("   "), profile("prod"));

        assertThatThrownBy(guard::verify).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("boot falha com segredo curto, mesmo customizado")
    void failsWithShortSecret() {
        JwtSecretGuard guard = new JwtSecretGuard(properties("curto-demais"), profile("prod"));

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("no perfil dev o segredo default continua valendo")
    void allowsDefaultSecretInDev() {
        JwtSecretGuard guard = new JwtSecretGuard(properties(JwtSecretGuard.DEV_SECRET), profile("dev"));

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("segredo forte passa em qualquer perfil")
    void allowsStrongSecretInProd() {
        JwtSecretGuard guard = new JwtSecretGuard(
                properties("um-segredo-de-producao-bem-longo-e-aleatorio"), profile("prod"));

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }
}
