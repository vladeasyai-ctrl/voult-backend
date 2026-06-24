package com.example.vault.node.dto;

import com.example.vault.node.entity.NodeType;

import java.time.Instant;
import java.util.UUID;

public record NodeDto(
        UUID id,
        UUID spaceId,
        UUID parentId,
        String name,
        NodeType type,
        String iconKey,
        String color,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
