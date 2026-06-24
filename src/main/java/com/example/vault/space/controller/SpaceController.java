package com.example.vault.space.controller;

import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.space.dto.CreateSpaceRequest;
import com.example.vault.space.dto.SpaceDto;
import com.example.vault.space.dto.UpdateSpaceRequest;
import com.example.vault.space.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
@Tag(name = "Spaces", description = "Top-level vault spaces and their branch trees")
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    @Operation(summary = "List all spaces")
    public List<SpaceDto> list() {
        return spaceService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get space by id")
    public SpaceDto get(@PathVariable UUID id) {
        return spaceService.get(id);
    }

    @GetMapping("/{id}/tree")
    @Operation(summary = "Get root branches and nested tree for a space")
    public List<TreeNodeDto> getTree(@PathVariable UUID id) {
        return spaceService.getTree(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a space")
    public SpaceDto create(@Valid @RequestBody CreateSpaceRequest request) {
        return spaceService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update space metadata")
    public SpaceDto update(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest request) {
        return spaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a space and all its nodes")
    public void delete(@PathVariable UUID id) {
        spaceService.delete(id);
    }
}
