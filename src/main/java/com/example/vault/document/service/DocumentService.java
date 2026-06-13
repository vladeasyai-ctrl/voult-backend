package com.example.vault.document.service;

import com.example.vault.common.event.DocumentCreatedEvent;
import com.example.vault.common.event.DomainEventPublisher;
import com.example.vault.document.dto.CreateDocumentRequest;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.entity.Document;
import com.example.vault.document.mapper.DocumentMapper;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.node.entity.Node;
import com.example.vault.node.repository.NodeRepository;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
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
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public DocumentDto create(CreateDocumentRequest request) {
        Node node = nodeService.createDocumentNode(request.parentId(), request.title());

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .nodeId(node.getId())
                .title(request.title())
                .description(request.description())
                .build();

        Document saved = documentRepository.save(document);
        eventPublisher.publish(new DocumentCreatedEvent(saved.getId(), saved.getNodeId(), saved.getTitle()));
        return documentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public DocumentDto getById(UUID id) {
        return documentMapper.toDto(findDocumentOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> search(String query) {
        if (query == null || query.isBlank()) {
            return documentRepository.findAll().stream()
                    .map(documentMapper::toDto)
                    .toList();
        }
        return documentRepository.searchByTitle(query.trim()).stream()
                .map(documentMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDto getByNodeId(UUID nodeId) {
        return documentRepository.findByNodeId(nodeId)
                .map(documentMapper::toDto)
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
}
