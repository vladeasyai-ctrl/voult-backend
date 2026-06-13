package com.example.vault.common.event;

import java.util.UUID;

public record DocumentVersionCreatedEvent(
        UUID versionId,
        UUID documentId,
        UUID assetId,
        int versionNumber
) {
}
