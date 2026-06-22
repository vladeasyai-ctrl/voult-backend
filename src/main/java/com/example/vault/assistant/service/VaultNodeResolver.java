package com.example.vault.assistant.service;

import com.example.vault.exception.ApiException;
import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.repository.NodeRepository;
import com.example.vault.space.repository.SpaceRepository;
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
    private final SpaceRepository spaceRepository;

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
    public Node resolveFolderByName(String name, UUID spaceId, UUID parentId) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Folder name is required");
        }
        UUID resolvedSpaceId = resolveSpaceId(spaceId, parentId);
        return nodeRepository.findFolderBySpaceParentAndName(
                        resolvedSpaceId,
                        parentId,
                        name.trim(),
                        NodeType.FOLDER
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                        "Folder not found: " + name));
    }

    @Transactional(readOnly = true)
    public Node resolveFolderByPath(UUID spaceId, List<String> folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH", "Folder path is empty");
        }
        UUID resolvedSpaceId = resolveSpaceId(spaceId, null, folderPath);
        UUID currentParent = null;
        for (String segment : folderPath) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            Node folder = resolveFolderByName(segment.trim(), resolvedSpaceId, currentParent);
            currentParent = folder.getId();
        }
        return requireFolder(currentParent);
    }

    @Transactional(readOnly = true)
    public Node resolveAnyNodeByName(String name) {
        List<Node> matches = nodeRepository.findAll().stream()
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
    public UUID resolveParentId(UUID spaceId, UUID parentNodeId, List<String> folderPath) {
        if (parentNodeId != null) {
            requireFolder(parentNodeId);
            return parentNodeId;
        }
        if (folderPath != null && !folderPath.isEmpty()) {
            UUID resolvedSpaceId = resolveSpaceId(spaceId, null, folderPath);
            if (folderPath.size() == 1) {
                return resolveFolderByName(folderPath.getFirst(), resolvedSpaceId, null).getId();
            }
            List<String> parents = new ArrayList<>(folderPath);
            Node parent = resolveFolderByPath(resolvedSpaceId, parents.subList(0, parents.size() - 1));
            return parent.getId();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public UUID resolveSpaceId(UUID explicitSpaceId, UUID parentNodeId) {
        if (parentNodeId != null) {
            return requireNode(parentNodeId).getSpaceId();
        }
        return resolveSpaceId(explicitSpaceId, null, List.of());
    }

    @Transactional(readOnly = true)
    public UUID resolveSpaceId(UUID explicitSpaceId, UUID parentNodeId, List<String> folderPath) {
        if (parentNodeId != null) {
            return requireNode(parentNodeId).getSpaceId();
        }
        if (explicitSpaceId != null) {
            if (!spaceRepository.existsById(explicitSpaceId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space not found");
            }
            return explicitSpaceId;
        }
        if (folderPath != null && !folderPath.isEmpty()) {
            String first = folderPath.getFirst().trim();
            List<UUID> spaceIds = nodeRepository.findDistinctSpaceIdsByRootBranchName(first);
            if (spaceIds.size() == 1) {
                return spaceIds.getFirst();
            }
            if (spaceIds.size() > 1) {
                throw new ApiException(HttpStatus.CONFLICT, "AMBIGUOUS_SPACE",
                        "Root branch '" + first + "' exists in multiple spaces; specify spaceId");
            }
        }
        return defaultSpaceId();
    }

    private UUID defaultSpaceId() {
        var spaces = spaceRepository.findAllByOrderBySortOrderAscNameAsc();
        if (spaces.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_SPACES", "No spaces available");
        }
        if (spaces.size() == 1) {
            return spaces.getFirst().getId();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "SPACE_REQUIRED",
                "Multiple spaces exist; specify spaceId or parentNodeId");
    }
}
