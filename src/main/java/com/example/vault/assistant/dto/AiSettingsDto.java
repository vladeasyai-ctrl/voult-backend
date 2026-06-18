package com.example.vault.assistant.dto;

public record AiSettingsDto(
        String provider,
        String model,
        String baseUrl,
        boolean apiKeyConfigured,
        String apiKeyHint
) {
}
