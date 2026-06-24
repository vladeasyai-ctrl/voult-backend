package com.example.vault.remoteupload.service;

import com.example.vault.asset.service.AssetService;
import com.example.vault.common.transaction.AfterCommitExecutor;
import com.example.vault.assistant.service.CurrentUserService;
import com.example.vault.document.dto.CreateDocumentRequest;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.service.DocumentService;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ImportSessionDto;
import com.example.vault.importing.service.ImportSessionService;
import com.example.vault.node.entity.Node;
import com.example.vault.node.repository.NodeRepository;
import com.example.vault.remoteupload.dto.CreateRemoteUploadSessionRequest;
import com.example.vault.remoteupload.dto.RemoteUploadEventDto;
import com.example.vault.remoteupload.dto.RemoteUploadPublicSessionDto;
import com.example.vault.remoteupload.dto.RemoteUploadSessionDto;
import com.example.vault.remoteupload.dto.RemoteUploadTokenResultDto;
import com.example.vault.remoteupload.entity.RemoteUploadMode;
import com.example.vault.remoteupload.entity.RemoteUploadSession;
import com.example.vault.remoteupload.entity.RemoteUploadStatus;
import com.example.vault.remoteupload.mapper.RemoteUploadSessionMapper;
import com.example.vault.remoteupload.repository.RemoteUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RemoteUploadSessionService {

    public static final String EVENT_FILE_UPLOADED = "FILE_UPLOADED";
    public static final String EVENT_IMPORT_CREATED = "IMPORT_CREATED";
    public static final String EVENT_SESSION_CLOSED = "SESSION_CLOSED";

    private static final int SESSION_TTL_MINUTES = 15;
    private static final int MAX_UPLOADS_PER_SESSION = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RemoteUploadSessionRepository sessionRepository;
    private final RemoteUploadSessionMapper sessionMapper;
    private final CurrentUserService currentUserService;
    private final NodeRepository nodeRepository;
    private final AssetService assetService;
    private final DocumentService documentService;
    private final ImportSessionService importSessionService;
    private final RemoteUploadEventBroadcaster eventBroadcaster;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public RemoteUploadSessionDto create(CreateRemoteUploadSessionRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        validateParentId(request.parentId());

        UUID spaceId = resolveSpaceId(request.spaceId(), request.parentId());
        RemoteUploadMode mode = request.mode() != null ? request.mode() : RemoteUploadMode.DIRECT;

        RemoteUploadSession session = RemoteUploadSession.builder()
                .id(UUID.randomUUID())
                .token(generateToken())
                .userId(userId)
                .parentId(request.parentId())
                .spaceId(spaceId)
                .mode(mode)
                .expiresAt(Instant.now().plus(SESSION_TTL_MINUTES, ChronoUnit.MINUTES))
                .status(RemoteUploadStatus.ACTIVE)
                .uploadCount(0)
                .build();

        return sessionMapper.toDto(sessionRepository.save(session));
    }

    @Transactional
    public RemoteUploadPublicSessionDto getPublicInfo(String token) {
        RemoteUploadSession session = findByTokenOrThrow(token);
        refreshExpiryStatus(session);
        return new RemoteUploadPublicSessionDto(
                session.getMode(),
                session.getStatus(),
                session.getExpiresAt(),
                isUsable(session)
        );
    }

    @Transactional
    public RemoteUploadTokenResultDto uploadByToken(String token, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File must not be empty");
        }

        RemoteUploadSession session = findByTokenOrThrow(token);
        ensureUsable(session);

        if (session.getUploadCount() >= MAX_UPLOADS_PER_SESSION) {
            throw new ApiException(HttpStatus.CONFLICT, "REMOTE_UPLOAD_LIMIT",
                    "Upload limit reached for this session");
        }

        session.setUploadCount(session.getUploadCount() + 1);
        sessionRepository.save(session);

        if (session.getMode() == RemoteUploadMode.AI_IMPORT) {
            ImportSessionDto importSession = importSessionService.create(
                    file,
                    session.getSpaceId(),
                    session.getParentId()
            );
            publishEvent(session, EVENT_IMPORT_CREATED, null, importSession.id(), null);
            return new RemoteUploadTokenResultDto(null, importSession.id());
        }

        var asset = assetService.upload(file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String title = filename.replaceAll("\\.[^.]+$", "");
        if (title.isBlank()) {
            title = filename;
        }

        DocumentDto document = documentService.create(new CreateDocumentRequest(
                session.getParentId(),
                title,
                null,
                asset.id(),
                false
        ));

        publishEvent(session, EVENT_FILE_UPLOADED, document, null, null);
        return new RemoteUploadTokenResultDto(document, null);
    }

    @Transactional
    public SseEmitter subscribeEvents(UUID sessionId) {
        RemoteUploadSession session = findOwnedSessionOrThrow(sessionId);
        refreshExpiryStatus(session);
        if (session.getStatus() != RemoteUploadStatus.ACTIVE) {
            throw new ApiException(HttpStatus.GONE, "REMOTE_UPLOAD_EXPIRED", "Upload session is no longer active");
        }
        return eventBroadcaster.subscribe(sessionId);
    }

    @Transactional
    public void close(UUID sessionId) {
        RemoteUploadSession session = findOwnedSessionOrThrow(sessionId);
        if (session.getStatus() == RemoteUploadStatus.ACTIVE) {
            session.setStatus(RemoteUploadStatus.CLOSED);
            sessionRepository.save(session);
            publishEvent(session, EVENT_SESSION_CLOSED, null, null, null);
        }
    }

    private UUID resolveSpaceId(UUID spaceId, UUID parentId) {
        if (spaceId != null) {
            return spaceId;
        }
        if (parentId == null) {
            return null;
        }
        return nodeRepository.findById(parentId)
                .map(Node::getSpaceId)
                .orElse(null);
    }

    private void validateParentId(UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (!nodeRepository.existsById(parentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NODE_NOT_FOUND", "Parent folder not found");
        }
    }

    private RemoteUploadSession findByTokenOrThrow(String token) {
        return sessionRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REMOTE_UPLOAD_INVALID",
                        "Upload session not found"));
    }

    private RemoteUploadSession findOwnedSessionOrThrow(UUID sessionId) {
        UUID userId = currentUserService.requireCurrentUserId();
        RemoteUploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("RemoteUploadSession", sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Upload session does not belong to current user");
        }
        return session;
    }

    private void ensureUsable(RemoteUploadSession session) {
        refreshExpiryStatus(session);
        if (!isUsable(session)) {
            throw new ApiException(HttpStatus.GONE, "REMOTE_UPLOAD_EXPIRED", "Upload session has expired");
        }
    }

    private void refreshExpiryStatus(RemoteUploadSession session) {
        if (session.getStatus() == RemoteUploadStatus.ACTIVE
                && session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(RemoteUploadStatus.EXPIRED);
            sessionRepository.save(session);
        }
    }

    private boolean isUsable(RemoteUploadSession session) {
        return session.getStatus() == RemoteUploadStatus.ACTIVE
                && session.getExpiresAt().isAfter(Instant.now());
    }

    private void publishEvent(
            RemoteUploadSession session,
            String type,
            DocumentDto document,
            UUID importId,
            String message
    ) {
        RemoteUploadSessionDto sessionDto = sessionMapper.toDto(session);
        UUID sessionId = session.getId();
        RemoteUploadEventDto event = new RemoteUploadEventDto(type, sessionDto, document, importId, message);
        afterCommitExecutor.run(() -> eventBroadcaster.publish(sessionId, event));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
