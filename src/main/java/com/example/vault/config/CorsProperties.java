package com.example.vault.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "vault.cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://localhost:5173"
    ));

    @Value("${CORS_ORIGINS:}")
    private String corsOriginsEnv;

    @PostConstruct
    void applyEnvOverride() {
        if (corsOriginsEnv == null || corsOriginsEnv.isBlank()) {
            return;
        }

        var merged = new ArrayList<>(allowedOrigins);
        Arrays.stream(corsOriginsEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(origin -> {
                    if (!merged.contains(origin)) {
                        merged.add(origin);
                    }
                });
        allowedOrigins = merged;
    }
}
