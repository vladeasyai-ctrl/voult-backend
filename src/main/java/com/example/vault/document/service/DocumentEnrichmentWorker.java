package com.example.vault.document.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.metadata.service.DocumentMetadataService;
import com.example.vault.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
public class DocumentEnrichmentWorker {

    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final DocumentMetadataService metadataService;
    private final DocumentService documentService;

    public DocumentEnrichmentWorker(
            DocumentAiAnalyzer documentAiAnalyzer,
            AssetRepository assetRepository,
            StorageService storageService,
            DocumentMetadataService metadataService,
            @Lazy DocumentService documentService
    ) {
        this.documentAiAnalyzer = documentAiAnalyzer;
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.metadataService = metadataService;
        this.documentService = documentService;
    }

    @Async
    public void enrichAsync(UUID documentId, UUID userId) {
        if (!documentAiAnalyzer.isEnabled()) {
            return;
        }

        try {
            metadataService.markProcessing(documentId);
            var document = documentService.findDocumentOrThrow(documentId);
            Asset asset = assetRepository.findById(document.getAssetId())
                    .orElseThrow(() -> new IllegalStateException("Asset not found for document " + documentId));

            byte[] content = storageService.download(asset.getStorageKey());
            String filename = resolveEnrichmentFilename(asset);
            ImportProposalDto proposal = documentAiAnalyzer.analyze(
                    content,
                    asset.getMimeType(),
                    filename,
                    Collections.emptyList(),
                    userId
            );

            documentService.applyEnrichment(documentId, proposal);
        } catch (Throwable e) {
            log.warn("Document enrichment failed for {}", documentId, e);
            metadataService.markFailed(documentId);
        }
    }

    private String resolveEnrichmentFilename(Asset asset) {
        String fromKey = extractFilename(asset.getStorageKey());
        if (fromKey.contains(".")) {
            return fromKey;
        }
        String mime = asset.getMimeType();
        if (mime != null && mime.contains("wordprocessingml")) {
            return fromKey + ".docx";
        }
        if (mime != null && mime.contains("spreadsheetml")) {
            return fromKey + ".xlsx";
        }
        if (mime != null && mime.contains("msword")) {
            return fromKey + ".doc";
        }
        return fromKey;
    }

    private String extractFilename(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }
}
