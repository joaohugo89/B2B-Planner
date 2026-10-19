package br.com.jhcomp.planner.asset.service;

import br.com.jhcomp.planner.common.NotFoundException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Guarda os arquivos num bucket compatível com S3 (AWS S3 ou Cloudflare R2), para que os
 * uploads sobrevivam a um novo deploy do container.
 */
public class S3AssetStorage implements AssetStorage {

    private final S3Client client;
    private final AssetStorageProperties.S3 config;

    public S3AssetStorage(S3Client client, AssetStorageProperties.S3 config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public String store(MultipartFile file) {
        String stored = AssetNames.storedNameFor(file);
        try (var input = file.getInputStream()) {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key(stored));
            if (StringUtils.hasText(file.getContentType())) {
                request.contentType(file.getContentType());
            }
            client.putObject(request.build(), RequestBody.fromInputStream(input, file.getSize()));
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", ex);
        }
        return stored;
    }

    @Override
    public Resource load(String fileName) {
        String name = StringUtils.cleanPath(fileName);
        try {
            byte[] bytes = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key(name))
                    .build()).asByteArray();
            // O nome é preservado para que o MediaTypeFactory do controller acerte o content type.
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("Arquivo", fileName);
        }
    }

    @Override
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key(StringUtils.cleanPath(fileName)))
                    .build());
        } catch (S3Exception ignored) {
            // Objeto órfão no bucket não impede a remoção do registro.
        }
    }

    @Override
    public String publicUrl(String storedName) {
        String base = config.getPublicBaseUrl();
        if (!StringUtils.hasText(base)) {
            // Sem domínio público do bucket, o backend continua servindo o arquivo.
            return AssetNames.API_PATH + storedName;
        }
        return base.replaceAll("/+$", "") + "/" + key(storedName);
    }

    /** Chave final no bucket, com o prefixo de ambiente quando houver. */
    private String key(String storedName) {
        String prefix = config.getPrefix().replaceAll("^/+|/+$", "");
        return prefix.isEmpty() ? storedName : prefix + "/" + storedName;
    }
}
