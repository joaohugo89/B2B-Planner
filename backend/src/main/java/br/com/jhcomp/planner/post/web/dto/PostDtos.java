package br.com.jhcomp.planner.post.web.dto;

import br.com.jhcomp.planner.post.domain.Audience;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostFormat;
import br.com.jhcomp.planner.post.domain.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PostDtos {

    private PostDtos() {
    }

    public record PostResponse(
            Long id,
            String code,
            String title,
            String brandPillar,
            PostFormat format,
            PostStatus status,
            Audience audience,
            int slideCount,
            String summary,
            String caption,
            String hashtags,
            String coverImageUrl,
            String ctaLabel,
            Instant scheduledAt,
            Integer estimatedReach,
            Integer estimatedSaves,
            java.util.List<String> slides,
            String instagramMediaId,
            Instant publishedAt,
            /** Progresso da publicação em segundo plano: PUBLICANDO, CONCLUIDO, ERRO ou nulo. */
            br.com.jhcomp.planner.post.domain.PublishState publishState,
            String publishError) {

        public static PostResponse from(Post post) {
            return new PostResponse(
                    post.getId(),
                    post.getCode(),
                    post.getTitle(),
                    post.getBrandPillar(),
                    post.getFormat(),
                    post.getStatus(),
                    post.getAudience(),
                    post.getSlideCount(),
                    post.getSummary(),
                    post.getCaption(),
                    post.getHashtags(),
                    post.getCoverImageUrl(),
                    post.getCtaLabel(),
                    post.getScheduledAt(),
                    post.getEstimatedReach(),
                    post.getEstimatedSaves(),
                    java.util.List.copyOf(post.getSlides()),
                    post.getInstagramMediaId(),
                    post.getPublishedAt(),
                    post.getPublishState(),
                    post.getPublishError());
        }
    }

    public record PostRequest(
            @NotBlank @Size(max = 16) String code,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 120) String brandPillar,
            PostFormat format,
            PostStatus status,
            Audience audience,
            @Positive int slideCount,
            @Size(max = 400) String summary,
            String caption,
            String hashtags,
            String coverImageUrl,
            @Size(max = 160) String ctaLabel,
            Instant scheduledAt,
            Integer estimatedReach,
            Integer estimatedSaves) {
    }

    public record StatusRequest(PostStatus status) {
    }

    /** Substitui as artes do carrossel, na ordem enviada. */
    public record SlidesRequest(java.util.List<String> slides) {
    }

    public record PlannerMetrics(
            long total,
            long readyToPost,
            long scheduled,
            long published,
            long b2b,
            long b2c,
            long carousels,
            long statics,
            long estimatedReach,
            long estimatedSaves) {
    }
}
