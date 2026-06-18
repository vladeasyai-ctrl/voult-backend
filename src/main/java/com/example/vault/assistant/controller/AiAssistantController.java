package com.example.vault.assistant.controller;

import com.example.vault.assistant.dto.AiChatRequestDto;
import com.example.vault.assistant.dto.AiChatResponseDto;
import com.example.vault.assistant.dto.AiExecuteRequestDto;
import com.example.vault.assistant.dto.AiPlanResponseDto;
import com.example.vault.assistant.dto.AiSettingsDto;
import com.example.vault.assistant.dto.UpdateAiSettingsRequest;
import com.example.vault.assistant.service.AiAssistantService;
import com.example.vault.assistant.service.AiSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Natural language vault management")
public class AiAssistantController {

    private final AiSettingsService aiSettingsService;
    private final AiAssistantService aiAssistantService;

    @GetMapping("/settings")
    @Operation(summary = "Get AI provider settings for current user")
    public AiSettingsDto getSettings() {
        return aiSettingsService.getSettings();
    }

    @PutMapping("/settings")
    @Operation(summary = "Update AI provider settings for current user")
    public AiSettingsDto updateSettings(@Valid @RequestBody UpdateAiSettingsRequest request) {
        return aiSettingsService.updateSettings(request);
    }

    @PostMapping("/plan")
    @Operation(summary = "Plan vault actions from a natural language command")
    public AiPlanResponseDto plan(@Valid @RequestBody AiChatRequestDto request) {
        return aiAssistantService.plan(request.message());
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute a previously planned set of vault actions")
    public AiChatResponseDto execute(@Valid @RequestBody AiExecuteRequestDto request) {
        return aiAssistantService.execute(request.actions());
    }
}
