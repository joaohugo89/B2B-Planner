package br.com.jhcomp.planner.user.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String role = "ROLE_ADMIN";

    /**
     * Incrementado ao sair de todas as sessões: tokens emitidos com uma versão anterior
     * deixam de ser aceitos no refresh.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getTokenVersion() { return tokenVersion; }

    /** Revoga todos os refresh tokens já emitidos para este usuário. */
    public int revokeTokens() {
        return ++this.tokenVersion;
    }
}
