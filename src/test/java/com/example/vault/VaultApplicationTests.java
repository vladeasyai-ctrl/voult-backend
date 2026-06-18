package com.example.vault;

import com.example.vault.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers
class VaultApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vault")
            .withUsername("vault")
            .withPassword("vault");

    @MockBean
    private StorageService storageService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("vault.storage.endpoint", () -> "http://localhost:9000");
        registry.add("vault.storage.access-key", () -> "minioadmin");
        registry.add("vault.storage.secret-key", () -> "minioadmin");
        registry.add("vault.storage.bucket", () -> "vault-assets-test");
    }

    @BeforeEach
    void setUpStorage() {
        doNothing().when(storageService).ensureBucketExists();
    }

    @Test
    void contextLoads() {
    }
}
