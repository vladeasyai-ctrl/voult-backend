package com.example.vault.remoteupload.dto;

import com.example.vault.document.dto.DocumentDto;

import java.util.UUID;

public record RemoteUploadTokenResultDto(
        DocumentDto document,
        UUID importId
) {
}
