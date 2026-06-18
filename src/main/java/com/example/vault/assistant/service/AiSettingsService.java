package com.example.vault.assistant.service;

import com.example.vault.assistant.dto.AiSettingsDto;
import com.example.vault.assistant.dto.UpdateAiSettingsRequest;
import com.example.vault.assistant.entity.AiUserSettings;
import com.example.vault.assistant.repository.AiUserSettingsRepository;
import com.example.vault.config.AiProperties;
import com.example.vault.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiUserSettingsRepository settingsRepository;
    private final AiProperties aiProperties;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public AiSettingsDto getSettings() {
        UUID userId = currentUserService.requireCurrentUserId();
        return settingsRepository.findByUserId(userId)
                .map(this::toDto)
                .orElseGet(this::defaultDto);
    }

    @Transactional
    public AiSettingsDto updateSettings(UpdateAiSettingsRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        AiUserSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> AiUserSettings.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .provider(aiProperties.getDefaultProvider())
                        .build());

        settings.setProvider(request.provider());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            settings.setApiKey(request.apiKey().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            settings.setModel(request.model().trim());
        }
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            settings.setBaseUrl(request.baseUrl().trim());
        }

        return toDto(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public ResolvedAiConfig resolveConfig() {
        UUID userId = currentUserService.requireCurrentUserId();
        AiUserSettings settings = settingsRepository.findByUserId(userId).orElse(null);

        String provider = settings != null && settings.getProvider() != null
                ? settings.getProvider()
                : aiProperties.getDefaultProvider();

        String apiKey = firstNonBlank(
                settings != null ? settings.getApiKey() : null,
                aiProperties.getFallbackApiKey()
        );
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_API_KEY_MISSING",
                    "Configure an AI API key in settings or OPENAI_API_KEY env variable");
        }

        String model = firstNonBlank(
                settings != null ? settings.getModel() : null,
                aiProperties.getOpenai().getModel()
        );
        String baseUrl = firstNonBlank(
                settings != null ? settings.getBaseUrl() : null,
                aiProperties.getOpenai().getBaseUrl()
        );

        return new ResolvedAiConfig(provider, apiKey, model, baseUrl, aiProperties.getOpenai().getTimeoutSeconds());
    }

    private AiSettingsDto defaultDto() {
        return new AiSettingsDto(
                aiProperties.getDefaultProvider(),
                aiProperties.getOpenai().getModel(),
                aiProperties.getOpenai().getBaseUrl(),
                aiProperties.getFallbackApiKey() != null && !aiProperties.getFallbackApiKey().isBlank(),
                maskKey(aiProperties.getFallbackApiKey())
        );
    }

    private AiSettingsDto toDto(AiUserSettings settings) {
        boolean configured = settings.getApiKey() != null && !settings.getApiKey().isBlank();
        return new AiSettingsDto(
                settings.getProvider(),
                settings.getModel() != null ? settings.getModel() : aiProperties.getOpenai().getModel(),
                settings.getBaseUrl() != null ? settings.getBaseUrl() : aiProperties.getOpenai().getBaseUrl(),
                configured,
                maskKey(settings.getApiKey())
        );
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return null;
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    public record ResolvedAiConfig(
            String provider,
            String apiKey,
            String model,
            String baseUrl,
            int timeoutSeconds
    ) {
    }
}
