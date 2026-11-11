package br.com.jhcomp.planner.asset;

import br.com.jhcomp.planner.asset.repository.TemplateAssetRepository;
import br.com.jhcomp.planner.asset.service.AssetStorage;
import br.com.jhcomp.planner.asset.service.TemplateAssetService;
import br.com.jhcomp.planner.asset.web.dto.AssetDtos.AssetRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateAssetServiceTest {

    @Test
    @DisplayName("código repetido é recusado antes de o arquivo ir para o disco")
    void codigoRepetidoNaoDeixaArquivoOrfao() {
        TemplateAssetRepository repository = mock(TemplateAssetRepository.class);
        AssetStorage storage = mock(AssetStorage.class);
        when(repository.existsByCode("BG-01")).thenReturn(true);

        TemplateAssetService service = new TemplateAssetService(repository, storage);
        AssetRequest request = new AssetRequest("BG-01", "Fundo", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createFromUpload(request,
                new MockMultipartFile("file", "fundo.png", "image/png", new byte[] {1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BG-01");

        verify(storage, never()).store(any());
        verify(repository, never()).save(any());
    }
}
