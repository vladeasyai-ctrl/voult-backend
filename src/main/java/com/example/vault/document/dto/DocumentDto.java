package com.example.vault.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        UUID nodeId,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
