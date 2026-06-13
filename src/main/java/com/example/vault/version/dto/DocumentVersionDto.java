package com.example.vault.version.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionDto(
        UUID id,
        UUID documentId,
        UUID assetId,
        Integer version,
        Instant createdAt
) {
}
