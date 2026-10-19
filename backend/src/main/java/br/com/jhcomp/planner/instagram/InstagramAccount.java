package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.common.BaseEntity;
import br.com.jhcomp.planner.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Conta do Instagram conectada ao planner. */
@Entity
@Table(name = "instagram_accounts")
public class InstagramAccount extends BaseEntity {

    @Column(name = "ig_user_id", nullable = false, unique = true, length = 40)
    private String igUserId;

    @Column(length = 120)
    private String username;

    @Column(name = "page_id", length = 40)
    private String pageId;

    @Column(name = "page_name", length = 160)
    private String pageName;

    // Cifrado em repouso (AES-GCM); nunca sai desta entidade em claro para o banco.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", columnDefinition = "text", nullable = false)
    private String accessToken;

    @Column(name = "token_expires")
    private Instant tokenExpires;

    protected InstagramAccount() {
    }

    public InstagramAccount(String igUserId, String accessToken) {
        this.igUserId = igUserId;
        this.accessToken = accessToken;
    }

    public String getIgUserId() { return igUserId; }
    public void setIgUserId(String igUserId) { this.igUserId = igUserId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public Instant getTokenExpires() { return tokenExpires; }
    public void setTokenExpires(Instant tokenExpires) { this.tokenExpires = tokenExpires; }
}
