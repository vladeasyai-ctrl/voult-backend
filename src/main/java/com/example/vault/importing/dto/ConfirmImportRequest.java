package com.example.vault.importing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ConfirmImportRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 5000) String summary,
        List<@NotBlank @Size(max = 100) String> tags,
        List<@NotBlank @Size(max = 255) String> folderPath,
        UUID spaceId,
        UUID parentId
) {
}
