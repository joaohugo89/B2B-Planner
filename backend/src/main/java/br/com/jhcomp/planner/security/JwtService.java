package br.com.jhcomp.planner.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_VERSION = "ver";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String subject, String role) {
        return generateAccessToken(subject, role, 0);
    }

    public String generateAccessToken(String subject, String role, int tokenVersion) {
        return build(subject, role, TYPE_ACCESS, tokenVersion,
                Duration.ofMinutes(properties.getAccessTokenMinutes()));
    }

    public String generateRefreshToken(String subject, String role) {
        return generateRefreshToken(subject, role, 0);
    }

    public String generateRefreshToken(String subject, String role, int tokenVersion) {
        return build(subject, role, TYPE_REFRESH, tokenVersion,
                Duration.ofDays(properties.getRefreshTokenDays()));
    }

    /** Versão gravada no token; tokens antigos, sem a claim, contam como 0. */
    public int tokenVersion(Claims claims) {
        Integer version = claims.get(CLAIM_VERSION, Integer.class);
        return version == null ? 0 : version;
    }

    public long accessTokenSeconds() {
        return Duration.ofMinutes(properties.getAccessTokenMinutes()).toSeconds();
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private String build(String subject, String role, String type, int tokenVersion, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
