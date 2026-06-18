package com.example.vault.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPlanDto(
        String reply,
        List<AiPlanActionDto> actions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPlanActionDto(
            String type,
            String name,
            @JsonProperty("newName") String newName,
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("documentId") UUID documentId,
            @JsonProperty("parentNodeId") UUID parentNodeId,
            @JsonProperty("targetParentNodeId") UUID targetParentNodeId,
            @JsonProperty("folderPath") List<String> folderPath,
            @JsonProperty("targetFolderPath") List<String> targetFolderPath
    ) {
    }
}
