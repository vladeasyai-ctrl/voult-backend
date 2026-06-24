package com.example.vault.remoteupload.controller;

import com.example.vault.remoteupload.dto.RemoteUploadTokenResultDto;
import com.example.vault.remoteupload.dto.CreateRemoteUploadSessionRequest;
import com.example.vault.remoteupload.dto.RemoteUploadPublicSessionDto;
import com.example.vault.remoteupload.dto.RemoteUploadSessionDto;
import com.example.vault.remoteupload.service.RemoteUploadSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/remote-upload-sessions")
@RequiredArgsConstructor
@Tag(name = "Remote upload", description = "Phone upload via QR token")
public class RemoteUploadSessionController {

    private final RemoteUploadSessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a remote upload session (authenticated)")
    public RemoteUploadSessionDto create(@RequestBody(required = false) CreateRemoteUploadSessionRequest request) {
        CreateRemoteUploadSessionRequest body = request != null ? request : new CreateRemoteUploadSessionRequest(null);
        return sessionService.create(body);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to remote upload events (authenticated)")
    public SseEmitter events(@PathVariable UUID id) {
        return sessionService.subscribeEvents(id);
    }

    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Close remote upload session (authenticated)")
    public void close(@PathVariable UUID id) {
        sessionService.close(id);
    }

    @GetMapping("/token/{token}")
    @Operation(summary = "Get public session info by token (no auth)")
    public RemoteUploadPublicSessionDto getByToken(@PathVariable String token) {
        return sessionService.getPublicInfo(token);
    }

    @PostMapping(value = "/token/{token}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file using session token (no auth)")
    public RemoteUploadTokenResultDto uploadByToken(
            @PathVariable String token,
            @RequestPart("file") MultipartFile file
    ) {
        return sessionService.uploadByToken(token, file);
    }
}
