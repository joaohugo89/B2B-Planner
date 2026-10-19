package br.com.jhcomp.planner.copy.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Conjunto nomeado de hashtags — "Manutenção", "Sites", "Local" — injetado na legenda com um clique. */
@Entity
@Table(name = "hashtag_blocks")
public class HashtagBlock extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String hashtags;

    protected HashtagBlock() {
    }

    public HashtagBlock(String name, String hashtags) {
        setName(name);
        setHashtags(hashtags);
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name == null ? null : name.trim(); }

    public String getHashtags() { return hashtags; }

    /** Normaliza o bloco: cada palavra começa com #, sem repetição e separadas por espaço. */
    public void setHashtags(String hashtags) {
        this.hashtags = normalize(hashtags);
    }

    /** Lista as hashtags do bloco, já normalizadas. */
    public List<String> tags() {
        return hashtags == null || hashtags.isBlank() ? List.of() : List.of(hashtags.split(" "));
    }

    /**
     * Aceita o que o usuário digitar — "manutencao, #ssd" — e devolve "#manutencao #ssd".
     * Repetidas (ignorando maiúsculas) entram uma única vez.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        Set<String> vistas = new LinkedHashSet<>();
        List<String> saida = new ArrayList<>();

        for (String parte : raw.split("[\\s,;]+")) {
            String tag = parte.replaceAll("^#+", "");
            if (tag.isBlank()) {
                continue;
            }
            if (vistas.add(("#" + tag).toLowerCase(Locale.ROOT))) {
                saida.add("#" + tag);
            }
        }

        return String.join(" ", saida);
    }
}
