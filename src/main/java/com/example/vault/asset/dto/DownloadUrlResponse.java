package com.example.vault.asset.dto;

public record DownloadUrlResponse(
        String url,
        int expiresInSeconds
) {
}
