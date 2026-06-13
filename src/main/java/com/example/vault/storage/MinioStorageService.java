package com.example.vault.storage;

import com.example.vault.config.MinioProperties;
import com.example.vault.exception.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    @Override
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                log.info("Created MinIO bucket: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "Failed to initialize storage bucket: " + e.getMessage());
        }
    }

    @Override
    public String upload(String objectKey, MultipartFile file) {
        try {
            return upload(objectKey, file.getInputStream(), file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED",
                    "Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    public String upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED",
                    "Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .expiry(minioProperties.getPresignedUrlExpirySeconds())
                    .build());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PRESIGN_FAILED",
                    "Failed to generate download URL: " + e.getMessage());
        }
    }
}
