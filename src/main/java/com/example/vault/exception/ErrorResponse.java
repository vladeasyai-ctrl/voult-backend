package com.example.vault.exception;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> errors
) {
    @Builder
    public record FieldError(String field, String message) {
    }
}
