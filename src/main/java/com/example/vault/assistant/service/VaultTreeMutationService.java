package com.example.vault.assistant.service;

import com.example.vault.document.repository.DocumentRepository;
import com.example.vault.document.service.DocumentService;
import com.example.vault.exception.ApiException;
import com.example.vault.node.entity.Node;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultTreeMutationService {

    private final NodeRepository nodeRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Transactional
    public void deleteFolderRecursive(UUID folderId) {
        Node folder = nodeRepository.findById(folderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", "Folder not found"));

        if (folder.getType() != NodeType.FOLDER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_FOLDER", "Node is not a folder");
        }

        List<Node> children = nodeRepository.findAllByParentIdOrderByNameAsc(folderId);
        for (Node child : children) {
            if (child.getType() == NodeType.FOLDER) {
                deleteFolderRecursive(child.getId());
            } else {
                documentRepository.findByNodeId(child.getId())
                        .ifPresent(doc -> documentService.delete(doc.getId()));
            }
        }
        nodeRepository.delete(folder);
    }
}
