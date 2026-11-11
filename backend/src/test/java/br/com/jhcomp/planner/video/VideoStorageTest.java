package br.com.jhcomp.planner.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fronteira de confiança: o que entra pelo upload e onde vai parar no disco. */
class VideoStorageTest {

    @TempDir
    Path root;

    private VideoStorage storage;

    @BeforeEach
    void setUp() {
        VideoProperties properties = new VideoProperties();
        properties.setDir(root.toString());
        storage = new VideoStorage(properties);
    }

    private static MockMultipartFile arquivo(String nome) {
        return new MockMultipartFile("file", nome, "application/octet-stream", new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("grava na pasta do projeto com nome gerado, aproveitando só a extensão")
    void gravaComNomeGerado() {
        String nome = storage.store(7L, VideoFile.BASE, arquivo("../../Meu Vídeo.MP4"));

        assertThat(nome).matches("base-[0-9a-f-]{36}\\.mp4");
        assertThat(root.resolve("7").resolve(nome)).exists();
    }

    @Test
    @DisplayName("recusa extensão fora da lista do tipo")
    void recusaExtensao() {
        assertThatThrownBy(() -> storage.store(7L, VideoFile.BASE, arquivo("instalador.exe")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(7L, VideoFile.AUDIO, arquivo("video.mp4")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(7L, VideoFile.BASE, arquivo("sem-extensao")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("um nome com .. nunca resolve para fora da pasta do projeto")
    void naoSaiDaPasta() {
        assertThatThrownBy(() -> storage.resolve(7L, "../8/final.mp4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve(7L, "."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("apagar o projeto leva a pasta inteira")
    void apagaProjeto() {
        storage.writeText(7L, "roteiro.txt", "olá");
        assertThat(Files.exists(root.resolve("7"))).isTrue();

        storage.deleteProject(7L);

        assertThat(Files.exists(root.resolve("7"))).isFalse();
    }

    @Test
    @DisplayName("o avatar salvo de um projeto vira o vídeo-base dos próximos")
    void avatarEReaproveitado() {
        assertThat(storage.avatar()).isEmpty();
        assertThat(storage.copyAvatarTo(8L)).isNull();

        String base = storage.store(7L, VideoFile.BASE, arquivo("eu.mov"));
        storage.saveAvatar(7L, base);
        // Apagar o projeto de origem não leva o avatar junto.
        storage.deleteProject(7L);

        String copia = storage.copyAvatarTo(8L);
        assertThat(copia).matches("base-[0-9a-f-]{36}\\.mov");
        assertThat(root.resolve("8").resolve(copia)).hasBinaryContent(new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("salvar outro avatar substitui o anterior: há sempre um só")
    void avatarNovoSubstitui() throws Exception {
        storage.saveAvatar(7L, storage.store(7L, VideoFile.BASE, arquivo("antigo.mov")));
        storage.saveAvatar(7L, storage.store(7L, VideoFile.BASE, arquivo("novo.mp4")));

        try (var arquivos = Files.list(root.resolve("avatar"))) {
            assertThat(arquivos.map(p -> p.getFileName().toString())).containsExactly("base.mp4");
        }
    }

    @Test
    @DisplayName("trocar o áudio invalida sincronia, legenda e final — e deixa o vídeo-base")
    void trocarAudioInvalidaOQueDependeDele() {
        VideoProject project = new VideoProject();
        project.replace(VideoFile.BASE, "base.mp4");
        project.replace(VideoFile.AUDIO, "audio-1.wav");
        project.replace(VideoFile.LIPSYNC, "lipsync.mp4");
        project.replace(VideoFile.SUBTITLES, "legenda.srt");
        project.replace(VideoFile.FINAL, "final.mp4");

        List<String> orfaos = project.replace(VideoFile.AUDIO, "audio-2.wav");

        assertThat(orfaos).containsExactlyInAnyOrder("audio-1.wav", "lipsync.mp4", "legenda.srt", "final.mp4");
        assertThat(project.file(VideoFile.BASE)).isEqualTo("base.mp4");
        assertThat(project.file(VideoFile.AUDIO)).isEqualTo("audio-2.wav");
        assertThat(project.has(VideoFile.FINAL)).isFalse();
    }

    @Test
    @DisplayName("corrigir a legenda derruba só o render: voz e sincronia continuam valendo")
    void trocarLegendaSoInvalidaOFinal() {
        VideoProject project = new VideoProject();
        project.replace(VideoFile.BASE, "base.mp4");
        project.replace(VideoFile.AUDIO, "audio.wav");
        project.replace(VideoFile.LIPSYNC, "lipsync.mp4");
        project.replace(VideoFile.FINAL, "final.mp4");

        List<String> orfaos = project.replace(VideoFile.SUBTITLES, "legenda.srt");

        assertThat(orfaos).containsExactly("final.mp4");
        assertThat(project.has(VideoFile.AUDIO)).isTrue();
        assertThat(project.has(VideoFile.LIPSYNC)).isTrue();
    }
}
