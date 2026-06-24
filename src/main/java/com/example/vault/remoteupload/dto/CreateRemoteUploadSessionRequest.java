package com.example.vault.remoteupload.dto;

import com.example.vault.remoteupload.entity.RemoteUploadMode;

import java.util.UUID;

public record CreateRemoteUploadSessionRequest(
        UUID parentId,
        UUID spaceId,
        RemoteUploadMode mode
) {
    public CreateRemoteUploadSessionRequest {
        if (mode == null) {
            mode = RemoteUploadMode.DIRECT;
        }
    }

    public CreateRemoteUploadSessionRequest(UUID parentId) {
        this(parentId, null, RemoteUploadMode.DIRECT);
    }
}
