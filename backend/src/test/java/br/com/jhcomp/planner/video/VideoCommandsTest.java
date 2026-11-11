package br.com.jhcomp.planner.video;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoCommandsTest {

    @Test
    @DisplayName("render completo: troca o áudio, queima a legenda e corta no mais curto")
    void renderCompleto() {
        List<String> args = VideoCommands.render("ffmpeg", "lipsync-1.mp4", "audio-1.wav", "subtitles-1.srt", "final-1.mp4");

        assertThat(args).containsSubsequence("ffmpeg", "-y", "-i", "lipsync-1.mp4", "-i", "audio-1.wav");
        assertThat(args).containsSubsequence("-vf",
                "subtitles=subtitles-1.srt:force_style='FontSize=9,MarginV=45,Outline=1'");
        assertThat(args).containsSubsequence("-map", "0:v:0", "-map", "1:a:0");
        assertThat(args).contains("-shortest");
        assertThat(args).last().isEqualTo("final-1.mp4");
    }

    @Test
    @DisplayName("sem áudio novo, o render mantém o som do vídeo — e não falha se ele for mudo")
    void renderSemAudio() {
        List<String> args = VideoCommands.render("ffmpeg", "base-1.mp4", null, null, "final-1.mp4");

        assertThat(args).containsSubsequence("-map", "0:v:0", "-map", "0:a?");
        assertThat(args).doesNotContain("-shortest", "-vf");
        assertThat(args.stream().filter("-i"::equals)).hasSize(1);
    }

    @Test
    @DisplayName("o modelo de comando vira argumentos separados, com os marcadores trocados")
    void expandeMarcadores() {
        List<String> args = VideoCommands.expand("wsl -d Ubuntu /opt/voz.sh {text} --saida={out}",
                Map.of("text", "roteiro.txt", "out", "audio-1.wav"));

        assertThat(args).containsExactly("wsl", "-d", "Ubuntu", "/opt/voz.sh", "roteiro.txt", "--saida=audio-1.wav");
    }

    @Test
    @DisplayName("valor com espaço continua sendo um argumento só: nada passa por shell")
    void valorNaoEReinterpretado() {
        List<String> args = VideoCommands.expand("motor {out}", Map.of("out", "a b; rm -rf /"));

        assertThat(args).containsExactly("motor", "a b; rm -rf /");
    }

    @Test
    @DisplayName("produzir tudo roda só as etapas com motor, na ordem, e sempre fecha no render")
    void sequenciaDeProducao() {
        VideoProperties semMotores = new VideoProperties();
        assertThat(VideoPipeline.sequence(semMotores)).containsExactly(VideoJob.Step.RENDER);

        VideoProperties completo = new VideoProperties();
        completo.setVoiceCommand("voz {text} {out}");
        completo.setLipsyncCommand("boca {video} {audio} {out}");
        completo.setTranscribeCommand("srt {audio} {out}");
        assertThat(VideoPipeline.sequence(completo)).containsExactly(
                VideoJob.Step.VOZ, VideoJob.Step.LIPSYNC, VideoJob.Step.LEGENDAS, VideoJob.Step.RENDER);

        VideoProperties semBoca = new VideoProperties();
        semBoca.setVoiceCommand("voz {text} {out}");
        assertThat(VideoPipeline.sequence(semBoca)).containsExactly(VideoJob.Step.VOZ, VideoJob.Step.RENDER);
    }

    @Test
    @DisplayName("marcador que a etapa não oferece é recusado em vez de ir literal ao motor")
    void marcadorDesconhecido() {
        assertThatThrownBy(() -> VideoCommands.expand("motor {video} {out}", Map.of("out", "x.srt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{video}");
    }
}
