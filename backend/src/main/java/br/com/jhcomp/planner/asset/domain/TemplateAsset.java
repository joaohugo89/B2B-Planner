package br.com.jhcomp.planner.asset.domain;

import br.com.jhcomp.planner.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "template_assets")
public class TemplateAsset extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssetKind kind = AssetKind.BACKGROUND;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssetRatio ratio = AssetRatio.RATIO_4_5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetTheme theme = AssetTheme.DARK;

    @Column(length = 240)
    private String composition;

    @Column(name = "preview_url", length = 1024)
    private String previewUrl;

    @Column(name = "download_url", length = 1024)
    private String downloadUrl;

    @Column(name = "file_name", length = 160)
    private String fileName;

    protected TemplateAsset() {
    }

    public TemplateAsset(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AssetKind getKind() { return kind; }
    public void setKind(AssetKind kind) { this.kind = kind; }

    public AssetRatio getRatio() { return ratio; }
    public void setRatio(AssetRatio ratio) { this.ratio = ratio; }

    public AssetTheme getTheme() { return theme; }
    public void setTheme(AssetTheme theme) { this.theme = theme; }

    public String getComposition() { return composition; }
    public void setComposition(String composition) { this.composition = composition; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
