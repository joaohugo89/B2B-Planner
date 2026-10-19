package br.com.jhcomp.planner.asset.service;

import br.com.jhcomp.planner.asset.domain.AssetKind;
import br.com.jhcomp.planner.asset.domain.AssetRatio;
import br.com.jhcomp.planner.asset.domain.AssetTheme;
import br.com.jhcomp.planner.asset.domain.TemplateAsset;
import br.com.jhcomp.planner.asset.repository.TemplateAssetRepository;
import br.com.jhcomp.planner.asset.web.dto.AssetDtos.AssetRequest;
import br.com.jhcomp.planner.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class TemplateAssetService {

    private final TemplateAssetRepository repository;
    private final AssetStorage storage;

    public TemplateAssetService(TemplateAssetRepository repository, AssetStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /** Grava o arquivo enviado pelo site e registra o template apontando para ele. */
    public TemplateAsset createFromUpload(AssetRequest request, MultipartFile file) {
        String stored = storage.store(file);
        String url = storage.publicUrl(stored);

        TemplateAsset asset = new TemplateAsset(request.code(), request.name());
        apply(asset, request);
        asset.setPreviewUrl(url);
        asset.setDownloadUrl(url);
        asset.setFileName(file.getOriginalFilename());
        return repository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<TemplateAsset> list(AssetKind kind, AssetRatio ratio) {
        if (kind != null && ratio != null) {
            return repository.findByKindAndRatioOrderByCodeAsc(kind, ratio);
        }
        if (kind != null) {
            return repository.findByKindOrderByCodeAsc(kind);
        }
        if (ratio != null) {
            return repository.findByRatioOrderByCodeAsc(ratio);
        }
        return repository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public TemplateAsset getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Template", id));
    }

    public TemplateAsset create(AssetRequest request) {
        TemplateAsset asset = new TemplateAsset(request.code(), request.name());
        apply(asset, request);
        return repository.save(asset);
    }

    public TemplateAsset update(Long id, AssetRequest request) {
        TemplateAsset asset = getById(id);
        asset.setCode(request.code());
        asset.setName(request.name());
        apply(asset, request);
        return repository.save(asset);
    }

    public void delete(Long id) {
        TemplateAsset asset = getById(id);
        storage.delete(storedNameOf(asset.getDownloadUrl()));
        repository.delete(asset);
    }

    /**
     * Nome do arquivo guardado, a partir da URL registrada. Cobre tanto o caminho servido
     * pelo backend quanto a URL direta de um bucket público.
     */
    private String storedNameOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url.split("[?#]", 2)[0];
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private void apply(TemplateAsset asset, AssetRequest request) {
        asset.setKind(request.kind() == null ? AssetKind.BACKGROUND : request.kind());
        asset.setRatio(request.ratio() == null ? AssetRatio.RATIO_4_5 : request.ratio());
        asset.setTheme(request.theme() == null ? AssetTheme.DARK : request.theme());
        asset.setComposition(request.composition());
        asset.setPreviewUrl(request.previewUrl());
        asset.setDownloadUrl(request.downloadUrl());
        asset.setFileName(request.fileName());
    }
}
