package com.example.vault.importing.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.asset.service.AssetService;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.service.DocumentService;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ConfirmImportRequest;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.importing.dto.ImportSessionDto;
import com.example.vault.importing.entity.ImportSession;
import com.example.vault.importing.entity.ImportStatus;
import com.example.vault.importing.mapper.ImportSessionMapper;
import com.example.vault.importing.repository.ImportSessionRepository;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportSessionService {

    private static final EnumSet<ImportStatus> ANALYZABLE = EnumSet.of(
            ImportStatus.UPLOADED, ImportStatus.FAILED, ImportStatus.PROPOSAL_READY
    );
    private static final EnumSet<ImportStatus> CONFIRMABLE = EnumSet.of(
            ImportStatus.PROPOSAL_READY
    );
    private static final EnumSet<ImportStatus> DISCARDABLE = EnumSet.of(
            ImportStatus.UPLOADED, ImportStatus.ANALYZING, ImportStatus.PROPOSAL_READY, ImportStatus.FAILED
    );

    private final ImportSessionRepository importSessionRepository;
    private final ImportSessionMapper importSessionMapper;
    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final NodeService nodeService;
    private final DocumentService documentService;
    private final ImportAnalysisWorker analysisWorker;

    @Transactional
    public ImportSessionDto create(MultipartFile file) {
        var asset = assetService.upload(file);

        ImportSession session = ImportSession.builder()
                .id(UUID.randomUUID())
                .assetId(asset.id())
                .status(ImportStatus.UPLOADED)
                .build();

        return importSessionMapper.toDto(importSessionRepository.save(session));
    }

    @Transactional
    public ImportSessionDto startAnalysis(UUID id) {
        ImportSession session = findSessionOrThrow(id);
        if (!ANALYZABLE.contains(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_IMPORT_STATE",
                    "Import cannot be analyzed in status " + session.getStatus());
        }
        if (!documentAiAnalyzer.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                    "Document AI analyzer is not enabled");
        }

        session.setStatus(ImportStatus.ANALYZING);
        session.setErrorMessage(null);
        session.setProposal(null);
        importSessionRepository.save(session);

        String filename = assetRepository.findById(session.getAssetId())
                .map(asset -> asset.getStorageKey())
                .map(key -> key.substring(key.lastIndexOf('/') + 1))
                .orElse("file");

        analysisWorker.analyzeAsync(id, filename);
        return importSessionMapper.toDto(session);
    }

    @Transactional(readOnly = true)
    public ImportSessionDto get(UUID id) {
        return importSessionMapper.toDto(findSessionOrThrow(id));
    }

    @Transactional
    public DocumentDto confirm(UUID id, ConfirmImportRequest request) {
        ImportSession session = findSessionOrThrow(id);
        if (!CONFIRMABLE.contains(session.getStatus()) || session.getProposal() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_IMPORT_STATE",
                    "Import must have a ready proposal before confirmation");
        }

        UUID parentId = nodeService.resolveImportParent(
                request.spaceId(),
                request.parentId(),
                request.folderPath() != null ? request.folderPath() : session.getProposal().folderPath(),
                session.getProposal().createMissingFolders()
        );

        DocumentDto document = documentService.createFromImport(
                parentId,
                request.title(),
                request.summary(),
                session.getAssetId(),
                request.tags() != null ? request.tags() : session.getProposal().tags(),
                session.getProposal()
        );

        session.setStatus(ImportStatus.CONFIRMED);
        importSessionRepository.save(session);
        return document;
    }

    @Transactional
    public void discard(UUID id) {
        ImportSession session = findSessionOrThrow(id);
        if (!DISCARDABLE.contains(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_IMPORT_STATE",
                    "Import cannot be discarded in status " + session.getStatus());
        }

        UUID assetId = session.getAssetId();
        session.setStatus(ImportStatus.DISCARDED);
        importSessionRepository.save(session);
        assetService.deleteIfOrphan(assetId);
    }

    @Transactional
    public void markProposalReady(UUID id, ImportProposalDto proposal) {
        ImportSession session = findSessionOrThrow(id);
        session.setProposal(proposal);
        session.setStatus(ImportStatus.PROPOSAL_READY);
        session.setErrorMessage(null);
        importSessionRepository.save(session);
    }

    @Transactional
    public void markFailed(UUID id, String errorMessage) {
        ImportSession session = findSessionOrThrow(id);
        session.setStatus(ImportStatus.FAILED);
        session.setErrorMessage(errorMessage);
        importSessionRepository.save(session);
        log.warn("Import {} analysis failed: {}", id, errorMessage);
    }

    ImportSession findSessionOrThrow(UUID id) {
        return importSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ImportSession", id));
    }
}
