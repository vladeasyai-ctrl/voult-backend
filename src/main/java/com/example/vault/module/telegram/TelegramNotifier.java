package com.example.vault.module.telegram;

import com.example.vault.common.event.DocumentCreatedEvent;

/**
 * Extension point for Telegram bot notifications.
 */
public interface TelegramNotifier {

    void onDocumentCreated(DocumentCreatedEvent event);

    boolean isEnabled();
}
