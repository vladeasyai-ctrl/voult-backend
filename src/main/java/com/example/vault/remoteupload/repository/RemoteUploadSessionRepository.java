package com.example.vault.remoteupload.repository;

import com.example.vault.remoteupload.entity.RemoteUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemoteUploadSessionRepository extends JpaRepository<RemoteUploadSession, UUID> {

    Optional<RemoteUploadSession> findByToken(String token);
}
