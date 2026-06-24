package com.example.vault.metadata.service;

import com.example.vault.document.entity.Document;
import com.example.vault.metadata.entity.AiStatus;
import com.example.vault.metadata.entity.DocumentMetadata;
import com.example.vault.metadata.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentMetadataService {

    private final DocumentMetadataRepository metadataRepository;

    @Transactional
    public DocumentMetadata createFromAi(
            UUID documentId,
            String aiSummary,
            List<String> aiTags
    ) {
        return createFromAi(documentId, aiSummary, aiTags, null, null, null);
    }

    @Transactional
    public DocumentMetadata createFromAi(
            UUID documentId,
            String aiSummary,
            List<String> aiTags,
            String ocrText,
            String classificationLabel,
            Double classificationConfidence
    ) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElseGet(() -> DocumentMetadata.builder()
                        .id(UUID.randomUUID())
                        .documentId(documentId)
                        .build());

        metadata.setAiSummary(aiSummary);
        metadata.setAiTags(aiTags);
        metadata.setAiStatus(AiStatus.COMPLETED);
        metadata.setAiProcessedAt(Instant.now());

        if (ocrText != null && !ocrText.isBlank()) {
            metadata.setOcrText(ocrText);
            metadata.setOcrStatus(AiStatus.COMPLETED);
        }
        if (classificationLabel != null && !classificationLabel.isBlank()) {
            metadata.setClassificationLabel(classificationLabel);
            if (classificationConfidence != null) {
                metadata.setClassificationConfidence(BigDecimal.valueOf(classificationConfidence));
            }
        }

        return metadataRepository.save(metadata);
    }

    @Transactional
    public void rebuildSearchVector(Document document) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(document.getId())
                .orElseGet(() -> metadataRepository.save(DocumentMetadata.builder()
                        .id(UUID.randomUUID())
                        .documentId(document.getId())
                        .build()));

        String tags = metadata.getAiTags() != null && !metadata.getAiTags().isEmpty()
                ? String.join(" ", metadata.getAiTags())
                : "";
        String summary = metadata.getAiSummary() != null ? metadata.getAiSummary() : "";

        metadataRepository.rebuildSearchVector(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                summary,
                tags
        );
    }

    @Transactional
    public void updateFromAi(UUID documentId, String aiSummary, List<String> aiTags) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalStateException("Metadata not found for document " + documentId));
        metadata.setAiSummary(aiSummary);
        metadata.setAiTags(aiTags);
        metadataRepository.save(metadata);
    }

    @Transactional
    public DocumentMetadata createPending(UUID documentId) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElseGet(() -> DocumentMetadata.builder()
                        .id(UUID.randomUUID())
                        .documentId(documentId)
                        .build());
        metadata.setAiStatus(AiStatus.PENDING);
        return metadataRepository.save(metadata);
    }

    @Transactional
    public void markProcessing(UUID documentId) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElseGet(() -> createPending(documentId));
        metadata.setAiStatus(AiStatus.PROCESSING);
        metadataRepository.save(metadata);
    }

    @Transactional
    public void markFailed(UUID documentId) {
        metadataRepository.findByDocumentId(documentId).ifPresent(metadata -> {
            metadata.setAiStatus(AiStatus.FAILED);
            metadata.setAiProcessedAt(Instant.now());
            metadataRepository.save(metadata);
        });
    }

    @Transactional
    public void completeFromAi(
            UUID documentId,
            String aiSummary,
            List<String> aiTags,
            String ocrText,
            String classificationLabel,
            Double classificationConfidence
    ) {
        DocumentMetadata metadata = metadataRepository.findByDocumentId(documentId)
                .orElseGet(() -> DocumentMetadata.builder()
                        .id(UUID.randomUUID())
                        .documentId(documentId)
                        .build());

        metadata.setAiSummary(aiSummary);
        metadata.setAiTags(aiTags);
        metadata.setAiStatus(AiStatus.COMPLETED);
        metadata.setAiProcessedAt(Instant.now());

        if (ocrText != null && !ocrText.isBlank()) {
            metadata.setOcrText(ocrText);
            metadata.setOcrStatus(AiStatus.COMPLETED);
        }
        if (classificationLabel != null && !classificationLabel.isBlank()) {
            metadata.setClassificationLabel(classificationLabel);
            if (classificationConfidence != null) {
                metadata.setClassificationConfidence(BigDecimal.valueOf(classificationConfidence));
            }
        }

        metadataRepository.save(metadata);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentMetadata> findByDocumentId(UUID documentId) {
        return metadataRepository.findByDocumentId(documentId);
    }
}
