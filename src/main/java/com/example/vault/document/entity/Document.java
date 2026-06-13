package com.example.vault.document.entity;

import com.example.vault.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "node_id", nullable = false, unique = true)
    private UUID nodeId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;
}
