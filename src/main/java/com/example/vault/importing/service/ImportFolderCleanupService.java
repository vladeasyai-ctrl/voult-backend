package com.example.vault.importing.service;

import com.example.vault.node.repository.NodeRepository;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportFolderCleanupService {

    private final NodeService nodeService;
    private final NodeRepository nodeRepository;

    @Transactional
    public void pruneEmptyFolders(List<UUID> createdFolderIds) {
        if (createdFolderIds == null || createdFolderIds.isEmpty()) {
            return;
        }
        List<UUID> ordered = new ArrayList<>(createdFolderIds);
        Collections.reverse(ordered);
        for (UUID folderId : ordered) {
            if (!nodeRepository.existsById(folderId)) {
                continue;
            }
            if (nodeRepository.existsByParentId(folderId)) {
                continue;
            }
            try {
                nodeService.delete(folderId);
            } catch (Exception e) {
                log.debug("Skipped pruning folder {}: {}", folderId, e.getMessage());
            }
        }
    }
}
