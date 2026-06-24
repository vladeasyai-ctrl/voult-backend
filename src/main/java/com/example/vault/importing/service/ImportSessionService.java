package com.example.vault.importing.service;

import com.example.vault.ai.DocumentAiAnalyzer;
import com.example.vault.assistant.service.CurrentUserService;
import com.example.vault.asset.service.AssetService;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.service.DocumentService;
import com.example.vault.common.transaction.AfterCommitExecutor;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ConfirmImportRequest;
import com.example.vault.importing.dto.ImportEventDto;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.importing.dto.ImportSessionDto;
import com.example.vault.importing.entity.ImportSession;
import com.example.vault.importing.entity.ImportStatus;
import com.example.vault.importing.mapper.ImportSessionMapper;
import com.example.vault.importing.repository.ImportSessionRepository;
import com.example.vault.node.dto.ResolveImportResult;
import com.example.vault.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportSessionService {

    public static final String EVENT_UPLOAD_RECEIVED = "UPLOAD_RECEIVED";
    public static final String EVENT_STORING = "STORING";
    public static final String EVENT_STORAGE_COMPLETE = "STORAGE_COMPLETE";
    public static final String EVENT_ANALYZING = "ANALYZING";
    public static final String EVENT_PROPOSAL_READY = "PROPOSAL_READY";
    public static final String EVENT_FAILED = "FAILED";

    private static final EnumSet<ImportStatus> CONFIRMABLE = EnumSet.of(ImportStatus.PROPOSAL_READY);
    private static final EnumSet<ImportStatus> DISCARDABLE = EnumSet.of(
            ImportStatus.UPLOADED, ImportStatus.ANALYZING, ImportStatus.PROPOSAL_READY, ImportStatus.FAILED
    );

    private final ImportSessionRepository importSessionRepository;
    private final ImportSessionMapper importSessionMapper;
    private final AssetService assetService;
    private final DocumentAiAnalyzer documentAiAnalyzer;
    private final DocumentService documentService;
    private final ImportProcessingWorker processingWorker;
    private final ImportEventBroadcaster eventBroadcaster;
    private final ImportFolderCleanupService folderCleanupService;
    private final NodeService nodeService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final CurrentUserService currentUserService;

    @Transactional
    public ImportSessionDto create(MultipartFile file, UUID spaceId, UUID parentId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File must not be empty");
        }
        if (!documentAiAnalyzer.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                    "Document AI analyzer is not enabled");
        }

        byte[] content = readContent(file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        var asset = assetService.createAssetRecord(content, filename, mimeType, file.getSize());

        ImportSession session = ImportSession.builder()
                .id(UUID.randomUUID())
                .assetId(asset.id())
                .spaceId(spaceId)
                .parentId(parentId)
                .status(ImportStatus.UPLOADED)
                .build();

        ImportSession saved = importSessionRepository.save(session);
        UUID importId = saved.getId();
        UUID userId = currentUserService.findCurrentUserId().orElse(null);
        afterCommitExecutor.run(() ->
                processingWorker.processAsync(importId, content, filename, mimeType, userId));
        return importSessionMapper.toDto(saved);
    }

    public SseEmitter subscribeEvents(UUID id) {
        findSessionOrThrow(id);
        return eventBroadcaster.subscribe(id);
    }

    @Transactional(readOnly = true)
    public ImportSessionDto get(UUID id) {
        return importSessionMapper.toDto(findSessionOrThrow(id));
    }

    @Transactional
    public DocumentDto confirm(UUID id, ConfirmImportRequest request) {
        ImportSession session = findSessionOrThrow(id);
        if (!CONFIRMABLE.contains(session.getStatus()) || session.getDocumentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_IMPORT_STATE",
                    "Import must have an auto-created document before confirmation");
        }

        DocumentDto document = documentService.updateFromImport(session.getDocumentId(), request, session);
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
        if (session.getDocumentId() != null) {
            documentService.delete(session.getDocumentId());
            folderCleanupService.pruneEmptyFolders(session.getCreatedFolderIds());
        }
        assetService.deleteIfOrphan(assetId);

        session.setStatus(ImportStatus.DISCARDED);
        importSessionRepository.save(session);
    }

    @Transactional
    public ImportSession markAnalyzing(UUID id) {
        ImportSession session = findSessionOrThrow(id);
        session.setStatus(ImportStatus.ANALYZING);
        session.setErrorMessage(null);
        return importSessionRepository.save(session);
    }

    @Transactional
    public ImportCompletionResult completeWithAutoCreate(UUID id, ImportProposalDto proposal) {
        ImportSession session = findSessionOrThrow(id);
        session.setProposal(proposal);
        session.setErrorMessage(null);

        ResolveImportResult resolved = nodeService.resolveImportParentForProposal(
                session.getSpaceId(),
                session.getParentId(),
                proposal.folderPath(),
                proposal.createMissingFolders()
        );

        DocumentDto document = documentService.createFromImport(
                resolved.parentId(),
                proposal.title(),
                proposal.summary(),
                session.getAssetId(),
                proposal.tags(),
                proposal
        );

        session.setDocumentId(document.id());
        session.setCreatedFolderIds(resolved.createdFolderIds());
        session.setStatus(ImportStatus.PROPOSAL_READY);
        importSessionRepository.save(session);
        return new ImportCompletionResult(session, document);
    }

    @Transactional
    public void markFailed(UUID id, String errorMessage) {
        ImportSession session = findSessionOrThrow(id);
        session.setStatus(ImportStatus.FAILED);
        session.setErrorMessage(errorMessage);
        importSessionRepository.save(session);
        log.warn("Import {} failed: {}", id, errorMessage);
    }

    public void publishEvent(UUID importId, String type, ImportSession session, DocumentDto document, String message) {
        ImportSessionDto sessionDto = importSessionMapper.toDto(session);
        eventBroadcaster.publish(importId, new ImportEventDto(type, sessionDto, document, message));
    }

    ImportSession findSessionOrThrow(UUID id) {
        return importSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ImportSession", id));
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", "Failed to read uploaded file");
        }
    }

    public record ImportCompletionResult(ImportSession session, DocumentDto document) {
    }
}
