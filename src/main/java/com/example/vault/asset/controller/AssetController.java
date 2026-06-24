package com.example.vault.asset.controller;

import com.example.vault.asset.dto.AssetDto;
import com.example.vault.asset.dto.DownloadUrlResponse;
import com.example.vault.asset.dto.TextPreviewResponse;
import com.example.vault.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "File upload and download")
public class AssetController {

    private final AssetService assetService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file to storage")
    public AssetDto upload(@RequestPart("file") MultipartFile file) {
        return assetService.upload(file);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset metadata")
    public AssetDto getById(@PathVariable UUID id) {
        return assetService.getById(id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Get presigned download URL")
    public DownloadUrlResponse download(
            @PathVariable UUID id,
            @RequestParam(required = false) String filename
    ) {
        return assetService.getDownloadUrl(id, filename);
    }

    @GetMapping("/{id}/text-preview")
    @Operation(summary = "Get extracted text preview for office and text documents")
    public TextPreviewResponse textPreview(@PathVariable UUID id) {
        return assetService.getTextPreview(id);
    }
}
