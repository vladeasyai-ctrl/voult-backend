package com.example.vault.assistant.provider;

import com.example.vault.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiProviderRegistry {

    private final Map<String, AiChatProvider> providers;

    public AiProviderRegistry(List<AiChatProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AiChatProvider::providerId, Function.identity()));
    }

    public AiChatProvider requireProvider(String providerId) {
        AiChatProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_PROVIDER_UNSUPPORTED",
                    "Unsupported AI provider: " + providerId);
        }
        return provider;
    }
}
