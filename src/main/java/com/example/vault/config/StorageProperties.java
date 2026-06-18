package com.example.vault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vault.storage")
public class StorageProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    /** Required for Cloudflare R2 — use "auto". Optional for local MinIO. */
    private String region;
    private int presignedUrlExpirySeconds = 3600;
    /** When false, bucket must already exist (recommended for production R2). */
    private boolean autoCreateBucket = true;
}
