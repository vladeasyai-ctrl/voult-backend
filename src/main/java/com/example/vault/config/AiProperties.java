package com.example.vault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vault.ai")
public class AiProperties {

    private boolean enabled = true;
    private String defaultProvider = "openai";
    private String fallbackApiKey;
    private OpenAi openai = new OpenAi();

    @Getter
    @Setter
    public static class OpenAi {
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private int timeoutSeconds = 60;
    }
}
