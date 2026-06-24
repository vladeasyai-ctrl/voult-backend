package com.example.vault.importing.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.asset.service.AssetService;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.entity.NodeType;
import com.example.vault.node.service.NodeService;
import com.example.vault.space.repository.SpaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ImportProcessingWorker {

    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final NodeService nodeService;
    private final SpaceRepository spaceRepository;
    private final ImportSessionService importSessionService;
    private final AssetService assetService;

    public ImportProcessingWorker(
            DocumentAiAnalyzer documentAiAnalyzer,
            NodeService nodeService,
            SpaceRepository spaceRepository,
            @Lazy ImportSessionService importSessionService,
            AssetService assetService
    ) {
        this.documentAiAnalyzer = documentAiAnalyzer;
        this.nodeService = nodeService;
        this.spaceRepository = spaceRepository;
        this.importSessionService = importSessionService;
        this.assetService = assetService;
    }

    @Async
    public void processAsync(UUID importId, byte[] content, String filename, String mimeType, UUID userId) {
        try {
            var session = importSessionService.findSessionOrThrow(importId);
            importSessionService.publishEvent(
                    importId,
                    ImportSessionService.EVENT_UPLOAD_RECEIVED,
                    session,
                    null,
                    "Upload received"
            );

            importSessionService.publishEvent(
                    importId,
                    ImportSessionService.EVENT_STORING,
                    session,
                    null,
                    "Saving file"
            );
            importSessionService.markAnalyzing(importId);
            session = importSessionService.findSessionOrThrow(importId);

            importSessionService.publishEvent(
                    importId,
                    ImportSessionService.EVENT_ANALYZING,
                    session,
                    null,
                    "Analyzing file"
            );

            List<TreeNodeDto> tree = loadTree();
            UUID assetId = session.getAssetId();

            CompletableFuture<Void> storageFuture = CompletableFuture.runAsync(() -> {
                assetService.uploadToStorage(assetId, content, mimeType);
                var updated = importSessionService.findSessionOrThrow(importId);
                importSessionService.publishEvent(
                        importId,
                        ImportSessionService.EVENT_STORAGE_COMPLETE,
                        updated,
                        null,
                        "File saved"
                );
            });

            CompletableFuture<ImportProposalDto> aiFuture = CompletableFuture.supplyAsync(() ->
                    documentAiAnalyzer.analyze(content, mimeType, filename, tree, userId));

            CompletableFuture.allOf(storageFuture, aiFuture).join();
            ImportProposalDto proposal = aiFuture.join();

            List<TreeNodeDto> freshTree = loadTree();
            proposal = snapProposalFolderPath(proposal, freshTree);

            ImportSessionService.ImportCompletionResult result =
                    importSessionService.completeWithAutoCreate(importId, proposal);

            importSessionService.publishEvent(
                    importId,
                    ImportSessionService.EVENT_PROPOSAL_READY,
                    result.session(),
                    result.document(),
                    "Document created"
            );
        } catch (Exception e) {
            log.error("Import processing failed for {}", importId, e);
            String message = e.getMessage() != null ? e.getMessage() : "Import failed";
            importSessionService.markFailed(importId, message);
            try {
                var failed = importSessionService.findSessionOrThrow(importId);
                importSessionService.publishEvent(
                        importId,
                        ImportSessionService.EVENT_FAILED,
                        failed,
                        null,
                        message
                );
            } catch (Exception publishError) {
                log.debug("Failed to publish import failure event for {}", importId, publishError);
            }
        }
    }

    private List<TreeNodeDto> loadTree() {
        List<TreeNodeDto> tree = new ArrayList<>();
        spaceRepository.findAllByOrderBySortOrderAscNameAsc()
                .forEach(space -> tree.addAll(nodeService.getTree(space.getId())));
        return tree;
    }

    private ImportProposalDto snapProposalFolderPath(ImportProposalDto proposal, List<TreeNodeDto> tree) {
        if (proposal.folderPath() == null || proposal.folderPath().isEmpty()) {
            return proposal;
        }

        List<String> snapped = snapFolderPathToTree(proposal.folderPath(), tree);
        if (snapped.equals(proposal.folderPath())) {
            return proposal;
        }

        return new ImportProposalDto(
                proposal.title(),
                proposal.summary(),
                proposal.tags(),
                snapped,
                proposal.createMissingFolders(),
                proposal.confidence(),
                proposal.ocrText(),
                proposal.classificationLabel(),
                proposal.classificationConfidence()
        );
    }

    private List<String> snapFolderPathToTree(List<String> proposed, List<TreeNodeDto> tree) {
        List<String> normalized = proposed.stream()
                .filter(segment -> segment != null && !segment.isBlank())
                .map(String::trim)
                .toList();
        if (normalized.isEmpty()) {
            return proposed;
        }

        List<List<String>> knownPaths = new ArrayList<>();
        collectFolderPaths(tree, new ArrayList<>(), knownPaths);

        for (List<String> known : knownPaths) {
            if (segmentsEqualIgnoreCase(known, normalized)) {
                return known;
            }
        }

        if (normalized.size() == 1) {
            String target = normalized.getFirst();
            List<List<String>> matches = knownPaths.stream()
                    .filter(path -> path.getLast().equalsIgnoreCase(target))
                    .toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
        }

        for (List<String> known : knownPaths) {
            if (known.size() >= normalized.size()) {
                List<String> suffix = known.subList(known.size() - normalized.size(), known.size());
                if (segmentsEqualIgnoreCase(suffix, normalized)) {
                    return known;
                }
            }
        }

        return proposed;
    }

    private void collectFolderPaths(
            List<TreeNodeDto> nodes,
            List<String> prefix,
            List<List<String>> out
    ) {
        for (TreeNodeDto node : nodes) {
            if (node.type() != NodeType.FOLDER) {
                continue;
            }
            List<String> path = new ArrayList<>(prefix);
            path.add(node.name());
            out.add(List.copyOf(path));
            collectFolderPaths(node.children(), path, out);
        }
    }

    private boolean segmentsEqualIgnoreCase(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equalsIgnoreCase(right.get(i))) {
                return false;
            }
        }
        return true;
    }
}
