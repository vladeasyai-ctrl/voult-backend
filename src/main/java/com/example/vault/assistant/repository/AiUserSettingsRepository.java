package com.example.vault.assistant.repository;

import com.example.vault.assistant.entity.AiUserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiUserSettingsRepository extends JpaRepository<AiUserSettings, UUID> {

    Optional<AiUserSettings> findByUserId(UUID userId);
}
