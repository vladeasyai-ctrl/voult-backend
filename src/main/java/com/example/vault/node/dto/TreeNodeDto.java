package com.example.vault.node.dto;

import com.example.vault.node.entity.NodeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TreeNodeDto(
        UUID id,
        UUID parentId,
        String name,
        NodeType type,
        Instant createdAt,
        Instant updatedAt,
        List<TreeNodeDto> children
) {
    public TreeNodeDto withChildren(List<TreeNodeDto> children) {
        return new TreeNodeDto(id, parentId, name, type, createdAt, updatedAt, children);
    }

    public static TreeNodeDto leaf(NodeDto node) {
        return new TreeNodeDto(
                node.id(), node.parentId(), node.name(), node.type(),
                node.createdAt(), node.updatedAt(), new ArrayList<>()
        );
    }
}
