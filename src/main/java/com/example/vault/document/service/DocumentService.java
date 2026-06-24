package com.example.vault.document.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.assistant.service.CurrentUserService;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.common.event.DocumentCreatedEvent;
import com.example.vault.common.event.DomainEventPublisher;
import com.example.vault.common.transaction.AfterCommitExecutor;
import com.example.vault.document.dto.CreateDocumentRequest;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.entity.Document;
import com.example.vault.document.mapper.DocumentMapper;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ConfirmImportRequest;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.importing.entity.ImportSession;
import com.example.vault.metadata.entity.DocumentMetadata;
import com.example.vault.metadata.service.DocumentMetadataService;
import com.example.vault.node.dto.MoveNodeRequest;
import com.example.vault.node.dto.ResolveImportResult;
import com.example.vault.node.entity.Node;
import com.example.vault.node.repository.NodeRepository;
import com.example.vault.node.service.NodeService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final AssetRepository assetRepository;
    private final DocumentMetadataService metadataService;
    private final DomainEventPublisher eventPublisher;
    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final DocumentEnrichmentWorker enrichmentWorker;
    private final AfterCommitExecutor afterCommitExecutor;
    private final CurrentUserService currentUserService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentMapper documentMapper,
            NodeService nodeService,
            NodeRepository nodeRepository,
            AssetRepository assetRepository,
            DocumentMetadataService metadataService,
            DomainEventPublisher eventPublisher,
            DocumentAiAnalyzer documentAiAnalyzer,
            @Lazy DocumentEnrichmentWorker enrichmentWorker,
            AfterCommitExecutor afterCommitExecutor,
            CurrentUserService currentUserService
    ) {
        this.documentRepository = documentRepository;
        this.documentMapper = documentMapper;
        this.nodeService = nodeService;
        this.nodeRepository = nodeRepository;
        this.assetRepository = assetRepository;
        this.metadataService = metadataService;
        this.eventPublisher = eventPublisher;
        this.documentAiAnalyzer = documentAiAnalyzer;
        this.enrichmentWorker = enrichmentWorker;
        this.afterCommitExecutor = afterCommitExecutor;
        this.currentUserService = currentUserService;
    }

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

        if (Boolean.TRUE.equals(request.enrichWithAi()) && documentAiAnalyzer.isEnabled()) {
            metadataService.createPending(saved.getId());
            UUID documentId = saved.getId();
            UUID userId = currentUserService.findCurrentUserId().orElse(null);
            afterCommitExecutor.run(() -> enrichmentWorker.enrichAsync(documentId, userId));
        }

        metadataService.rebuildSearchVector(saved);

        eventPublisher.publish(new DocumentCreatedEvent(
                saved.getId(), saved.getNodeId(), saved.getAssetId(), saved.getTitle()));
        return toDto(saved);
    }

    @Transactional
    public void applyEnrichment(UUID documentId, ImportProposalDto proposal) {
        Document document = findDocumentOrThrow(documentId);
        if (proposal.title() != null && !proposal.title().isBlank()) {
            document.setTitle(proposal.title().trim());
        }
        if (proposal.summary() != null && !proposal.summary().isBlank()) {
            document.setDescription(proposal.summary().trim());
        }
        Document saved = documentRepository.save(document);

        nodeRepository.findById(saved.getNodeId()).ifPresent(node -> {
            node.setName(saved.getTitle());
            nodeRepository.save(node);
        });

        metadataService.completeFromAi(
                documentId,
                proposal.summary(),
                proposal.tags(),
                proposal.ocrText(),
                proposal.classificationLabel(),
                proposal.classificationConfidence()
        );
        metadataService.rebuildSearchVector(saved);
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
        return search(null, query);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> search(UUID spaceId, String query) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        List<Document> documents = spaceId == null
                ? documentRepository.search(normalized)
                : documentRepository.searchBySpace(spaceId, normalized);
        return documents.stream()
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
    public DocumentDto updateFromImport(UUID documentId, ConfirmImportRequest request, ImportSession session) {
        Document document = findDocumentOrThrow(documentId);
        document.setTitle(request.title());
        document.setDescription(request.summary());
        Document saved = documentRepository.save(document);

        metadataService.updateFromAi(saved.getId(), request.summary(), request.tags());

        List<String> folderPath = request.folderPath() != null
                ? request.folderPath()
                : (session.getProposal() != null ? session.getProposal().folderPath() : List.of());
        boolean createMissing = session.getProposal() != null && session.getProposal().createMissingFolders();

        ResolveImportResult resolved = nodeService.resolveImportParentWithTracking(
                request.spaceId() != null ? request.spaceId() : session.getSpaceId(),
                request.parentId() != null ? request.parentId() : session.getParentId(),
                folderPath,
                createMissing
        );

        Node documentNode = nodeRepository.findById(saved.getNodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Node", saved.getNodeId()));
        UUID currentParentId = documentNode.getParentId();
        if (!java.util.Objects.equals(currentParentId, resolved.parentId())) {
            nodeService.move(saved.getNodeId(), new MoveNodeRequest(resolved.parentId()));
        }

        metadataService.rebuildSearchVector(saved);
        return toDto(saved);
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
        DocumentMetadata metadata = metadataService.findByDocumentId(document.getId()).orElse(null);
        String aiSummary = metadata != null ? metadata.getAiSummary() : null;
        String aiStatus = metadata != null && metadata.getAiStatus() != null
                ? metadata.getAiStatus().name()
                : null;
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
                aiStatus,
                mimeType,
                base.createdAt(),
                base.updatedAt()
        );
    }
}
