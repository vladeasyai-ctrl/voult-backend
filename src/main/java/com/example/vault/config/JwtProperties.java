package com.example.vault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vault.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;
}
