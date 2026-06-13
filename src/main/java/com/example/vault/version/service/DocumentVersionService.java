package com.example.vault.version.service;

import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.common.event.DocumentVersionCreatedEvent;
import com.example.vault.common.event.DomainEventPublisher;
import com.example.vault.document.service.DocumentService;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.version.dto.CreateVersionRequest;
import com.example.vault.version.dto.DocumentVersionDto;
import com.example.vault.version.entity.DocumentVersion;
import com.example.vault.version.mapper.DocumentVersionMapper;
import com.example.vault.version.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final DocumentVersionRepository versionRepository;
    private final DocumentVersionMapper versionMapper;
    private final DocumentService documentService;
    private final AssetRepository assetRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public DocumentVersionDto createVersion(UUID documentId, CreateVersionRequest request) {
        documentService.findDocumentOrThrow(documentId);
        assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        if (versionRepository.existsByAssetId(request.assetId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSET_ALREADY_USED",
                    "Asset is already linked to a document version");
        }

        int nextVersion = versionRepository.findTopByDocumentIdOrderByVersionDesc(documentId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        DocumentVersion version = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .assetId(request.assetId())
                .version(nextVersion)
                .build();

        DocumentVersion saved = versionRepository.save(version);
        eventPublisher.publish(new DocumentVersionCreatedEvent(
                saved.getId(), saved.getDocumentId(), saved.getAssetId(), saved.getVersion()));
        return versionMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionDto> getVersions(UUID documentId) {
        documentService.findDocumentOrThrow(documentId);
        return versionMapper.toDtoList(
                versionRepository.findAllByDocumentIdOrderByVersionDesc(documentId));
    }
}
