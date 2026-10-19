package br.com.jhcomp.planner.asset.web.dto;

import br.com.jhcomp.planner.asset.domain.AssetKind;
import br.com.jhcomp.planner.asset.domain.AssetRatio;
import br.com.jhcomp.planner.asset.domain.AssetTheme;
import br.com.jhcomp.planner.asset.domain.TemplateAsset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetResponse(
            Long id,
            String code,
            String name,
            AssetKind kind,
            AssetRatio ratio,
            AssetTheme theme,
            String composition,
            String previewUrl,
            String downloadUrl,
            String fileName) {

        public static AssetResponse from(TemplateAsset asset) {
            return new AssetResponse(
                    asset.getId(),
                    asset.getCode(),
                    asset.getName(),
                    asset.getKind(),
                    asset.getRatio(),
                    asset.getTheme(),
                    asset.getComposition(),
                    asset.getPreviewUrl(),
                    asset.getDownloadUrl(),
                    asset.getFileName());
        }
    }

    public record AssetRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 160) String name,
            AssetKind kind,
            AssetRatio ratio,
            AssetTheme theme,
            @Size(max = 240) String composition,
            String previewUrl,
            String downloadUrl,
            @Size(max = 160) String fileName) {
    }
}
