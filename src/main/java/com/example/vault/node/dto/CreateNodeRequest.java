package com.example.vault.node.dto;

import com.example.vault.node.entity.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateNodeRequest(
        @NotNull UUID spaceId,
        UUID parentId,
        @NotBlank @Size(max = 255) String name,
        @NotNull NodeType type,
        @Size(max = 64) String iconKey,
        @Size(max = 32) String color,
        @Size(max = 5000) String description
) {
}
