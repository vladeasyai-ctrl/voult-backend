package com.example.vault.document.service;

import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.common.event.DocumentCreatedEvent;
import com.example.vault.common.event.DomainEventPublisher;
import com.example.vault.document.dto.CreateDocumentRequest;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.entity.Document;
import com.example.vault.document.mapper.DocumentMapper;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.metadata.entity.DocumentMetadata;
import com.example.vault.metadata.service.DocumentMetadataService;
import com.example.vault.node.entity.Node;
import com.example.vault.node.repository.NodeRepository;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final AssetRepository assetRepository;
    private final DocumentMetadataService metadataService;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public DocumentDto create(CreateDocumentRequest request) {
        validateAssetAvailable(request.assetId());

        Node node = nodeService.createDocumentNode(request.parentId(), request.title());

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .nodeId(node.getId())
                .title(request.title())
                .description(request.description())
                .assetId(request.assetId())
                .build();

        Document saved = documentRepository.save(document);
        metadataService.rebuildSearchVector(saved);
        eventPublisher.publish(new DocumentCreatedEvent(
                saved.getId(), saved.getNodeId(), saved.getAssetId(), saved.getTitle()));
        return toDto(saved);
    }

    @Transactional
    public DocumentDto createFromImport(
            UUID parentId,
            String title,
            String summary,
            UUID assetId,
            List<String> tags
    ) {
        return createFromImport(parentId, title, summary, assetId, tags, null);
    }

    @Transactional
    public DocumentDto createFromImport(
            UUID parentId,
            String title,
            String summary,
            UUID assetId,
            List<String> tags,
            ImportProposalDto proposal
    ) {
        validateAssetAvailable(assetId);

        Node node = nodeService.createDocumentNode(parentId, title);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .nodeId(node.getId())
                .title(title)
                .description(summary)
                .assetId(assetId)
                .build();

        Document saved = documentRepository.save(document);
        if (proposal != null) {
            metadataService.createFromAi(
                    saved.getId(),
                    summary,
                    tags,
                    proposal.ocrText(),
                    proposal.classificationLabel(),
                    proposal.classificationConfidence()
            );
        } else {
            metadataService.createFromAi(saved.getId(), summary, tags);
        }
        metadataService.rebuildSearchVector(saved);

        eventPublisher.publish(new DocumentCreatedEvent(
                saved.getId(), saved.getNodeId(), saved.getAssetId(), saved.getTitle()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public DocumentDto getById(UUID id) {
        return toDto(findDocumentOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> search(String query) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return documentRepository.search(normalized).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDto getByNodeId(UUID nodeId) {
        return documentRepository.findByNodeId(nodeId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "node:" + nodeId));
    }

    @Transactional
    public void delete(UUID id) {
        Document document = findDocumentOrThrow(id);
        UUID nodeId = document.getNodeId();
        documentRepository.delete(document);
        nodeRepository.deleteById(nodeId);
    }

    public Document findDocumentOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    private void validateAssetAvailable(UUID assetId) {
        assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
        if (documentRepository.existsByAssetId(assetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSET_ALREADY_USED",
                    "Asset is already linked to a document");
        }
    }

    private DocumentDto toDto(Document document) {
        DocumentDto base = documentMapper.toDto(document);
        String aiSummary = metadataService.findByDocumentId(document.getId())
                .map(DocumentMetadata::getAiSummary)
                .orElse(null);
        String mimeType = assetRepository.findById(document.getAssetId())
                .map(asset -> asset.getMimeType())
                .orElse(null);
        return new DocumentDto(
                base.id(),
                base.nodeId(),
                base.assetId(),
                base.title(),
                base.description(),
                aiSummary,
                mimeType,
                base.createdAt(),
                base.updatedAt()
        );
    }
}
