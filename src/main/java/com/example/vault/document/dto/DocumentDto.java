package com.example.vault.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        UUID nodeId,
        UUID assetId,
        String title,
        String description,
        String aiSummary,
        String aiStatus,
        String mimeType,
        Instant createdAt,
        Instant updatedAt
) {
}
