package com.example.vault.assistant.dto;

import java.util.List;

public record AiChatResponseDto(
        String reply,
        List<String> executedActions,
        List<String> errors,
        boolean success
) {
}
