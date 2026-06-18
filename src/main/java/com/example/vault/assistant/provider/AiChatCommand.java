package com.example.vault.assistant.provider;

public record AiChatCommand(
        String systemPrompt,
        String userMessage,
        String apiKey,
        String model,
        String baseUrl,
        int timeoutSeconds
) {
}
