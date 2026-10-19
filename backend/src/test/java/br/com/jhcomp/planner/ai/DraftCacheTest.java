package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DraftCacheTest {

    private static PostDraft draft(String title) {
        return new PostDraft(title, "Autoridade", "CARROSSEL", "B2B", 5, "resumo", "legenda", "#jhcomp", "Chame no direct");
    }

    @Test
    @DisplayName("o mesmo briefing, publico e formato caem na mesma chave")
    void sameBriefingSameKey() {
        String first = DraftCache.key(" Backup em PME ", "B2B", "CARROSSEL", "claude-opus-5-5", "medium", "");
        String second = DraftCache.key("backup em pme", "b2b", "carrossel", "claude-opus-5-5", "medium", "");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("mudar esforco ou instrucoes da marca muda a chave")
    void settingsChangeKey() {
        String base = DraftCache.key("backup", "B2B", "CARROSSEL", "claude-opus-5-5", "medium", "");

        assertThat(DraftCache.key("backup", "B2B", "CARROSSEL", "claude-opus-5-5", "high", ""))
                .isNotEqualTo(base);
        assertThat(DraftCache.key("backup", "B2B", "CARROSSEL", "claude-opus-5-5", "medium", "sem emojis"))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("devolve o rascunho guardado dentro da validade")
    void returnsStoredDraft() {
        DraftCache cache = new DraftCache(Duration.ofHours(1), Instant::now);
        cache.put("k", draft("Post 1"));

        assertThat(cache.find("k")).map(PostDraft::title).contains("Post 1");
        assertThat(cache.find("outra")).isEmpty();
    }

    @Test
    @DisplayName("esquece o rascunho depois da expiracao")
    void expires() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
        DraftCache cache = new DraftCache(Duration.ofHours(1), now::get);
        cache.put("k", draft("Post 1"));

        now.set(Instant.parse("2026-01-01T10:59:00Z"));
        assertThat(cache.find("k")).isPresent();

        now.set(Instant.parse("2026-01-01T11:00:01Z"));
        assertThat(cache.find("k")).isEmpty();
    }
}
