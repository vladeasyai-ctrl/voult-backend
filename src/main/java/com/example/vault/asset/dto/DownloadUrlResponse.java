package com.example.vault.asset.dto;

public record DownloadUrlResponse(
        String url,
        String attachmentUrl,
        int expiresInSeconds
) {
}
