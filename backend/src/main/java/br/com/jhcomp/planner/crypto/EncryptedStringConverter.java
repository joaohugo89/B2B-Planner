package br.com.jhcomp.planner.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cifra a coluna em AES-GCM ao gravar e decifra ao ler.
 *
 * <p>O Hibernate pode instanciar conversores fora do contexto Spring, por isso o
 * {@link CryptoService} é guardado num campo estático preenchido na criação do bean.</p>
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile CryptoService crypto;

    public EncryptedStringConverter() {
    }

    @Autowired
    public EncryptedStringConverter(CryptoService cryptoService) {
        crypto = cryptoService;
    }

    /** Usado pelos testes para injetar um serviço sem subir o contexto. */
    public static void useCrypto(CryptoService cryptoService) {
        crypto = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return require().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return require().decrypt(dbData);
    }

    private static CryptoService require() {
        CryptoService current = crypto;
        if (current == null) {
            throw new IllegalStateException("CryptoService ainda não inicializado para o conversor de colunas.");
        }
        return current;
    }
}
