package br.com.jhcomp.planner.instagram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jhcomp.instagram")
public class InstagramProperties {

    /** App ID do app criado no Meta for Developers. */
    private String appId = "";

    /** App Secret do mesmo app. */
    private String appSecret = "";

    /** Deve bater exatamente com a URI cadastrada no app da Meta. */
    private String redirectUri = "http://localhost:5173/instagram/callback";

    /**
     * URL pública do backend. A Meta baixa as artes por aqui, então localhost não serve:
     * use um túnel (ngrok/cloudflared) em desenvolvimento ou o domínio real em produção.
     */
    private String publicBaseUrl = "";

    /** Versão da Graph API usada nas chamadas. */
    private String apiVersion = "v21.0";

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public boolean isConfigured() {
        return !appId.isBlank() && !appSecret.isBlank();
    }

    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank()
                && !publicBaseUrl.contains("localhost") && !publicBaseUrl.contains("127.0.0.1");
    }

    public String graphUrl(String path) {
        return "https://graph.facebook.com/" + apiVersion + path;
    }
}
