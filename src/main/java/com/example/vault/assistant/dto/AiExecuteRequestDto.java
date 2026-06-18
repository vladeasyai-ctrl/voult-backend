package com.example.vault.assistant.dto;

import com.example.vault.assistant.dto.AiPlanDto.AiPlanActionDto;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AiExecuteRequestDto(
        @NotNull List<AiPlanActionDto> actions
) {
}
