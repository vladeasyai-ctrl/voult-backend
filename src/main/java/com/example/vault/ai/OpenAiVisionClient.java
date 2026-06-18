package com.example.vault.ai;

import com.example.vault.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiVisionClient {

    private final ObjectMapper objectMapper;

    public String analyzeImage(
            String baseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            String userText,
            String mimeType,
            byte[] imageBytes
    ) {
        try {
            String dataUrl = "data:" + mimeType + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(imageBytes);

            var restClient = org.springframework.web.client.RestClient.builder()
                    .baseUrl(trimTrailingSlash(baseUrl))
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", userText),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                            ))
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
            log.error("OpenAI vision request failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                    "AI vision request failed: " + e.getMessage());
        }
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
