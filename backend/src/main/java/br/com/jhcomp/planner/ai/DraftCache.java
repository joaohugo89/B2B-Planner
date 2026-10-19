package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Guarda rascunhos em memória para que o mesmo briefing não pague uma segunda chamada à API.
 * A chave inclui tudo que muda o resultado: briefing, público, formato, modelo, esforço e as
 * instruções extras da marca.
 */
@Component
public class DraftCache {

    private record Entry(PostDraft draft, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Supplier<Instant> clock;

    @Autowired
    public DraftCache(AiProperties properties) {
        this(Duration.ofMinutes(properties.getDraftCacheMinutes()), Instant::now);
    }

    /** Usado nos testes para controlar o relógio e o tempo de vida. */
    DraftCache(Duration ttl, Supplier<Instant> clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public static String key(String brief, String audience, String format,
                             String model, String effort, String brandInstructions) {
        return String.join("\u0000",
                normalize(brief), normalize(audience), normalize(format),
                normalize(model), normalize(effort), normalize(brandInstructions));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public Optional<PostDraft> find(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.get().isAfter(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.draft());
    }

    public void put(String key, PostDraft draft) {
        entries.put(key, new Entry(draft, clock.get().plus(ttl)));
    }

    public void clear() {
        entries.clear();
    }
}
