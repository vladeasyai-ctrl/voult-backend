package com.example.vault.assistant.dto;

import com.example.vault.assistant.dto.AiPlanDto.AiPlanActionDto;

import java.util.List;

public record AiPlanResponseDto(
        String reply,
        List<AiPlanActionDto> actions
) {
}
