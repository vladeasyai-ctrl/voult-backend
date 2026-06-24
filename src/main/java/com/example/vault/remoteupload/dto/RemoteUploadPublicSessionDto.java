package com.example.vault.remoteupload.dto;

import com.example.vault.remoteupload.entity.RemoteUploadMode;
import com.example.vault.remoteupload.entity.RemoteUploadStatus;

import java.time.Instant;

public record RemoteUploadPublicSessionDto(
        RemoteUploadMode mode,
        RemoteUploadStatus status,
        Instant expiresAt,
        boolean valid
) {
}
