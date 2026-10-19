package br.com.jhcomp.planner.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String EMAIL = "admin@jhcomp.com.br";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(propertiesWith(30, 14));
    }

    private JwtProperties propertiesWith(long accessMinutes, long refreshDays) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("jhcomp-engineering-system-test-secret-key-with-enough-length");
        properties.setAccessTokenMinutes(accessMinutes);
        properties.setRefreshTokenDays(refreshDays);
        return properties;
    }

    @Test
    @DisplayName("access token carrega subject, role e tipo 'access'")
    void accessTokenCarriesSubjectAndRole() {
        String token = jwtService.generateAccessToken(EMAIL, "ROLE_ADMIN");

        Claims claims = jwtService.parse(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_ADMIN");
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    @Test
    @DisplayName("refresh token é distinguível do access token")
    void refreshTokenIsDistinguishable() {
        Claims claims = jwtService.parse(jwtService.generateRefreshToken(EMAIL, "ROLE_ADMIN")).orElseThrow();

        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    @DisplayName("token assinado com outro segredo é rejeitado")
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(propertiesWith(30, 14));
        JwtProperties foreign = propertiesWith(30, 14);
        foreign.setSecret("um-segredo-completamente-diferente-mas-igualmente-longo");
        String foreignToken = new JwtService(foreign).generateAccessToken(EMAIL, "ROLE_ADMIN");

        assertThat(other.parse(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("token expirado e lixo textual retornam Optional vazio")
    void expiredAndGarbageTokensAreRejected() {
        JwtService expiring = new JwtService(propertiesWith(0, 0));

        assertThat(expiring.parse(expiring.generateAccessToken(EMAIL, "ROLE_ADMIN"))).isEmpty();
        assertThat(jwtService.parse("nao-e-um-jwt")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    @Test
    @DisplayName("accessTokenSeconds reflete a configuração em minutos")
    void accessTokenSecondsReflectsConfiguration() {
        assertThat(jwtService.accessTokenSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("tokens de usuários distintos não se confundem")
    void tokensAreBoundToTheirSubject() {
        Optional<Claims> claims = jwtService.parse(jwtService.generateAccessToken("outro@jhcomp.com.br", "ROLE_USER"));

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("outro@jhcomp.com.br");
        assertThat(claims.get().get("role", String.class)).isEqualTo("ROLE_USER");
    }
}
