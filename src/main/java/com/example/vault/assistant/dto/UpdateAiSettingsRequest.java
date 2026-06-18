package com.example.vault.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAiSettingsRequest(
        @NotBlank @Size(max = 50) String provider,
        @Size(max = 500) String apiKey,
        @Size(max = 100) String model,
        @Size(max = 500) String baseUrl
) {
}
