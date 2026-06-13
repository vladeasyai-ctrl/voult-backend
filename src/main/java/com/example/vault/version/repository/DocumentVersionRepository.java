package com.example.vault.version.repository;

import com.example.vault.version.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocumentIdOrderByVersionDesc(UUID documentId);

    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionDesc(UUID documentId);

    boolean existsByAssetId(UUID assetId);
}
