package br.com.jhcomp.planner.video;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta as linhas de comando dos motores — sempre como lista de argumentos, nunca como
 * texto para um shell: é isso que impede um nome de arquivo ou um roteiro de virar comando.
 */
final class VideoCommands {

    private VideoCommands() {
    }

    /**
     * Render final. Os nomes são relativos à pasta do projeto, que é o diretório de trabalho
     * do processo — e é por isso que o filtro de legendas dispensa o escape de "C:\".
     *
     * @param audio     null mantém o áudio do próprio vídeo
     * @param subtitles null renderiza sem legenda
     */
    static List<String> render(String ffmpeg, String video, String audio, String subtitles, String out) {
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-y", "-i", video));
        if (audio != null) {
            args.addAll(List.of("-i", audio));
        }
        if (subtitles != null) {
            args.addAll(List.of("-vf", "subtitles=" + subtitles));
        }
        // "0:a?" não falha quando o vídeo-base é mudo.
        args.addAll(List.of("-map", "0:v:0", "-map", audio != null ? "1:a:0" : "0:a?"));
        args.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                "-c:a", "aac"));
        if (audio != null) {
            // A voz gerada raramente tem a duração exata do vídeo-base.
            args.add("-shortest");
        }
        args.addAll(List.of("-movflags", "+faststart", out));
        return args;
    }

    /**
     * Troca os marcadores do modelo configurado pelos valores da etapa.
     *
     * <p>shortcut: o modelo é separado por espaços, sem aspas — caminho com espaço precisa
     * de um script intermediário. Trocar por um parser quando isso atrapalhar.</p>
     */
    static List<String> expand(String template, Map<String, String> values) {
        List<String> args = new ArrayList<>();
        for (String token : template.trim().split("\\s+")) {
            String arg = token;
            for (var entry : values.entrySet()) {
                arg = arg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (arg.matches(".*\\{[a-z]+}.*")) {
                throw new IllegalArgumentException(
                        "Marcador desconhecido em '" + token + "'. Aceitos nesta etapa: " + values.keySet());
            }
            args.add(arg);
        }
        return args;
    }
}
