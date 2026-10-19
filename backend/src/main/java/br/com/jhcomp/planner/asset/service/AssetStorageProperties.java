package br.com.jhcomp.planner.asset.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jhcomp.assets")
public class AssetStorageProperties {

    /** Onde gravar os uploads: disco da máquina (padrão) ou um bucket S3/R2. */
    public enum Backend { DISK, S3 }

    private Backend storage = Backend.DISK;

    /** Pasta onde os arquivos enviados pelo site são gravados (storage=disk). */
    private String dir = "uploads";

    private final S3 s3 = new S3();

    public Backend getStorage() { return storage; }
    public void setStorage(Backend storage) { this.storage = storage; }

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }

    public S3 getS3() { return s3; }

    /** Bucket compatível com S3 — serve para AWS S3 e para o Cloudflare R2. */
    public static class S3 {

        private String bucket = "";

        /** Região da AWS; no R2 qualquer valor serve ("auto"). */
        private String region = "us-east-1";

        /** Endpoint próprio do provedor (R2, MinIO). Vazio = endpoint padrão da AWS. */
        private String endpoint = "";

        private String accessKey = "";

        private String secretKey = "";

        /** Prefixo de chave dentro do bucket, para separar ambientes. */
        private String prefix = "";

        /**
         * Base de URL pública do bucket (CDN ou domínio do R2). Quando preenchida, a API
         * devolve a URL direta do arquivo; vazia, o backend continua servindo em
         * /api/assets/files/{nome}.
         */
        private String publicBaseUrl = "";

        /** Caminho em vez de subdomínio no host do bucket (necessário no MinIO). */
        private boolean pathStyleAccess = false;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }

        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
    }
}
