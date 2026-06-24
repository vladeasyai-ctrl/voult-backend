package com.example.vault.node.dto;

import jakarta.validation.constraints.Size;

public record UpdateNodeRequest(
        @Size(max = 255) String name,
        @Size(max = 64) String iconKey,
        @Size(max = 32) String color,
        @Size(max = 5000) String description
) {
}
