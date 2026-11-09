package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Um vídeo em produção: roteiro, arquivos de cada etapa e, se houver, o post a que pertence. */
@Entity
@Table(name = "video_projects")
public class VideoProject extends BaseEntity {

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "text")
    private String script;

    /** Só o id: o vídeo não copia nada do post, e apagar o post não apaga o vídeo. */
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "base_video", length = 80)
    private String baseVideo;

    @Column(length = 80)
    private String audio;

    @Column(name = "lipsync_video", length = 80)
    private String lipsyncVideo;

    @Column(length = 80)
    private String subtitles;

    @Column(name = "final_video", length = 80)
    private String finalVideo;

    public String file(VideoFile kind) {
        return switch (kind) {
            case BASE -> baseVideo;
            case AUDIO -> audio;
            case LIPSYNC -> lipsyncVideo;
            case SUBTITLES -> subtitles;
            case FINAL -> finalVideo;
        };
    }

    public boolean has(VideoFile kind) {
        return file(kind) != null;
    }

    /**
     * Aponta o projeto para um arquivo novo e esquece o que dependia do antigo.
     *
     * @return os nomes que ficaram sem dono e devem ser apagados do disco
     */
    public List<String> replace(VideoFile kind, String name) {
        List<String> orfaos = new ArrayList<>();
        String anterior = file(kind);
        if (anterior != null && !anterior.equals(name)) {
            orfaos.add(anterior);
        }
        assign(kind, name);
        for (VideoFile dependente : kind.depois()) {
            if (file(dependente) != null) {
                orfaos.add(file(dependente));
                assign(dependente, null);
            }
        }
        return orfaos;
    }

    private void assign(VideoFile kind, String name) {
        switch (kind) {
            case BASE -> baseVideo = name;
            case AUDIO -> audio = name;
            case LIPSYNC -> lipsyncVideo = name;
            case SUBTITLES -> subtitles = name;
            case FINAL -> finalVideo = name;
        }
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
}
