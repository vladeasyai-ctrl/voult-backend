package com.example.vault.assistant.service;

import com.example.vault.exception.ApiException;
import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultNodeResolver {

    private final NodeRepository nodeRepository;

    @Transactional(readOnly = true)
    public Node requireNode(UUID nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND",
                        "Node not found: " + nodeId));
    }

    @Transactional(readOnly = true)
    public Node requireFolder(UUID nodeId) {
        Node node = requireNode(nodeId);
        if (node.getType() != NodeType.FOLDER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_FOLDER", "Node is not a folder: " + node.getName());
        }
        return node;
    }

    @Transactional(readOnly = true)
    public Node resolveFolderByName(String name, UUID parentId) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Folder name is required");
        }
        List<Node> matches = nodeRepository.findAllByOrderByNameAsc().stream()
                .filter(n -> n.getType() == NodeType.FOLDER)
                .filter(n -> n.getName().equalsIgnoreCase(name.trim()))
                .filter(n -> parentId == null ? n.getParentId() == null : parentId.equals(n.getParentId()))
                .toList();

        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                    "Folder not found: " + name);
        }
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "AMBIGUOUS_FOLDER",
                    "Multiple folders named '" + name + "', use nodeId");
        }
        return matches.getFirst();
    }

    @Transactional(readOnly = true)
    public Node resolveFolderByPath(List<String> folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH", "Folder path is empty");
        }
        UUID currentParent = null;
        for (String segment : folderPath) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            Node folder = resolveFolderByName(segment.trim(), currentParent);
            currentParent = folder.getId();
        }
        return requireFolder(currentParent);
    }

    @Transactional(readOnly = true)
    public Node resolveAnyNodeByName(String name) {
        List<Node> matches = nodeRepository.findAllByOrderByNameAsc().stream()
                .filter(n -> n.getName().toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "No node matching: " + name);
        }
        if (matches.size() > 1) {
            List<String> ids = matches.stream().map(n -> n.getName() + " (" + n.getId() + ")").toList();
            throw new ApiException(HttpStatus.CONFLICT, "AMBIGUOUS_NODE",
                    "Multiple matches: " + String.join(", ", ids));
        }
        return matches.getFirst();
    }

    @Transactional(readOnly = true)
    public UUID resolveParentId(UUID parentNodeId, List<String> folderPath) {
        if (parentNodeId != null) {
            requireFolder(parentNodeId);
            return parentNodeId;
        }
        if (folderPath != null && !folderPath.isEmpty()) {
            if (folderPath.size() == 1) {
                return resolveFolderByName(folderPath.getFirst(), null).getId();
            }
            List<String> parents = new ArrayList<>(folderPath);
            Node parent = resolveFolderByPath(parents.subList(0, parents.size() - 1));
            return parent.getId();
        }
        return null;
    }
}
