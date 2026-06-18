package com.example.vault.document.repository;

import com.example.vault.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByNodeId(UUID nodeId);

    boolean existsByAssetId(UUID assetId);

    @Query(value = """
            SELECT d.* FROM documents d
            LEFT JOIN document_metadata dm ON dm.document_id = d.id
            WHERE CAST(:query AS text) IS NULL OR TRIM(CAST(:query AS text)) = '' OR
                  LOWER(d.title) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%')) OR
                  LOWER(COALESCE(d.description, '')) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%')) OR
                  LOWER(COALESCE(dm.ai_summary, '')) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%')) OR
                  (dm.search_vector IS NOT NULL AND dm.search_vector @@ plainto_tsquery('simple', CAST(:query AS text)))
            ORDER BY d.title
            """, nativeQuery = true)
    List<Document> search(@Param("query") String query);
}
