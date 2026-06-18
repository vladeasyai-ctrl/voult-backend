package com.example.vault.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class S3StorageConfig {

    private final StorageProperties storageProperties;

    @Bean
    public MinioClient minioClient() {
        var builder = MinioClient.builder()
                .endpoint(storageProperties.getEndpoint())
                .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey());

        if (StringUtils.hasText(storageProperties.getRegion())) {
            builder.region(storageProperties.getRegion());
        }

        return builder.build();
    }
}
