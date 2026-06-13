package com.example.vault.document.controller;

import com.example.vault.document.dto.CreateDocumentRequest;
import com.example.vault.document.dto.DocumentDto;
import com.example.vault.document.service.DocumentService;
import com.example.vault.version.dto.CreateVersionRequest;
import com.example.vault.version.dto.DocumentVersionDto;
import com.example.vault.version.service.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document management and versioning")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionService versionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new document")
    public DocumentDto create(@Valid @RequestBody CreateDocumentRequest request) {
        return documentService.create(request);
    }

    @GetMapping("/search")
    @Operation(summary = "Search documents by title")
    public List<DocumentDto> search(@RequestParam(required = false) String q) {
        return documentService.search(q);
    }

    @GetMapping("/by-node/{nodeId}")
    @Operation(summary = "Get document by node ID")
    public DocumentDto getByNodeId(@PathVariable UUID nodeId) {
        return documentService.getByNodeId(nodeId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public DocumentDto getById(@PathVariable UUID id) {
        return documentService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete document")
    public void delete(@PathVariable UUID id) {
        documentService.delete(id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new document version")
    public DocumentVersionDto createVersion(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVersionRequest request
    ) {
        return versionService.createVersion(id, request);
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List document versions")
    public List<DocumentVersionDto> getVersions(@PathVariable UUID id) {
        return versionService.getVersions(id);
    }
}
