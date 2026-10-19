package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.domain.PublishState;
import br.com.jhcomp.planner.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Publicacao em segundo plano: o pedido volta na hora, o resultado (ou o erro) aparece pelo
 * status e nenhum post pode ficar preso em PUBLICANDO.
 */
class InstagramPublishServiceTest {

    private static final String SEM_CONTA = "Conecte uma conta do Instagram antes de publicar.";

    private PostRepository posts;
    private InstagramService instagram;
    private PublishStateStore store;
    private InstagramPublishService service;
    private Post post;

    /** Executor sincrono: a tarefa roda na propria thread do teste, sem espera. */
    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        instagram = mock(InstagramService.class);
        store = new PublishStateStore(posts);
        service = new InstagramPublishService(instagram, store, Runnable::run);

        post = post(10L, "07", PostStatus.PRONTO_PARA_POSTAR);
        when(posts.findById(10L)).thenAnswer(invocation -> Optional.of(post));
        when(posts.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Post post(Long id, String code, PostStatus status) {
        Post post = new Post(code, "Post " + code);
        post.setStatus(status);
        try {
            Field idField = Post.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(post, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return post;
    }

    @Test
    @DisplayName("A falha da integracao chega pelo status, e o post volta ao estado anterior")
    void failureSurfacesThroughStatus() {
        when(instagram.publish(10L)).thenThrow(new InstagramException(SEM_CONTA));

        service.requestPublish(10L);

        InstagramPublishService.PublishStatus status = service.status(10L);
        assertThat(status.state()).isEqualTo("ERRO");
        assertThat(status.publishing()).isFalse();
        assertThat(status.published()).isFalse();
        assertThat(status.error()).isEqualTo(SEM_CONTA);
        // Nada de PUBLICANDO preso: a situacao de producao e a de antes da tentativa.
        assertThat(status.postStatus()).isEqualTo(PostStatus.PRONTO_PARA_POSTAR);
        assertThat(post.getPublishPreviousStatus()).isNull();
    }

    @Test
    @DisplayName("Publicacao bem-sucedida grava a midia e fecha o progresso como CONCLUIDO")
    void successMarksDone() {
        when(instagram.publish(10L)).thenAnswer(invocation -> {
            post.setInstagramMediaId("ig-123");
            post.setPublishedAt(Instant.parse("2026-10-19T12:00:00Z"));
            post.setStatus(PostStatus.PUBLICADO);
            return post;
        });

        service.requestPublish(10L);

        InstagramPublishService.PublishStatus status = service.status(10L);
        assertThat(status.state()).isEqualTo("CONCLUIDO");
        assertThat(status.published()).isTrue();
        assertThat(status.instagramMediaId()).isEqualTo("ig-123");
        assertThat(status.error()).isNull();
        assertThat(status.postStatus()).isEqualTo(PostStatus.PUBLICADO);
    }

    @Test
    @DisplayName("Post ja publicado nao entra na fila de novo")
    void alreadyPublishedIsRejected() {
        post.setInstagramMediaId("ig-123");

        assertThatThrownBy(() -> service.requestPublish(10L))
                .isInstanceOf(InstagramException.class)
                .hasMessageContaining("já foi publicado");

        verifyNoInteractions(instagram);
    }

    @Test
    @DisplayName("Um segundo pedido durante a publicacao em andamento e recusado")
    void concurrentRequestIsRejected() {
        post.setPublishState(PublishState.PUBLICANDO);
        post.setPublishStartedAt(Instant.now());

        assertThatThrownBy(() -> service.requestPublish(10L))
                .isInstanceOf(InstagramException.class)
                .hasMessageContaining("já está sendo publicado");

        verifyNoInteractions(instagram);
    }

    @Test
    @DisplayName("PUBLICANDO parado tempo demais vira erro na leitura do status")
    void stalePublishingIsReleasedOnRead() {
        post.setStatus(PostStatus.AGENDADO);
        post.setPublishState(PublishState.PUBLICANDO);
        post.setPublishPreviousStatus(PostStatus.PRONTO_PARA_POSTAR);
        post.setPublishStartedAt(Instant.now().minus(30, ChronoUnit.MINUTES));

        InstagramPublishService.PublishStatus status = service.status(10L);

        assertThat(status.state()).isEqualTo("ERRO");
        assertThat(status.error()).contains("interrompida");
        assertThat(status.postStatus()).isEqualTo(PostStatus.PRONTO_PARA_POSTAR);
    }

    @Test
    @DisplayName("No restart do backend, o que estava em PUBLICANDO volta ao estado anterior com erro")
    void orphanedPublishIsReleasedOnStartup() {
        Post travado = post(11L, "08", PostStatus.PUBLICADO);
        travado.setPublishState(PublishState.PUBLICANDO);
        travado.setPublishPreviousStatus(PostStatus.AGENDADO);
        travado.setPublishStartedAt(Instant.now());
        when(posts.findByPublishState(PublishState.PUBLICANDO)).thenReturn(List.of(travado));

        service.releaseOrphanedOnStartup();

        assertThat(travado.getPublishState()).isEqualTo(PublishState.ERRO);
        assertThat(travado.getPublishError()).contains("interrompida");
        assertThat(travado.getStatus()).isEqualTo(PostStatus.AGENDADO);
    }

    @Test
    @DisplayName("Status de post inexistente e erro de Instagram, nao NullPointer")
    void unknownPostIsReported() {
        when(posts.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status(99L))
                .isInstanceOf(InstagramException.class)
                .hasMessageContaining("Post não encontrado");
    }
}
