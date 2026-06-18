package com.example.vault.ai;

import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.node.dto.TreeNodeDto;

import java.util.List;
import java.util.UUID;

public interface DocumentAiAnalyzer {

    ImportProposalDto analyze(UUID assetId, String originalFilename, List<TreeNodeDto> tree);

    boolean isEnabled();
}
