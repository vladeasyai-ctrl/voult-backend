package com.example.vault.importing.controller;

import com.example.vault.document.dto.DocumentDto;
import com.example.vault.importing.dto.ConfirmImportRequest;
import com.example.vault.importing.dto.ImportSessionDto;
import com.example.vault.importing.service.ImportSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
@Tag(name = "Imports", description = "AI-assisted document import staging")
public class ImportController {

    private final ImportSessionService importSessionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file for AI-assisted import")
    public ImportSessionDto create(@RequestPart("file") MultipartFile file) {
        return importSessionService.create(file);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import session status and proposal")
    public ImportSessionDto get(@PathVariable UUID id) {
        return importSessionService.get(id);
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Analyze uploaded file with AI and build a placement proposal")
    public ImportSessionDto analyze(@PathVariable UUID id) {
        return importSessionService.startAnalysis(id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm AI proposal and create document in the vault tree")
    public DocumentDto confirm(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmImportRequest request
    ) {
        return importSessionService.confirm(id, request);
    }

    @PostMapping("/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Discard staged import and remove uploaded file")
    public void discard(@PathVariable UUID id) {
        importSessionService.discard(id);
    }
}
