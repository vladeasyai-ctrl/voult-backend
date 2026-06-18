package com.example.vault.assistant.provider;

public interface AiChatProvider {

    String providerId();

    String complete(AiChatCommand command);
}
