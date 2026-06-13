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

    @Query("SELECT d FROM Document d WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY d.title")
    List<Document> searchByTitle(@Param("query") String query);
}
