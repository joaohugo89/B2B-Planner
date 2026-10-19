package br.com.jhcomp.planner.post.service;

import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.post.domain.Audience;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostFormat;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.repository.PostRepository;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PlannerMetrics;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PostRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Post> list(PostStatus status, Audience audience) {
        if (status != null) {
            return repository.findByStatusOrderByCodeAsc(status);
        }
        if (audience != null) {
            return repository.findByAudienceOrderByCodeAsc(audience);
        }
        return repository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Post getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Post", id));
    }

    @Transactional(readOnly = true)
    public Post getByCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NotFoundException("Post", code));
    }

    public Post create(PostRequest request) {
        Post post = new Post(request.code(), request.title());
        apply(post, request);
        return repository.save(post);
    }

    public Post update(Long id, PostRequest request) {
        Post post = getById(id);
        post.setCode(request.code());
        post.setTitle(request.title());
        apply(post, request);
        return repository.save(post);
    }

    /** Troca as artes do carrossel; a primeira vira a capa do post. */
    public Post replaceSlides(Long id, List<String> slides) {
        Post post = getById(id);
        post.replaceSlides(slides);
        if (post.getSlides().size() > 1) {
            post.setFormat(PostFormat.CARROSSEL);
        }
        return repository.save(post);
    }

    public Post changeStatus(Long id, PostStatus status) {
        Post post = getById(id);
        post.setStatus(status);
        return repository.save(post);
    }

    public void delete(Long id) {
        repository.delete(getById(id));
    }

    @Transactional(readOnly = true)
    public PlannerMetrics metrics() {
        List<Post> all = repository.findAllByOrderByCodeAsc();
        long reach = all.stream().filter(p -> p.getEstimatedReach() != null).mapToLong(Post::getEstimatedReach).sum();
        long saves = all.stream().filter(p -> p.getEstimatedSaves() != null).mapToLong(Post::getEstimatedSaves).sum();

        return new PlannerMetrics(
                all.size(),
                count(all, PostStatus.PRONTO_PARA_POSTAR),
                count(all, PostStatus.AGENDADO),
                count(all, PostStatus.PUBLICADO),
                all.stream().filter(p -> p.getAudience() == Audience.B2B).count(),
                all.stream().filter(p -> p.getAudience() == Audience.B2C).count(),
                all.stream().filter(p -> p.getFormat() == PostFormat.CARROSSEL).count(),
                all.stream().filter(p -> p.getFormat() == PostFormat.ESTATICO).count(),
                reach,
                saves);
    }

    private long count(List<Post> posts, PostStatus status) {
        return posts.stream().filter(p -> p.getStatus() == status).count();
    }

    private void apply(Post post, PostRequest request) {
        post.setBrandPillar(request.brandPillar());
        post.setFormat(request.format() == null ? PostFormat.ESTATICO : request.format());
        post.setStatus(request.status() == null ? PostStatus.RASCUNHO : request.status());
        post.setAudience(request.audience() == null ? Audience.B2B : request.audience());
        post.setSlideCount(Math.max(1, request.slideCount()));
        post.setSummary(request.summary());
        post.setCaption(request.caption());
        post.setHashtags(request.hashtags());
        post.setCoverImageUrl(request.coverImageUrl());
        post.setCtaLabel(request.ctaLabel());
        post.setScheduledAt(request.scheduledAt());
        post.setEstimatedReach(request.estimatedReach());
        post.setEstimatedSaves(request.estimatedSaves());
    }
}
