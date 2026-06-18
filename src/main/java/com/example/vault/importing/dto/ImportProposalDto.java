package com.example.vault.importing.dto;

import java.util.List;

public record ImportProposalDto(
        String title,
        String summary,
        List<String> tags,
        List<String> folderPath,
        boolean createMissingFolders,
        Double confidence,
        String ocrText,
        String classificationLabel,
        Double classificationConfidence
) {
}
