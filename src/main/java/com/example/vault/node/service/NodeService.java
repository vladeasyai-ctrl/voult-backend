package com.example.vault.node.service;

import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.node.dto.CreateNodeRequest;
import com.example.vault.node.dto.MoveNodeRequest;
import com.example.vault.node.dto.NodeDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.dto.UpdateNodeRequest;
import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.mapper.NodeMapper;
import com.example.vault.node.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final NodeMapper nodeMapper;

    @Transactional(readOnly = true)
    public List<TreeNodeDto> getTree() {
        List<Node> allNodes = nodeRepository.findAllByOrderByNameAsc();
        Map<UUID, List<Node>> childrenByParent = allNodes.stream()
                .filter(node -> node.getParentId() != null)
                .collect(Collectors.groupingBy(Node::getParentId));

        return allNodes.stream()
                .filter(node -> node.getParentId() == null)
                .map(root -> buildTreeNode(root, childrenByParent))
                .toList();
    }

    @Transactional
    public NodeDto create(CreateNodeRequest request) {
        validateParent(request.parentId());
        if (request.type() == NodeType.DOCUMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NODE_TYPE",
                    "DOCUMENT nodes must be created via document API");
        }

        Node node = Node.builder()
                .id(UUID.randomUUID())
                .parentId(request.parentId())
                .name(request.name())
                .type(request.type())
                .build();

        return nodeMapper.toDto(nodeRepository.save(node));
    }

    @Transactional
    public NodeDto update(UUID id, UpdateNodeRequest request) {
        Node node = findNodeOrThrow(id);
        node.setName(request.name());
        return nodeMapper.toDto(nodeRepository.save(node));
    }

    @Transactional
    public NodeDto move(UUID id, MoveNodeRequest request) {
        Node node = findNodeOrThrow(id);
        if (node.getParentId() == null && request.parentId() == null) {
            return nodeMapper.toDto(node);
        }
        if (request.parentId() != null && request.parentId().equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MOVE", "Cannot move node into itself");
        }
        if (request.parentId() != null && isDescendant(request.parentId(), id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MOVE", "Cannot move node into its descendant");
        }
        validateParent(request.parentId());
        node.setParentId(request.parentId());
        return nodeMapper.toDto(nodeRepository.save(node));
    }

    @Transactional
    public void delete(UUID id) {
        Node node = findNodeOrThrow(id);
        if (node.getType() == NodeType.DOCUMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_DOCUMENT_NODE",
                    "Delete the document instead of its node directly");
        }
        if (nodeRepository.existsByParentId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "NODE_HAS_CHILDREN",
                    "Cannot delete folder with children");
        }
        nodeRepository.delete(node);
    }

    @Transactional(readOnly = true)
    public Node findNodeOrThrow(UUID id) {
        return nodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Node", id));
    }

    public Node createDocumentNode(UUID parentId, String name) {
        validateParent(parentId);
        Node node = Node.builder()
                .id(UUID.randomUUID())
                .parentId(parentId)
                .name(name)
                .type(NodeType.DOCUMENT)
                .build();
        return nodeRepository.save(node);
    }

    private void validateParent(UUID parentId) {
        if (parentId == null) {
            return;
        }
        Node parent = findNodeOrThrow(parentId);
        if (parent.getType() != NodeType.FOLDER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARENT",
                    "Parent must be a FOLDER node");
        }
    }

    private boolean isDescendant(UUID candidateParentId, UUID nodeId) {
        UUID current = candidateParentId;
        while (current != null) {
            if (current.equals(nodeId)) {
                return true;
            }
            current = nodeRepository.findById(current)
                    .map(Node::getParentId)
                    .orElse(null);
        }
        return false;
    }

    private TreeNodeDto buildTreeNode(Node node, Map<UUID, List<Node>> childrenByParent) {
        List<TreeNodeDto> children = childrenByParent.getOrDefault(node.getId(), List.of()).stream()
                .map(child -> buildTreeNode(child, childrenByParent))
                .toList();

        NodeDto dto = nodeMapper.toDto(node);
        return TreeNodeDto.leaf(dto).withChildren(new ArrayList<>(children));
    }
}
