package com.example.vault.module.telegram;

import com.example.vault.common.event.DocumentCreatedEvent;
import com.example.vault.common.event.DocumentVersionCreatedEvent;

/**
 * Extension point for Telegram bot notifications.
 */
public interface TelegramNotifier {

    void onDocumentCreated(DocumentCreatedEvent event);

    void onDocumentVersionCreated(DocumentVersionCreatedEvent event);

    boolean isEnabled();
}
