package com.example.vault.assistant.service;

import com.example.vault.assistant.dto.AiPlanDto;
import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.document.service.DocumentService;
import com.example.vault.exception.ApiException;
import com.example.vault.node.dto.CreateNodeRequest;
import com.example.vault.node.dto.MoveNodeRequest;
import com.example.vault.node.dto.UpdateNodeRequest;
import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultActionExecutor {

    private final NodeService nodeService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final VaultTreeMutationService treeMutationService;
    private final VaultNodeResolver nodeResolver;

    public record ActionExecutionResult(List<String> executed, List<String> errors) {
    }

    @Transactional
    public ActionExecutionResult executeAll(List<AiPlanDto.AiPlanActionDto> actions) {
        List<String> executed = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (actions == null || actions.isEmpty()) {
            return new ActionExecutionResult(executed, errors);
        }

        for (AiPlanDto.AiPlanActionDto action : actions) {
            try {
                executed.add(executeOne(action));
            } catch (Exception e) {
                errors.add(formatError(action, e));
            }
        }
        return new ActionExecutionResult(executed, errors);
    }

    private String executeOne(AiPlanDto.AiPlanActionDto action) {
        String type = action.type() != null ? action.type().trim().toUpperCase() : "";
        return switch (type) {
            case "CREATE_FOLDER", "CREATE_ROOT_FOLDER" -> createFolder(action);
            case "DELETE_FOLDER" -> deleteFolder(action);
            case "MOVE_NODE", "MOVE_FOLDER", "MOVE_DOCUMENT" -> moveNode(action);
            case "RENAME_NODE", "RENAME_FOLDER" -> renameNode(action);
            case "DELETE_DOCUMENT", "DELETE_FILE" -> deleteDocument(action);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ACTION",
                    "Unknown action type: " + action.type());
        };
    }

    private String createFolder(AiPlanDto.AiPlanActionDto action) {
        if (action.folderPath() != null && !action.folderPath().isEmpty()) {
            UUID folderId = nodeService.resolveOrCreateFolderPath(
                    action.parentNodeId(),
                    action.folderPath(),
                    true
            );
            Node folder = nodeResolver.requireFolder(folderId);
            return "Создан путь «" + folder.getName() + "» (" + String.join(" / ", action.folderPath()) + ")";
        }

        if (action.name() == null || action.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Folder name is required");
        }

        UUID parentId = action.parentNodeId();
        if (parentId != null) {
            nodeResolver.requireFolder(parentId);
        }

        var created = nodeService.create(new CreateNodeRequest(parentId, action.name().trim(), NodeType.FOLDER));
        return "Создана папка «" + created.name() + "»" + (parentId == null ? " (корень)" : "");
    }

    private String deleteFolder(AiPlanDto.AiPlanActionDto action) {
        UUID folderId = resolveNodeId(action, NodeType.FOLDER);
        Node folder = nodeResolver.requireFolder(folderId);
        treeMutationService.deleteFolderRecursive(folderId);
        return "Удалена папка «" + folder.getName() + "» со всем содержимым";
    }

    private String moveNode(AiPlanDto.AiPlanActionDto action) {
        UUID nodeId = resolveNodeId(action, null);
        UUID targetParentId = resolveTargetParent(action);
        var moved = nodeService.move(nodeId, new MoveNodeRequest(targetParentId));
        return "Перемещён узел «" + moved.name() + "»";
    }

    private UUID resolveTargetParent(AiPlanDto.AiPlanActionDto action) {
        if (action.targetParentNodeId() != null) {
            nodeResolver.requireFolder(action.targetParentNodeId());
            return action.targetParentNodeId();
        }
        if (action.targetFolderPath() != null && !action.targetFolderPath().isEmpty()) {
            return nodeResolver.resolveFolderByPath(action.targetFolderPath()).getId();
        }
        return null;
    }

    private String renameNode(AiPlanDto.AiPlanActionDto action) {
        UUID nodeId = resolveNodeId(action, null);
        if (action.newName() == null || action.newName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "New name is required");
        }
        var updated = nodeService.update(nodeId, new UpdateNodeRequest(action.newName().trim()));
        return "Переименован узел в «" + updated.name() + "»";
    }

    private String deleteDocument(AiPlanDto.AiPlanActionDto action) {
        UUID documentId = action.documentId();
        if (documentId == null && action.nodeId() != null) {
            documentId = documentRepository.findByNodeId(action.nodeId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                            "Document not found for node"))
                    .getId();
        }
        if (documentId == null && action.name() != null) {
            documentId = documentRepository.findAll().stream()
                    .filter(d -> d.getTitle().equalsIgnoreCase(action.name().trim()))
                    .map(d -> d.getId())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                            "Document not found: " + action.name()));
        }
        if (documentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_ID_REQUIRED", "documentId or nodeId required");
        }
        var doc = documentService.findDocumentOrThrow(documentId);
        documentService.delete(documentId);
        return "Удалён документ «" + doc.getTitle() + "»";
    }

    private UUID resolveNodeId(AiPlanDto.AiPlanActionDto action, NodeType expectedType) {
        if (action.nodeId() != null) {
            Node node = nodeResolver.requireNode(action.nodeId());
            if (expectedType != null && node.getType() != expectedType) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NODE_TYPE",
                        "Expected " + expectedType + " but got " + node.getType());
            }
            return node.getId();
        }
        if (action.name() != null && !action.name().isBlank()) {
            Node node = nodeResolver.resolveAnyNodeByName(action.name());
            if (expectedType != null && node.getType() != expectedType) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NODE_TYPE",
                        "Node '" + action.name() + "' is not a " + expectedType);
            }
            return node.getId();
        }
        if (action.folderPath() != null && !action.folderPath().isEmpty()) {
            Node folder = nodeResolver.resolveFolderByPath(action.folderPath());
            return folder.getId();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "NODE_REFERENCE_REQUIRED",
                "Specify nodeId, name, or folderPath");
    }

    private String formatError(AiPlanDto.AiPlanActionDto action, Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return (action.type() != null ? action.type() : "ACTION") + ": " + msg;
    }
}
