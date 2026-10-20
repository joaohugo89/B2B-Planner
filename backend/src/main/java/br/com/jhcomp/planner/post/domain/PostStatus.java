package br.com.jhcomp.planner.post.domain;

public enum PostStatus {
    RASCUNHO,
    AGENDADO,
    PRONTO_PARA_POSTAR,
    PUBLICADO;

    /**
     * Um post que já saiu da bancada: aprovado, com data marcada ou no ar.
     *
     * <p>É o que alguém sem conta pode ver. O que ainda está em RASCUNHO é trabalho em
     * andamento e só aparece para quem entra no planner.</p>
     */
    public boolean isFinalizado() {
        return this != RASCUNHO;
    }
}
