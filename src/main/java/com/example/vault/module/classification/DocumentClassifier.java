package com.example.vault.module.classification;

import java.util.UUID;

/**
 * Extension point for AI-based document classification.
 */
public interface DocumentClassifier {

    void classifyDocument(UUID documentId, UUID assetId);

    boolean isEnabled();
}
