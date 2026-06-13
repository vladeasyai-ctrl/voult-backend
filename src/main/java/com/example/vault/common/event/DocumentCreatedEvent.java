package com.example.vault.common.event;

import java.util.UUID;

public record DocumentCreatedEvent(
        UUID documentId,
        UUID nodeId,
        String title
) {
}
