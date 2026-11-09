package br.com.jhcomp.planner.video;

import java.util.List;
import java.util.Set;

/**
 * Os cinco arquivos que um projeto pode ter, na ordem em que o pipeline os produz.
 *
 * <p>{@link #depois()} é a regra de "refazer só o necessário": trocar um arquivo invalida
 * o que foi gerado a partir dele, e nada além disso.</p>
 */
public enum VideoFile {
    BASE("base", Set.of("mp4", "mov", "webm")),
    AUDIO("audio", Set.of("wav", "mp3", "m4a")),
    LIPSYNC("lipsync", Set.of("mp4")),
    SUBTITLES("subtitles", Set.of("srt")),
    FINAL("final", Set.of("mp4"));

    private final String prefixo;
    private final Set<String> extensoes;

    VideoFile(String prefixo, Set<String> extensoes) {
        this.prefixo = prefixo;
        this.extensoes = extensoes;
    }

    public String prefixo() {
        return prefixo;
    }

    public Set<String> extensoes() {
        return extensoes;
    }

    /** O que deixa de valer quando este arquivo é trocado. */
    public List<VideoFile> depois() {
        return switch (this) {
            case BASE -> List.of(LIPSYNC, FINAL);
            case AUDIO -> List.of(LIPSYNC, SUBTITLES, FINAL);
            case LIPSYNC, SUBTITLES -> List.of(FINAL);
            case FINAL -> List.of();
        };
    }
}
