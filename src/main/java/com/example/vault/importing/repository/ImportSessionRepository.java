package com.example.vault.importing.repository;

import com.example.vault.importing.entity.ImportSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportSessionRepository extends JpaRepository<ImportSession, UUID> {
}
