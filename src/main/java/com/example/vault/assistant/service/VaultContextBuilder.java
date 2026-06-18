package com.example.vault.assistant.service;

import com.example.vault.document.entity.Document;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.service.NodeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VaultContextBuilder {

    private final NodeService nodeService;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String buildContextJson() {
        List<TreeNodeDto> tree = nodeService.getTree();
        Map<UUID, Document> documentsByNode = documentRepository.findAll().stream()
                .collect(Collectors.toMap(Document::getNodeId, d -> d, (a, b) -> a));

        List<VaultNodeContext> nodes = new ArrayList<>();
        flatten(tree, documentsByNode, nodes);

        try {
            return objectMapper.writeValueAsString(Map.of("nodes", nodes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vault context", e);
        }
    }

    private void flatten(List<TreeNodeDto> tree, Map<UUID, Document> documentsByNode, List<VaultNodeContext> out) {
        for (TreeNodeDto node : tree) {
            Document document = documentsByNode.get(node.id());
            out.add(new VaultNodeContext(
                    node.id(),
                    node.name(),
                    node.type().name(),
                    node.parentId(),
                    document != null ? document.getId() : null
            ));
            flatten(node.children(), documentsByNode, out);
        }
    }

    private record VaultNodeContext(
            UUID id,
            String name,
            String type,
            UUID parentId,
            UUID documentId
    ) {
    }
}
