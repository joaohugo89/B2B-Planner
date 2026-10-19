package br.com.jhcomp.planner.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Chave usada para cifrar segredos em repouso (tokens e chaves de API). */
@ConfigurationProperties(prefix = "jhcomp.encryption")
public class EncryptionProperties {

    /**
     * Chave AES de 128/192/256 bits em Base64, ou qualquer frase (derivada por SHA-256).
     * Vazia significa "usar a chave de desenvolvimento", que só é aceita no perfil dev.
     */
    private String key = "";

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
}
