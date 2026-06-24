package com.example.vault.storage;

import com.example.vault.config.StorageProperties;
import com.example.vault.exception.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    @PostConstruct
    @Override
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(storageProperties.getBucket()).build());
            if (!exists) {
                if (!storageProperties.isAutoCreateBucket()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                            "Storage bucket does not exist: " + storageProperties.getBucket());
                }
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(storageProperties.getBucket()).build());
                log.info("Created storage bucket: {}", storageProperties.getBucket());
            }
        } catch (ApiException e) {
            throw e;
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
                    .bucket(storageProperties.getBucket())
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
        return generatePresignedDownloadUrl(objectKey, null, false);
    }

    @Override
    public String generatePresignedDownloadUrl(String objectKey, String filename, boolean attachment) {
        try {
            var builder = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .expiry(storageProperties.getPresignedUrlExpirySeconds());

            Map<String, String> queryParams = contentDispositionParams(filename, attachment);
            if (!queryParams.isEmpty()) {
                builder.extraQueryParams(queryParams);
            }

            return minioClient.getPresignedObjectUrl(builder.build());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PRESIGN_FAILED",
                    "Failed to generate download URL: " + e.getMessage());
        }
    }

    private Map<String, String> contentDispositionParams(String filename, boolean attachment) {
        if (filename == null || filename.isBlank()) {
            return Map.of();
        }
        String trimmed = filename.trim();
        String asciiFallback = trimmed.replaceAll("[^\\x20-\\x7E]", "_");
        if (asciiFallback.isBlank()) {
            asciiFallback = "download";
        }
        String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (attachment ? "attachment" : "inline")
                + "; filename=\"" + asciiFallback + "\""
                + "; filename*=UTF-8''" + encoded;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response-content-disposition", disposition);
        return params;
    }

    @Override
    public byte[] download(String objectKey) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(storageProperties.getBucket())
                        .object(objectKey)
                        .build());
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            stream.transferTo(buffer);
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DOWNLOAD_FAILED",
                    "Failed to download file: " + e.getMessage());
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DELETE_FAILED",
                    "Failed to delete file: " + e.getMessage());
        }
    }
}
