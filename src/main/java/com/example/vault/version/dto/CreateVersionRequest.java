package com.example.vault.version.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVersionRequest(
        @NotNull UUID assetId
) {
}
