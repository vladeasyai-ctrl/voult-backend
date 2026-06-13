package com.example.vault.asset.service;

import com.example.vault.asset.dto.AssetDto;
import com.example.vault.asset.dto.DownloadUrlResponse;
import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.mapper.AssetMapper;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.config.MinioProperties;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetMapper assetMapper;
    private final StorageService storageService;
    private final MinioProperties minioProperties;

    @Transactional
    public AssetDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File must not be empty");
        }

        UUID assetId = UUID.randomUUID();
        String storageKey = buildStorageKey(assetId, file.getOriginalFilename());
        byte[] content = readContent(file);
        String checksum = computeChecksum(content);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        storageService.upload(storageKey, new java.io.ByteArrayInputStream(content), content.length, contentType);

        Asset asset = Asset.builder()
                .id(assetId)
                .storageKey(storageKey)
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .size(file.getSize())
                .checksum(checksum)
                .build();

        return assetMapper.toDto(assetRepository.save(asset));
    }

    @Transactional(readOnly = true)
    public AssetDto getById(UUID id) {
        return assetMapper.toDto(assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id)));
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse getDownloadUrl(UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));

        String url = storageService.generatePresignedDownloadUrl(asset.getStorageKey());
        return new DownloadUrlResponse(url, minioProperties.getPresignedUrlExpirySeconds());
    }

    private String buildStorageKey(UUID assetId, String originalFilename) {
        String safeName = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        return "assets/" + assetId + "/" + safeName;
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", "Failed to read uploaded file");
        }
    }

    private String computeChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CHECKSUM_FAILED",
                    "Failed to compute file checksum");
        }
    }
}
