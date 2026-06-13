package com.example.vault.asset.dto;

import java.time.Instant;
import java.util.UUID;

public record AssetDto(
        UUID id,
        String storageKey,
        String mimeType,
        Long size,
        String checksum,
        Instant createdAt
) {
}
