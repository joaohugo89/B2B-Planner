package br.com.jhcomp.planner.video;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tira uma porcentagem do que o motor vai escrevendo, em três tentativas:
 *
 * <ol>
 *   <li>{@code PROGRESSO 42} — o contrato dos scripts em /opt/engines, para motores com
 *       várias fases, em que a barra do próprio programa zera e enche mais de uma vez;</li>
 *   <li>FFmpeg — ele não imprime porcentagem, só {@code time=} e, no começo, a duração
 *       das entradas;</li>
 *   <li>o último {@code NN%} que aparecer, que cobre barras comuns como a do whisper.</li>
 * </ol>
 *
 * <p>Guarda estado entre as leituras porque só o fim do log é relido, e a duração que o
 * FFmpeg informa uma vez, no início, já teria saído da janela.</p>
 */
final class Progress {

    private static final Pattern MARCADOR = Pattern.compile("PROGRESSO (\\d{1,3})");
    private static final Pattern DURACAO = Pattern.compile("Duration: (\\d+):(\\d\\d):(\\d\\d(?:\\.\\d+)?)");
    private static final Pattern TEMPO = Pattern.compile("time=(\\d+):(\\d\\d):(\\d\\d(?:\\.\\d+)?)");
    private static final Pattern PORCENTO = Pattern.compile("(\\d{1,3})%");

    /** A menor duração entre as entradas: o render corta no fluxo mais curto. */
    private double duracao = Double.MAX_VALUE;

    /** @return 0 a 100, ou null enquanto o motor não deu nenhuma pista */
    Integer update(String tail) {
        Integer marcador = ultimo(MARCADOR, tail);
        if (marcador != null) {
            return limitar(marcador);
        }

        Matcher duracoes = DURACAO.matcher(tail);
        while (duracoes.find()) {
            duracao = Math.min(duracao, segundos(duracoes));
        }
        Matcher tempos = TEMPO.matcher(tail);
        Double tempo = null;
        while (tempos.find()) {
            tempo = segundos(tempos);
        }
        if (tempo != null && duracao > 0 && duracao != Double.MAX_VALUE) {
            return limitar((int) (tempo * 100 / duracao));
        }

        Integer porcento = ultimo(PORCENTO, tail);
        return porcento == null ? null : limitar(porcento);
    }

    private static Integer ultimo(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Integer valor = null;
        while (matcher.find()) {
            valor = Integer.parseInt(matcher.group(1));
        }
        return valor;
    }

    private static double segundos(Matcher m) {
        return Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
    }

    private static int limitar(int valor) {
        return Math.max(0, Math.min(100, valor));
    }
}
