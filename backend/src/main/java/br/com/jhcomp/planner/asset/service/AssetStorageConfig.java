package br.com.jhcomp.planner.asset.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/** Escolhe o storage de assets por {@code jhcomp.assets.storage} (disk é o padrão). */
@Configuration
public class AssetStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "jhcomp.assets.storage", havingValue = "disk", matchIfMissing = true)
    public AssetStorage diskAssetStorage(AssetStorageProperties properties) {
        return new DiskAssetStorage(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "jhcomp.assets.storage", havingValue = "s3")
    public AssetStorage s3AssetStorage(AssetStorageProperties properties) {
        AssetStorageProperties.S3 config = properties.getS3();
        if (!StringUtils.hasText(config.getBucket())) {
            throw new IllegalStateException(
                    "jhcomp.assets.storage=s3 exige ASSETS_S3_BUCKET (jhcomp.assets.s3.bucket).");
        }

        S3ClientBuilder builder = S3Client.builder().region(Region.of(config.getRegion()));
        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        if (config.isPathStyleAccess()) {
            builder.forcePathStyle(true);
        }
        // Sem chaves explícitas, usa a cadeia padrão do SDK (role da instância, perfil, env).
        builder.credentialsProvider(
                StringUtils.hasText(config.getAccessKey()) && StringUtils.hasText(config.getSecretKey())
                        ? StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()))
                        : DefaultCredentialsProvider.create());

        return new S3AssetStorage(builder.build(), config);
    }
}
