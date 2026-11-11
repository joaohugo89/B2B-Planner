package br.com.jhcomp.planner.asset.repository;

import br.com.jhcomp.planner.asset.domain.AssetKind;
import br.com.jhcomp.planner.asset.domain.AssetRatio;
import br.com.jhcomp.planner.asset.domain.TemplateAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateAssetRepository extends JpaRepository<TemplateAsset, Long> {

    boolean existsByCode(String code);

    List<TemplateAsset> findAllByOrderByCodeAsc();

    List<TemplateAsset> findByKindOrderByCodeAsc(AssetKind kind);

    List<TemplateAsset> findByRatioOrderByCodeAsc(AssetRatio ratio);

    List<TemplateAsset> findByKindAndRatioOrderByCodeAsc(AssetKind kind, AssetRatio ratio);
}
