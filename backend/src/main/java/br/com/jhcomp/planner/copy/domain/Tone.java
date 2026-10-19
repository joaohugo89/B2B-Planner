package br.com.jhcomp.planner.copy.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Locale;

/** Tom de uma legenda — cadastrado pelo usuário, não mais um enum fixo. */
@Entity
@Table(name = "copy_tones")
public class Tone extends BaseEntity {

    @Column(nullable = false, unique = true, length = 24)
    private String code;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(length = 240)
    private String description;

    protected Tone() {
    }

    public Tone(String code, String label) {
        setCode(code);
        this.label = label;
    }

    public String getCode() { return code; }

    /** Código normalizado: maiúsculas, sem espaços — é o que fica gravado nas legendas. */
    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
