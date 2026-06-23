package com.example.vault.asset.service;

import com.example.vault.asset.dto.AssetDto;
import com.example.vault.asset.dto.DownloadUrlResponse;
import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.mapper.AssetMapper;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.config.StorageProperties;
import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.document.repository.DocumentRepository;
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
    private final StorageProperties storageProperties;
    private final DocumentRepository documentRepository;

    @Transactional
    public AssetDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File must not be empty");
        }
        byte[] content = readContent(file);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        AssetDto asset = createAssetRecord(content, file.getOriginalFilename(), contentType, file.getSize());
        uploadToStorage(asset.id(), content, contentType);
        return asset;
    }

    @Transactional
    public AssetDto createAssetRecord(byte[] content, String originalFilename, String contentType, Long size) {
        UUID assetId = UUID.randomUUID();
        String storageKey = buildStorageKey(assetId, originalFilename);
        String checksum = computeChecksum(content);
        String resolvedContentType = contentType != null ? contentType : "application/octet-stream";

        Asset asset = Asset.builder()
                .id(assetId)
                .storageKey(storageKey)
                .mimeType(resolvedContentType)
                .size(size != null ? size : content.length)
                .checksum(checksum)
                .build();

        return assetMapper.toDto(assetRepository.save(asset));
    }

    public void uploadToStorage(UUID assetId, byte[] content, String contentType) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
        String resolvedContentType = contentType != null ? contentType : asset.getMimeType();
        storageService.upload(
                asset.getStorageKey(),
                new java.io.ByteArrayInputStream(content),
                content.length,
                resolvedContentType
        );
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
        return new DownloadUrlResponse(url, storageProperties.getPresignedUrlExpirySeconds());
    }

    @Transactional
    public void deleteIfOrphan(UUID assetId) {
        if (documentRepository.existsByAssetId(assetId)) {
            return;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            return;
        }
        storageService.deleteObject(asset.getStorageKey());
        assetRepository.delete(asset);
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
