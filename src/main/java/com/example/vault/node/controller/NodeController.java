package com.example.vault.node.controller;

import com.example.vault.node.dto.CreateNodeRequest;
import com.example.vault.node.dto.MoveNodeRequest;
import com.example.vault.node.dto.NodeDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.node.dto.UpdateNodeRequest;
import com.example.vault.node.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
@Tag(name = "Nodes", description = "Tree structure management")
public class NodeController {

    private final NodeService nodeService;

    @GetMapping("/tree")
    @Operation(summary = "Get full node tree")
    public List<TreeNodeDto> getTree() {
        return nodeService.getTree();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a folder node")
    public NodeDto create(@Valid @RequestBody CreateNodeRequest request) {
        return nodeService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update node name")
    public NodeDto update(@PathVariable UUID id, @Valid @RequestBody UpdateNodeRequest request) {
        return nodeService.update(id, request);
    }

    @PatchMapping("/{id}/move")
    @Operation(summary = "Move node to another folder")
    public NodeDto move(@PathVariable UUID id, @Valid @RequestBody MoveNodeRequest request) {
        return nodeService.move(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a folder node")
    public void delete(@PathVariable UUID id) {
        nodeService.delete(id);
    }
}
