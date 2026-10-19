package br.com.jhcomp.planner.post.domain;

/** Progresso da publicação em segundo plano no Instagram. Nulo = nunca foi tentada. */
public enum PublishState {
    /** A tarefa está na fila ou falando com a Meta neste momento. */
    PUBLICANDO,
    /** A mídia foi criada no Instagram. */
    CONCLUIDO,
    /** A última tentativa falhou; a mensagem fica em publishError. */
    ERRO
}
