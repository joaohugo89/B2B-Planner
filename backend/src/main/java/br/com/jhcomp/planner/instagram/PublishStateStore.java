package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.domain.PublishState;
import br.com.jhcomp.planner.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Guarda o progresso da publicação em segundo plano. Fica separado do
 * {@link InstagramPublishService} para que cada escrita rode na sua própria transação,
 * já que a chamada vem de uma thread do executor, fora de qualquer request.
 */
@Component
public class PublishStateStore {

    private static final Logger log = LoggerFactory.getLogger(PublishStateStore.class);

    /**
     * Acima disso, uma publicação PUBLICANDO é considerada perdida (processo morto, thread
     * travada): o status é marcado como erro na primeira leitura em vez de ficar preso.
     */
    static final Duration MAX_PUBLISH_TIME = Duration.ofMinutes(10);

    static final String INTERRUPTED_MESSAGE =
            "A publicação foi interrompida antes de terminar (o servidor reiniciou ou a tarefa travou). "
                    + "Nada foi enviado ao Instagram — tente publicar de novo.";

    private final PostRepository posts;

    public PublishStateStore(PostRepository posts) {
        this.posts = posts;
    }

    /**
     * Marca o post como PUBLICANDO e guarda a situação anterior. Recusa quando o post já foi
     * publicado ou quando já existe uma tentativa em andamento.
     */
    @Transactional
    public Post markPublishing(Long postId) {
        Post post = require(postId);

        if (post.getInstagramMediaId() != null) {
            throw new InstagramException("Este post já foi publicado no Instagram.");
        }
        if (post.getPublishState() == PublishState.PUBLICANDO && !expired(post)) {
            throw new InstagramException("Este post já está sendo publicado. Acompanhe o andamento aqui mesmo.");
        }

        post.setPublishPreviousStatus(post.getStatus());
        post.setPublishState(PublishState.PUBLICANDO);
        post.setPublishError(null);
        post.setPublishStartedAt(Instant.now());

        return posts.save(post);
    }

    /** A mídia foi criada: o próprio InstagramService já gravou id, data e status PUBLICADO. */
    @Transactional
    public void markDone(Long postId) {
        Post post = require(postId);
        post.setPublishState(PublishState.CONCLUIDO);
        post.setPublishError(null);
        post.setPublishPreviousStatus(null);
        posts.save(post);
    }

    /** Falhou: volta à situação de antes da tentativa e deixa a mensagem visível para a UI. */
    @Transactional
    public void markFailed(Long postId, String message) {
        Post post = require(postId);
        restoreFailed(post, message);
        posts.save(post);
    }

    @Transactional(readOnly = true)
    public Post find(Long postId) {
        return require(postId);
    }

    /**
     * Destrava um post deixado em PUBLICANDO por tempo demais — thread travada, ou um restart
     * entre o início e o fim da tarefa. Devolve o post já atualizado quando houve destrave.
     */
    @Transactional
    public Post failIfExpired(Long postId) {
        Post post = require(postId);
        if (post.getPublishState() == PublishState.PUBLICANDO && expired(post)) {
            log.warn("Publicação do post {} passou de {} em PUBLICANDO; marcando como erro.",
                    post.getCode(), MAX_PUBLISH_TIME);
            restoreFailed(post, INTERRUPTED_MESSAGE);
            return posts.save(post);
        }
        return post;
    }

    /**
     * Na partida do backend, nenhuma tarefa de publicação sobreviveu ao processo anterior:
     * tudo que estiver em PUBLICANDO ficou órfão e volta ao estado anterior com o erro.
     */
    @Transactional
    public int failOrphaned() {
        List<Post> travados = posts.findByPublishState(PublishState.PUBLICANDO);
        for (Post post : travados) {
            log.warn("Post {} estava em PUBLICANDO quando o backend subiu; voltando ao estado anterior.",
                    post.getCode());
            restoreFailed(post, INTERRUPTED_MESSAGE);
            posts.save(post);
        }
        return travados.size();
    }

    private void restoreFailed(Post post, String message) {
        PostStatus anterior = post.getPublishPreviousStatus();
        if (anterior != null) {
            post.setStatus(anterior);
        }
        post.setPublishPreviousStatus(null);
        post.setPublishState(PublishState.ERRO);
        post.setPublishError(message == null || message.isBlank() ? "Falha desconhecida na publicação." : message);
    }

    private boolean expired(Post post) {
        Instant inicio = post.getPublishStartedAt();
        return inicio == null || inicio.isBefore(Instant.now().minus(MAX_PUBLISH_TIME));
    }

    private Post require(Long postId) {
        return posts.findById(postId)
                .orElseThrow(() -> new InstagramException("Post não encontrado: " + postId));
    }
}
