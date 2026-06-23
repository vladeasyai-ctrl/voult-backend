package com.example.vault.node.dto;

import java.util.List;
import java.util.UUID;

public record ResolveImportResult(
        UUID parentId,
        List<UUID> createdFolderIds
) {
}
