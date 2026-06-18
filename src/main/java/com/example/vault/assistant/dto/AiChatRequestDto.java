package com.example.vault.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequestDto(
        @NotBlank @Size(max = 4000) String message
) {
}
