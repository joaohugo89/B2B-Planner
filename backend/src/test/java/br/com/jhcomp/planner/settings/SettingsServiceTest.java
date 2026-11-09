package br.com.jhcomp.planner.settings;

import br.com.jhcomp.planner.crypto.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que importa aqui é o cache: ele existe porque uma única chamada ao assistente lia seis
 * configurações do banco. Se ele guardar valor velho depois de alguém salvar o painel, a
 * aplicação passa a usar a chave antiga sem avisar ninguém — por isso o teste.
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private AppSettingRepository repository;

    @Mock
    private CryptoService crypto;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(repository, crypto);
    }

    @Test
    @DisplayName("lê o banco uma vez só, mesmo repetindo a consulta")
    void segundaLeituraVemDoCache() {
        when(repository.findById(SettingsService.AI_MODEL))
                .thenReturn(Optional.of(new AppSetting(SettingsService.AI_MODEL, "gpt-oss-120b")));

        assertThat(service.valueOr(SettingsService.AI_MODEL, "padrao")).isEqualTo("gpt-oss-120b");
        assertThat(service.valueOr(SettingsService.AI_MODEL, "padrao")).isEqualTo("gpt-oss-120b");
        assertThat(service.find(SettingsService.AI_MODEL)).contains("gpt-oss-120b");

        verify(repository, times(1)).findById(SettingsService.AI_MODEL);
    }

    @Test
    @DisplayName("chave ausente também é lembrada, sem repetir o SELECT")
    void ausenciaTambemFicaNoCache() {
        when(repository.findById(SettingsService.AI_BASE_URL)).thenReturn(Optional.empty());

        assertThat(service.valueOr(SettingsService.AI_BASE_URL, "do-ambiente")).isEqualTo("do-ambiente");
        assertThat(service.valueOr(SettingsService.AI_BASE_URL, "do-ambiente")).isEqualTo("do-ambiente");

        verify(repository, times(1)).findById(SettingsService.AI_BASE_URL);
    }

    @Test
    @DisplayName("salvar derruba o cache e o novo valor passa a valer")
    void salvarInvalidaOCache() {
        when(repository.findById(SettingsService.AI_MODEL))
                .thenReturn(Optional.of(new AppSetting(SettingsService.AI_MODEL, "antigo")));

        assertThat(service.valueOr(SettingsService.AI_MODEL, "padrao")).isEqualTo("antigo");

        service.save(SettingsService.AI_MODEL, "novo");

        when(repository.findById(SettingsService.AI_MODEL))
                .thenReturn(Optional.of(new AppSetting(SettingsService.AI_MODEL, "novo")));
        assertThat(service.valueOr(SettingsService.AI_MODEL, "padrao")).isEqualTo("novo");
    }

    @Test
    @DisplayName("apagar o valor derruba o cache e volta a valer o ambiente")
    void apagarInvalidaOCache() {
        when(repository.findById(SettingsService.AI_MODEL))
                .thenReturn(Optional.of(new AppSetting(SettingsService.AI_MODEL, "antigo")));

        assertThat(service.valueOr(SettingsService.AI_MODEL, "do-ambiente")).isEqualTo("antigo");

        service.save(SettingsService.AI_MODEL, "   ");
        verify(repository).deleteById(SettingsService.AI_MODEL);

        when(repository.findById(SettingsService.AI_MODEL)).thenReturn(Optional.empty());
        assertThat(service.valueOr(SettingsService.AI_MODEL, "do-ambiente")).isEqualTo("do-ambiente");
    }

    @Test
    @DisplayName("segredo que não abre com a chave atual vale como não configurado")
    void segredoIlegivelNaoDerrubaAAplicacao() {
        when(repository.findById(SettingsService.AI_API_KEY))
                .thenReturn(Optional.of(new AppSetting(SettingsService.AI_API_KEY, "enc:v1:lixo")));
        when(crypto.decrypt(anyString())).thenThrow(new IllegalStateException("chave trocada"));

        assertThat(service.find(SettingsService.AI_API_KEY)).isEmpty();
        assertThat(service.valueOr(SettingsService.AI_API_KEY, null)).isNull();

        verify(repository, times(1)).findById(SettingsService.AI_API_KEY);
        verify(repository, never()).save(any());
    }
}
