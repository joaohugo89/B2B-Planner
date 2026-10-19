package br.com.jhcomp.planner.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jhcomp.jwt")
public class JwtProperties {

    /** Base64 or plain secret with at least 32 characters. */
    private String secret = "jhcomp-engineering-system-dev-secret-key-change-me";

    /** Access token lifetime in minutes. */
    private long accessTokenMinutes = 30;

    /** Refresh token lifetime in days. */
    private long refreshTokenDays = 14;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTokenMinutes() { return accessTokenMinutes; }
    public void setAccessTokenMinutes(long accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }

    public long getRefreshTokenDays() { return refreshTokenDays; }
    public void setRefreshTokenDays(long refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }
}
