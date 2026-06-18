package com.example.vault.ai;

import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.entity.NodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Placeholder analyzer until an external AI provider is wired in.
 * Uses filename heuristics and existing folder names from the vault tree.
 */
@Component
@RequiredArgsConstructor
public class StubDocumentAiAnalyzer implements DocumentAiAnalyzer {

    private final AssetRepository assetRepository;

    @Override
    public ImportProposalDto analyze(UUID assetId, String originalFilename, List<TreeNodeDto> tree) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));

        String filename = originalFilename != null && !originalFilename.isBlank()
                ? originalFilename
                : extractFilename(asset.getStorageKey());
        String baseName = stripExtension(filename);
        String lower = baseName.toLowerCase(Locale.ROOT);

        List<String> tags = inferTags(lower, asset.getMimeType());
        String summary = buildSummary(baseName, tags);
        List<String> folderPath = suggestFolderPath(lower, tags, tree);
        String title = prettifyTitle(baseName);

        return new ImportProposalDto(
                title,
                summary,
                tags,
                folderPath,
                true,
                0.75,
                null,
                null,
                null
        );
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private List<String> inferTags(String lowerName, String mimeType) {
        Set<String> tags = new LinkedHashSet<>();
        if (lowerName.contains("passport") || lowerName.contains("paszport") || lowerName.contains("паспорт")) {
            tags.add("passport");
            tags.add("identity");
        }
        if (lowerName.contains("invoice") || lowerName.contains("faktura") || lowerName.contains("счет")) {
            tags.add("invoice");
        }
        if (lowerName.contains("contract") || lowerName.contains("umowa") || lowerName.contains("договор")) {
            tags.add("contract");
        }
        if (mimeType != null && mimeType.startsWith("image/")) {
            tags.add("photo");
        }
        if (mimeType != null && mimeType.contains("pdf")) {
            tags.add("pdf");
        }
        if (tags.isEmpty()) {
            tags.add("document");
        }
        return List.copyOf(tags);
    }

    private String buildSummary(String baseName, List<String> tags) {
        if (tags.contains("passport")) {
            if (baseName.toLowerCase(Locale.ROOT).contains("pl") || baseName.toLowerCase(Locale.ROOT).contains("pol")) {
                return "Паспорт Польша — " + prettifyTitle(baseName);
            }
            return "Документ удостоверения личности — " + prettifyTitle(baseName);
        }
        return prettifyTitle(baseName);
    }

    private List<String> suggestFolderPath(String lowerName, List<String> tags, List<TreeNodeDto> tree) {
        List<String> existing = flattenFolderPaths(tree);
        if (tags.contains("passport")) {
            String match = existing.stream()
                    .filter(path -> path.toLowerCase(Locale.ROOT).contains("passport")
                            || path.toLowerCase(Locale.ROOT).contains("паспорт")
                            || path.toLowerCase(Locale.ROOT).contains("paszport"))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                return List.of(match.split("/"));
            }
            return List.of("Документы", "Личное", "Паспорта");
        }
        if (tags.contains("invoice")) {
            return List.of("Документы", "Финансы", "Счета");
        }
        if (tags.contains("contract")) {
            return List.of("Документы", "Работа", "Договоры");
        }
        if (!existing.isEmpty()) {
            String first = existing.getFirst();
            return List.of(first.split("/"));
        }
        return List.of("Документы", "Неразобранное");
    }

    private List<String> flattenFolderPaths(List<TreeNodeDto> nodes) {
        List<String> paths = new ArrayList<>();
        collectFolderPaths(nodes, "", paths);
        return paths;
    }

    private void collectFolderPaths(List<TreeNodeDto> nodes, String prefix, List<String> paths) {
        for (TreeNodeDto node : nodes) {
            if (node.type() != NodeType.FOLDER) {
                continue;
            }
            String path = prefix.isEmpty() ? node.name() : prefix + "/" + node.name();
            paths.add(path);
            collectFolderPaths(node.children(), path, paths);
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String extractFilename(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }

    private String prettifyTitle(String baseName) {
        return baseName.replace('_', ' ').replace('-', ' ').trim();
    }
}
