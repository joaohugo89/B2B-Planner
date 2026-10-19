package br.com.jhcomp.planner.copy.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "copy_entries")
public class CopyEntry extends BaseEntity {

    @Column(name = "post_code", length = 16)
    private String postCode;

    @Column(nullable = false, length = 200)
    private String headline;

    /** Código do tom cadastrado em copy_tones. */
    @Column(nullable = false, length = 24)
    private String tone = "AUTORIDADE";

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(columnDefinition = "text")
    private String hashtags;

    @Column(length = 240)
    private String cta;

    @Column(length = 160)
    private String strategy;

    @Column(name = "best_time", length = 60)
    private String bestTime;

    protected CopyEntry() {
    }

    public CopyEntry(String headline, String body) {
        this.headline = headline;
        this.body = body;
    }

    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getHashtags() { return hashtags; }
    public void setHashtags(String hashtags) { this.hashtags = hashtags; }

    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getBestTime() { return bestTime; }
    public void setBestTime(String bestTime) { this.bestTime = bestTime; }

    public int getCharCount() {
        return body == null ? 0 : body.length();
    }
}
