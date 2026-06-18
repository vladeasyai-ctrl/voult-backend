package com.example.vault.assistant.provider;

import com.example.vault.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiChatProvider implements AiChatProvider {

    private final ObjectMapper objectMapper;

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public String complete(AiChatCommand command) {
        try {
            var restClient = org.springframework.web.client.RestClient.builder()
                    .baseUrl(trimTrailingSlash(command.baseUrl()))
                    .defaultHeader("Authorization", "Bearer " + command.apiKey())
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> body = Map.of(
                    "model", command.model(),
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", command.systemPrompt()),
                            Map.of("role", "user", "content", command.userMessage())
                    )
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "AI returned empty response");
            }
            return content.asText();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI request failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                    "AI provider request failed: " + e.getMessage());
        }
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
