package com.example.vault.importing.dto;

import com.example.vault.importing.entity.ImportStatus;

import java.time.Instant;
import java.util.UUID;

public record ImportSessionDto(
        UUID id,
        UUID assetId,
        UUID documentId,
        UUID spaceId,
        UUID parentId,
        ImportStatus status,
        ImportProposalDto proposal,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
