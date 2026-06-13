package com.example.vault.module.search;

import java.util.UUID;

/**
 * Extension point for full-text search indexing.
 */
public interface FullTextSearchIndexer {

    void indexDocument(UUID documentId, String title, String description);

    void indexDocumentVersion(UUID versionId, UUID documentId, UUID assetId);

    boolean isEnabled();
}
