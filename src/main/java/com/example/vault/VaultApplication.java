package com.example.vault;

import com.example.vault.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VaultApplication {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(VaultApplication.class, args);
    }
}
