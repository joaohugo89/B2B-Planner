package br.com.jhcomp.planner.post.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
public class Post extends BaseEntity {

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "brand_pillar", length = 120)
    private String brandPillar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PostFormat format = PostFormat.ESTATICO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PostStatus status = PostStatus.RASCUNHO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Audience audience = Audience.B2B;

    @Column(name = "slide_count", nullable = false)
    private int slideCount = 1;

    @Column(length = 400)
    private String summary;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(columnDefinition = "text")
    private String hashtags;

    @Column(name = "cover_image_url", length = 1024)
    private String coverImageUrl;

    @Column(name = "cta_label", length = 160)
    private String ctaLabel;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "estimated_reach")
    private Integer estimatedReach;

    @Column(name = "estimated_saves")
    private Integer estimatedSaves;

    /** Id da mídia no Instagram, quando o post já foi publicado por aqui. */
    @Column(name = "instagram_media_id", length = 40)
    private String instagramMediaId;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Progresso da publicação em segundo plano; nulo quando nunca foi tentada. */
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_state", length = 16)
    private PublishState publishState;

    @Column(name = "publish_error", columnDefinition = "text")
    private String publishError;

    @Column(name = "publish_started_at")
    private Instant publishStartedAt;

    /** Situação de produção de antes da tentativa, para restaurar quando a publicação falha. */
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_previous_status", length = 32)
    private PostStatus publishPreviousStatus;

    /** Artes do carrossel, na ordem de publicação. Um post estático tem um único slide. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_slides", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 1024, nullable = false)
    private List<String> slides = new ArrayList<>();

    protected Post() {
    }

    public Post(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBrandPillar() { return brandPillar; }
    public void setBrandPillar(String brandPillar) { this.brandPillar = brandPillar; }

    public PostFormat getFormat() { return format; }
    public void setFormat(PostFormat format) { this.format = format; }

    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }

    public Audience getAudience() { return audience; }
    public void setAudience(Audience audience) { this.audience = audience; }

    public int getSlideCount() { return slideCount; }
    public void setSlideCount(int slideCount) { this.slideCount = slideCount; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getHashtags() { return hashtags; }
    public void setHashtags(String hashtags) { this.hashtags = hashtags; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getCtaLabel() { return ctaLabel; }
    public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public Integer getEstimatedReach() { return estimatedReach; }
    public void setEstimatedReach(Integer estimatedReach) { this.estimatedReach = estimatedReach; }

    public Integer getEstimatedSaves() { return estimatedSaves; }
    public void setEstimatedSaves(Integer estimatedSaves) { this.estimatedSaves = estimatedSaves; }

    public String getInstagramMediaId() { return instagramMediaId; }
    public void setInstagramMediaId(String instagramMediaId) { this.instagramMediaId = instagramMediaId; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public PublishState getPublishState() { return publishState; }
    public void setPublishState(PublishState publishState) { this.publishState = publishState; }

    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }

    public Instant getPublishStartedAt() { return publishStartedAt; }
    public void setPublishStartedAt(Instant publishStartedAt) { this.publishStartedAt = publishStartedAt; }

    public PostStatus getPublishPreviousStatus() { return publishPreviousStatus; }
    public void setPublishPreviousStatus(PostStatus status) { this.publishPreviousStatus = status; }

    public List<String> getSlides() { return slides; }

    /** Substitui os slides e mantém capa e contagem coerentes com eles. */
    public void replaceSlides(List<String> images) {
        slides.clear();
        if (images != null) {
            slides.addAll(images.stream().filter(url -> url != null && !url.isBlank()).toList());
        }
        if (!slides.isEmpty()) {
            this.coverImageUrl = slides.get(0);
            this.slideCount = slides.size();
        }
    }
}
