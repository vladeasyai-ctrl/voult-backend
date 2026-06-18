package com.example.vault.metadata.entity;

import com.example.vault.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false, unique = true)
    private UUID documentId;

    @Column(name = "ocr_text")
    private String ocrText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status")
    private AiStatus ocrStatus;

    @Column(name = "classification_label")
    private String classificationLabel;

    @Column(name = "classification_confidence", precision = 5, scale = 4)
    private BigDecimal classificationConfidence;

    @Column(name = "ai_summary")
    private String aiSummary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ai_tags", columnDefinition = "text[]")
    private List<String> aiTags;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status")
    private AiStatus aiStatus;

    @Column(name = "ai_processed_at")
    private Instant aiProcessedAt;
}
