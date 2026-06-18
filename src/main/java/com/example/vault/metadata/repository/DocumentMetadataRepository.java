package com.example.vault.metadata.repository;

import com.example.vault.metadata.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    Optional<DocumentMetadata> findByDocumentId(UUID documentId);

    @Modifying
    @Query(value = """
            UPDATE document_metadata dm
            SET search_vector =
                setweight(to_tsvector('simple', coalesce(:title, '')), 'A') ||
                setweight(to_tsvector('simple', coalesce(:description, '')), 'B') ||
                setweight(to_tsvector('simple', coalesce(:summary, '')), 'A') ||
                setweight(to_tsvector('simple', coalesce(:tags, '')), 'C'),
                updated_at = NOW()
            WHERE dm.document_id = :documentId
            """, nativeQuery = true)
    void rebuildSearchVector(
            @Param("documentId") UUID documentId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("summary") String summary,
            @Param("tags") String tags
    );
}
