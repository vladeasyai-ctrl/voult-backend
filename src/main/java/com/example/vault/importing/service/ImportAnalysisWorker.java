package com.example.vault.importing.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.service.NodeService;
import com.example.vault.space.repository.SpaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ImportAnalysisWorker {

    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final NodeService nodeService;
    private final SpaceRepository spaceRepository;
    private final ImportSessionService importSessionService;

    public ImportAnalysisWorker(
            DocumentAiAnalyzer documentAiAnalyzer,
            NodeService nodeService,
            SpaceRepository spaceRepository,
            @Lazy ImportSessionService importSessionService
    ) {
        this.documentAiAnalyzer = documentAiAnalyzer;
        this.nodeService = nodeService;
        this.spaceRepository = spaceRepository;
        this.importSessionService = importSessionService;
    }

    @Async
    public void analyzeAsync(UUID importId, String originalFilename) {
        try {
            List<TreeNodeDto> tree = new ArrayList<>();
            spaceRepository.findAllByOrderBySortOrderAscNameAsc()
                    .forEach(space -> tree.addAll(nodeService.getTree(space.getId())));
            var session = importSessionService.findSessionOrThrow(importId);
            ImportProposalDto proposal = documentAiAnalyzer.analyze(
                    session.getAssetId(),
                    originalFilename,
                    tree
            );
            importSessionService.markProposalReady(importId, proposal);
        } catch (Exception e) {
            log.error("AI analysis failed for import {}", importId, e);
            importSessionService.markFailed(importId, e.getMessage() != null ? e.getMessage() : "Analysis failed");
        }
    }
}
