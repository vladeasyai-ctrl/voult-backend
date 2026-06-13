package com.example.vault.node.dto;

import java.util.UUID;

public record MoveNodeRequest(
        UUID parentId
) {
}
