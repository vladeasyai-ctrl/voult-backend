package com.example.vault.module.ocr;

import java.util.UUID;

/**
 * Extension point for OCR processing of document assets.
 * Implementations should be registered as Spring beans when the OCR module is enabled.
 */
public interface OcrProcessor {

    void processDocument(UUID documentId, UUID assetId);

    boolean isEnabled();
}
