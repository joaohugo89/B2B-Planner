package br.com.jhcomp.planner.asset;

import br.com.jhcomp.planner.asset.service.AssetStorage;
import br.com.jhcomp.planner.asset.service.AssetStorageProperties;
import br.com.jhcomp.planner.asset.service.S3AssetStorage;
import br.com.jhcomp.planner.config.CorsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que trocar o storage não muda as URLs que a UI já usa. */
class AssetStorageUrlTest {

    @Test
    @DisplayName("sem variáveis, o padrão é disco e as origens do Vite local")
    void defaultsKeepLocalBehaviour() {
        assertThat(new AssetStorageProperties().getStorage())
                .isEqualTo(AssetStorageProperties.Backend.DISK);
        assertThat(new CorsProperties().getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Test
    @DisplayName("no disco, a URL continua saindo do backend")
    void diskServesThroughTheApi(@TempDir Path dir) {
        AssetStorageProperties properties = new AssetStorageProperties();
        properties.setDir(dir.toString());

        AssetStorage storage = new br.com.jhcomp.planner.asset.service.DiskAssetStorage(properties);

        assertThat(storage.publicUrl("abc.png")).isEqualTo("/api/assets/files/abc.png");
    }

    @Test
    @DisplayName("no S3 sem domínio público, o backend segue servindo o arquivo")
    void s3WithoutPublicDomainServesThroughTheApi() {
        AssetStorageProperties.S3 config = new AssetStorageProperties.S3();
        config.setBucket("planner");

        assertThat(new S3AssetStorage(null, config).publicUrl("abc.png"))
                .isEqualTo("/api/assets/files/abc.png");
    }

    @Test
    @DisplayName("no S3 com domínio público, a URL aponta direto para o bucket, com o prefixo")
    void s3WithPublicDomainReturnsDirectUrl() {
        AssetStorageProperties.S3 config = new AssetStorageProperties.S3();
        config.setBucket("planner");
        config.setPrefix("/prod/");
        config.setPublicBaseUrl("https://cdn.exemplo.com/");

        assertThat(new S3AssetStorage(null, config).publicUrl("abc.png"))
                .isEqualTo("https://cdn.exemplo.com/prod/abc.png");
    }
}
