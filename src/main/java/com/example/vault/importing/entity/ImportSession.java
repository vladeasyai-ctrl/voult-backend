package com.example.vault.importing.entity;

import com.example.vault.common.AuditableEntity;
import com.example.vault.importing.dto.ImportProposalDto;
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

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "import_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSession extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false, unique = true)
    private UUID assetId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ImportProposalDto proposal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_folder_ids", columnDefinition = "jsonb")
    private List<UUID> createdFolderIds;

    @Column(name = "error_message")
    private String errorMessage;
}
