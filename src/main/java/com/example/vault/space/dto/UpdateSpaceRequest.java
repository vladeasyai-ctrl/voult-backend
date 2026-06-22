package com.example.vault.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateSpaceRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 64) String presetId,
        Map<String, Object> settings
) {
}
