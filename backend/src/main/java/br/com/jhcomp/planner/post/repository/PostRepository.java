package br.com.jhcomp.planner.post.repository;

import br.com.jhcomp.planner.post.domain.Audience;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.domain.PublishState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByCode(String code);

    boolean existsByCode(String code);

    List<Post> findAllByOrderByCodeAsc();

    List<Post> findByStatusOrderByCodeAsc(PostStatus status);

    List<Post> findByAudienceOrderByCodeAsc(Audience audience);

    /** Usado na partida para destravar publicações que o restart deixou pela metade. */
    List<Post> findByPublishState(PublishState state);

    long countByStatus(PostStatus status);

    long countByAudience(Audience audience);
}
