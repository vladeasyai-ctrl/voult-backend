package com.example.vault.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    public static void load() {
        List<Path> candidates = List.of(
                Path.of(".env"),
                Path.of("..", ".env")
        );

        for (Path path : candidates) {
            if (!Files.exists(path)) {
                continue;
            }

            Path directory = path.getParent() != null ? path.getParent() : Path.of(".");
            Dotenv dotenv = Dotenv.configure()
                    .filename(path.getFileName().toString())
                    .directory(directory.toString())
                    .ignoreIfMissing()
                    .load();

            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });

            System.out.println("[vault] Loaded environment from: " + path.toAbsolutePath().normalize());
            return;
        }

        System.out.println("[vault] No .env file found (checked .env and ../.env)");
    }
}
