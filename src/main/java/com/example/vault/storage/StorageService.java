package com.example.vault.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface StorageService {

    String upload(String objectKey, MultipartFile file);

    String upload(String objectKey, InputStream inputStream, long size, String contentType);

    String generatePresignedDownloadUrl(String objectKey);

    void ensureBucketExists();
}
