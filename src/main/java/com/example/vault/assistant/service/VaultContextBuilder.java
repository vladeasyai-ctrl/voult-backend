package com.example.vault.assistant.service;

import com.example.vault.document.entity.Document;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.service.NodeService;
import com.example.vault.space.entity.Space;
import com.example.vault.space.repository.SpaceRepository;
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
    private final SpaceRepository spaceRepository;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String buildContextJson() {
        Map<UUID, Document> documentsByNode = documentRepository.findAll().stream()
                .collect(Collectors.toMap(Document::getNodeId, d -> d, (a, b) -> a));

        List<VaultSpaceContext> spaces = new ArrayList<>();
        for (Space space : spaceRepository.findAllByOrderBySortOrderAscNameAsc()) {
            List<TreeNodeDto> tree = nodeService.getTree(space.getId());
            List<VaultNodeContext> nodes = new ArrayList<>();
            flatten(tree, documentsByNode, nodes);
            spaces.add(new VaultSpaceContext(space.getId(), space.getName(), nodes));
        }

        try {
            return objectMapper.writeValueAsString(Map.of("spaces", spaces));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vault context", e);
        }
    }

    private void flatten(List<TreeNodeDto> tree, Map<UUID, Document> documentsByNode, List<VaultNodeContext> out) {
        for (TreeNodeDto node : tree) {
            Document document = documentsByNode.get(node.id());
            out.add(new VaultNodeContext(
                    node.id(),
                    node.spaceId(),
                    node.name(),
                    node.type().name(),
                    node.parentId(),
                    document != null ? document.getId() : null
            ));
            flatten(node.children(), documentsByNode, out);
        }
    }

    private record VaultSpaceContext(UUID id, String name, List<VaultNodeContext> nodes) {
    }

    private record VaultNodeContext(
            UUID id,
            UUID spaceId,
            String name,
            String type,
            UUID parentId,
            UUID documentId
    ) {
    }
}
