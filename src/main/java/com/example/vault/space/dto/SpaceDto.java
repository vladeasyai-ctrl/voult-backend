package com.example.vault.space.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SpaceDto(
        UUID id,
        String name,
        String presetId,
        int sortOrder,
        Map<String, Object> settings,
        Instant createdAt,
        Instant updatedAt
) {
}
