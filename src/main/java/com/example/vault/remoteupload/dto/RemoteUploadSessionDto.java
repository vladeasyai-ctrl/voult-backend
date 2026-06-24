package com.example.vault.remoteupload.dto;

import com.example.vault.remoteupload.entity.RemoteUploadMode;
import com.example.vault.remoteupload.entity.RemoteUploadStatus;

import java.time.Instant;
import java.util.UUID;

public record RemoteUploadSessionDto(
        UUID id,
        String token,
        UUID parentId,
        UUID spaceId,
        RemoteUploadMode mode,
        Instant expiresAt,
        RemoteUploadStatus status,
        int uploadCount,
        Instant createdAt
) {
}
