package br.com.jhcomp.planner.instagram;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Limites da API do Instagram verificados antes de publicar, para a recusa vir daqui
 * com uma mensagem clara em vez de voltar como erro genérico da Meta.
 */
public final class InstagramRules {

    public static final int MAX_CAPTION = 2_200;
    public static final int MAX_HASHTAGS = 30;
    public static final int MAX_MENTIONS = 20;
    public static final int MAX_CAROUSEL = 10;

    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}0-9_]+");
    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9._]+");

    private InstagramRules() {
    }

    public record Check(int captionLength, int hashtags, int mentions, int images, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    public static Check check(String caption, int images) {
        String text = caption == null ? "" : caption;
        int hashtags = count(HASHTAG, text);
        int mentions = count(MENTION, text);
        List<String> problems = new ArrayList<>();

        if (text.length() > MAX_CAPTION) {
            problems.add("A legenda tem %d caracteres; o limite do Instagram é %d."
                    .formatted(text.length(), MAX_CAPTION));
        }
        if (hashtags > MAX_HASHTAGS) {
            problems.add("A legenda tem %d hashtags; o limite é %d.".formatted(hashtags, MAX_HASHTAGS));
        }
        if (mentions > MAX_MENTIONS) {
            problems.add("A legenda tem %d menções (@); o limite é %d.".formatted(mentions, MAX_MENTIONS));
        }
        if (images == 0) {
            problems.add("O post não tem arte. Gere uma no Editor de Posts antes de publicar.");
        }
        if (images > MAX_CAROUSEL) {
            problems.add("São %d imagens; um carrossel aceita no máximo %d.".formatted(images, MAX_CAROUSEL));
        }

        return new Check(text.length(), hashtags, mentions, images, problems);
    }

    private static int count(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }
}
