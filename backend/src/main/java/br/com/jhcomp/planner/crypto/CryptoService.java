package br.com.jhcomp.planner.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Cifra segredos em repouso com AES-GCM.
 *
 * <p>O texto cifrado é guardado como {@code enc:v1:<base64(iv||ciphertext||tag)>}. Valores sem esse
 * prefixo são devolvidos como estão na leitura, para que registros antigos em claro continuem
 * funcionando até a migração de backfill cifrá-los.</p>
 */
@Service
public class CryptoService {

    public static final String PREFIX = "enc:v1:";

    /** Só vale no perfil dev; fora dele a aplicação não sobe sem JHCOMP_ENCRYPTION_KEY. */
    static final String DEV_KEY = "jhcomp-planner-dev-encryption-key-change-me";

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public CryptoService(EncryptionProperties properties, Environment environment) {
        String configured = properties.getKey() == null ? "" : properties.getKey().trim();
        if (configured.isEmpty()) {
            if (!isDevProfile(environment)) {
                throw new IllegalStateException(
                        "JHCOMP_ENCRYPTION_KEY é obrigatória fora do perfil dev: defina uma chave AES "
                                + "(32 bytes em Base64) para cifrar tokens e chaves de API em repouso.");
            }
            log.warn("JHCOMP_ENCRYPTION_KEY não definida: usando a chave de desenvolvimento. "
                    + "Nunca use este modo em produção.");
            configured = DEV_KEY;
        }
        this.key = deriveKey(configured);
    }

    /** Construtor direto, para testes e para o backfill. */
    public CryptoService(String rawKey) {
        this.key = deriveKey(rawKey);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(sealed, 0, payload, iv.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao cifrar valor sensível.", ex);
        }
    }

    /** Decifra; valor em claro (sem prefixo) volta inalterado. */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] sealed = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao decifrar valor sensível: a chave de criptografia mudou?", ex);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKey deriveKey(String raw) {
        byte[] material;
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            material = (decoded.length == 16 || decoded.length == 24 || decoded.length == 32)
                    ? decoded
                    : sha256(raw);
        } catch (IllegalArgumentException notBase64) {
            material = sha256(raw);
        }
        return new SecretKeySpec(material, "AES");
    }

    private static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", ex);
        }
    }

    private static boolean isDevProfile(Environment environment) {
        List<String> active = List.of(environment.getActiveProfiles());
        List<String> profiles = active.isEmpty() ? List.of(environment.getDefaultProfiles()) : active;
        return profiles.contains("dev") || profiles.contains("test");
    }
}
