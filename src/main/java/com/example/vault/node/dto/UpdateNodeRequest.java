package com.example.vault.node.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNodeRequest(
        @NotBlank @Size(max = 255) String name
) {
}
