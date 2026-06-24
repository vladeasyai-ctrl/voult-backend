package com.example.vault.node.dto;

import com.example.vault.node.entity.NodeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TreeNodeDto(
        UUID id,
        UUID spaceId,
        UUID parentId,
        String name,
        NodeType type,
        String iconKey,
        String color,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<TreeNodeDto> children
) {
    public TreeNodeDto withChildren(List<TreeNodeDto> children) {
        return new TreeNodeDto(
                id, spaceId, parentId, name, type, iconKey, color, description,
                createdAt, updatedAt, children
        );
    }

    public static TreeNodeDto leaf(NodeDto node) {
        return new TreeNodeDto(
                node.id(), node.spaceId(), node.parentId(), node.name(), node.type(),
                node.iconKey(), node.color(), node.description(),
                node.createdAt(), node.updatedAt(), new ArrayList<>()
        );
    }
}
