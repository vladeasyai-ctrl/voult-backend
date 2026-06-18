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
        DocumentMetadata.DocumentMetadataBuilder builder = DocumentMetadata.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .aiSummary(aiSummary)
                .aiTags(aiTags)
                .aiStatus(AiStatus.COMPLETED)
                .aiProcessedAt(Instant.now());

        if (ocrText != null && !ocrText.isBlank()) {
            builder.ocrText(ocrText);
            builder.ocrStatus(AiStatus.COMPLETED);
        }
        if (classificationLabel != null && !classificationLabel.isBlank()) {
            builder.classificationLabel(classificationLabel);
            if (classificationConfidence != null) {
                builder.classificationConfidence(BigDecimal.valueOf(classificationConfidence));
            }
        }

        return metadataRepository.save(builder.build());
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

    @Transactional(readOnly = true)
    public Optional<DocumentMetadata> findByDocumentId(UUID documentId) {
        return metadataRepository.findByDocumentId(documentId);
    }
}
