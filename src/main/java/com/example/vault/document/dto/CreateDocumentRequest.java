package com.example.vault.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDocumentRequest(
        UUID parentId,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 5000) String description,
        @NotNull UUID assetId
) {
}
