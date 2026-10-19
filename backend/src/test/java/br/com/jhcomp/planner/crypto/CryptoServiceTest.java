package br.com.jhcomp.planner.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AES-GCM: ida e volta, formato do texto cifrado e exigencia da chave fora de dev. */
class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService("chave-de-teste-com-tamanho-suficiente");

    @Test
    @DisplayName("valor cifrado volta igual ao original")
    void roundTrip() {
        String secret = "IGQVJYteste-token-longo-do-instagram";

        String sealed = crypto.encrypt(secret);

        assertThat(sealed).startsWith(CryptoService.PREFIX).doesNotContain(secret);
        assertThat(crypto.decrypt(sealed)).isEqualTo(secret);
    }

    @Test
    @DisplayName("cifrar duas vezes gera textos diferentes (IV aleatorio) que decifram igual")
    void randomIvPerEncryption() {
        String first = crypto.encrypt("mesma-chave");
        String second = crypto.encrypt("mesma-chave");

        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second)).isEqualTo("mesma-chave");
    }

    @Test
    @DisplayName("valor em claro sem prefixo e devolvido como esta na leitura")
    void legacyPlaintextPassesThrough() {
        assertThat(crypto.decrypt("valor-antigo-em-claro")).isEqualTo("valor-antigo-em-claro");
        assertThat(CryptoService.isEncrypted("valor-antigo-em-claro")).isFalse();
    }

    @Test
    @DisplayName("nulo e vazio passam intactos")
    void nullAndEmpty() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.encrypt("")).isEmpty();
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("ja cifrado nao e cifrado de novo")
    void doesNotDoubleEncrypt() {
        String sealed = crypto.encrypt("segredo");
        assertThat(crypto.encrypt(sealed)).isEqualTo(sealed);
    }

    @Test
    @DisplayName("chave trocada faz a leitura falhar em vez de devolver lixo")
    void wrongKeyFails() {
        String sealed = crypto.encrypt("segredo");

        assertThatThrownBy(() -> new CryptoService("outra-chave-completamente-diferente").decrypt(sealed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decifrar");
    }

    @Test
    @DisplayName("sem JHCOMP_ENCRYPTION_KEY fora de dev o bean nao e criado")
    void keyRequiredOutsideDev() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CryptoService(new EncryptionProperties(), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JHCOMP_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("no perfil dev a chave padrao e aceita com aviso")
    void devFallbackAllowed() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        CryptoService service = new CryptoService(new EncryptionProperties(), dev);

        assertThat(service.decrypt(service.encrypt("ok"))).isEqualTo("ok");
    }
}
