package br.com.jhcomp.planner.video;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressTest {

    @Test
    @DisplayName("sem pista nenhuma na saída, não inventa porcentagem")
    void semPista() {
        assertThat(new Progress().update("carregando modelo...\n")).isNull();
    }

    @Test
    @DisplayName("o marcador dos scripts vence a barra do próprio motor, que zera a cada fase")
    void marcadorVence() {
        String saida = "PROGRESSO 35\n 98%|#########8| 122/125 [01:03<00:01]\nPROGRESSO 62\n  4%|4   | 5/125";

        assertThat(new Progress().update(saida)).isEqualTo(62);
    }

    @Test
    @DisplayName("FFmpeg: tempo já renderizado sobre a entrada mais curta")
    void ffmpeg() {
        Progress progress = new Progress();
        String cabecalho = "Input #0, mov\n  Duration: 00:00:42.00, start: 0\nInput #1, wav\n  Duration: 00:00:40.00, bitrate\n";

        assertThat(progress.update(cabecalho)).isNull();
        assertThat(progress.update(cabecalho + "frame= 250 fps= 60 time=00:00:10.00 bitrate=")).isEqualTo(25);
    }

    @Test
    @DisplayName("a duração é lembrada depois que o cabeçalho sai da janela do log")
    void lembraADuracao() {
        Progress progress = new Progress();
        progress.update("  Duration: 00:01:00.00, start: 0\n");

        assertThat(progress.update("frame= 900 time=00:00:30.00 bitrate=\rframe= 1350 time=00:00:45.00 bitrate=")).isEqualTo(75);
    }

    @Test
    @DisplayName("barra comum: vale a última porcentagem impressa, nunca acima de 100")
    void porcentagemGenerica() {
        assertThat(new Progress().update("progress =  10%\nprogress =  45%\n")).isEqualTo(45);
        assertThat(new Progress().update("ganho de 250% no teste\n")).isEqualTo(100);
    }
}
