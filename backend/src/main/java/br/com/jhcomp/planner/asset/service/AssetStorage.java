package br.com.jhcomp.planner.asset.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Onde os arquivos de template ficam guardados. Existem duas implementações:
 * {@link DiskAssetStorage} (padrão, grava na pasta local) e {@link S3AssetStorage}
 * (S3 ou Cloudflare R2), escolhidas por {@code jhcomp.assets.storage}.
 */
public interface AssetStorage {

    /** Grava o arquivo com um nome único e devolve esse nome. */
    String store(MultipartFile file);

    /** Entrega o conteúdo gravado para download. */
    Resource load(String fileName);

    void delete(String fileName);

    /** URL que a UI usa para exibir e baixar o asset. */
    String publicUrl(String storedName);
}
