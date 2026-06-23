package com.example.vault.importing.dto;

import com.example.vault.document.dto.DocumentDto;

public record ImportEventDto(
        String type,
        ImportSessionDto session,
        DocumentDto document,
        String message
) {
}
