package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.domain.PublishState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Publicação em segundo plano: o endpoint devolve na hora e uma thread do executor conversa com
 * a Meta, que pode levar dezenas de segundos num carrossel. A UI acompanha pelo status do post.
 */
@Service
public class InstagramPublishService {

    private static final Logger log = LoggerFactory.getLogger(InstagramPublishService.class);

    /** O que a UI precisa para mostrar "Publicando…", o resultado ou o erro. */
    public record PublishStatus(
            Long postId,
            String postCode,
            /** PUBLICANDO, CONCLUIDO, ERRO ou NUNCA quando nunca foi tentada. */
            String state,
            boolean publishing,
            boolean published,
            String error,
            String instagramMediaId,
            Instant publishedAt,
            Instant startedAt,
            PostStatus postStatus) {

        public static PublishStatus from(Post post) {
            PublishState state = post.getPublishState();
            return new PublishStatus(
                    post.getId(),
                    post.getCode(),
                    state == null ? "NUNCA" : state.name(),
                    state == PublishState.PUBLICANDO,
                    post.getInstagramMediaId() != null,
                    post.getPublishError(),
                    post.getInstagramMediaId(),
                    post.getPublishedAt(),
                    post.getPublishStartedAt(),
                    post.getStatus());
        }
    }

    private final InstagramService instagram;
    private final PublishStateStore store;
    private final Executor executor;

    public InstagramPublishService(InstagramService instagram, PublishStateStore store,
                                   Executor instagramPublishExecutor) {
        this.instagram = instagram;
        this.store = store;
        this.executor = instagramPublishExecutor;
    }

    /**
     * Agenda a publicação e devolve o status inicial. Erros de integração (conta não conectada,
     * URL pública ausente, recusa da Meta) aparecem depois, pelo endpoint de status.
     */
    public PublishStatus requestPublish(Long postId) {
        PublishStatus inicial = PublishStatus.from(store.markPublishing(postId));
        executor.execute(() -> run(postId));
        return inicial;
    }

    /** Situação atual, já destravando o que ficou tempo demais em PUBLICANDO. */
    public PublishStatus status(Long postId) {
        return PublishStatus.from(store.failIfExpired(postId));
    }

    /** Roda na thread do executor: nenhuma exceção pode escapar sem virar erro no post. */
    private void run(Long postId) {
        try {
            instagram.publish(postId);
            store.markDone(postId);
        } catch (RuntimeException ex) {
            log.warn("Publicação em segundo plano do post {} falhou: {}", postId, ex.getMessage());
            try {
                store.markFailed(postId, ex.getMessage());
            } catch (RuntimeException falhaAoGravar) {
                log.error("Não foi possível registrar a falha de publicação do post {}", postId, falhaAoGravar);
            }
        } catch (Error fatal) {
            store.markFailed(postId, "Erro interno durante a publicação: " + fatal);
            throw fatal;
        }
    }

    /**
     * Uma tarefa de publicação não sobrevive ao restart do backend. Se o processo caiu no meio,
     * o post ficaria preso em PUBLICANDO para sempre — então destravamos tudo na partida.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void releaseOrphanedOnStartup() {
        int destravados = store.failOrphaned();
        if (destravados > 0) {
            log.info("{} publicação(ões) interrompida(s) pelo restart voltaram ao estado anterior.", destravados);
        }
    }
}
