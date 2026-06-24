package com.example.vault.asset.dto;

public record TextPreviewResponse(
        String text,
        int totalLength,
        boolean truncated
) {
}
