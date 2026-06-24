package com.example.vault.remoteupload.dto;

import com.example.vault.document.dto.DocumentDto;

import java.util.UUID;

public record RemoteUploadEventDto(
        String type,
        RemoteUploadSessionDto session,
        DocumentDto document,
        UUID importId,
        String message
) {
}
