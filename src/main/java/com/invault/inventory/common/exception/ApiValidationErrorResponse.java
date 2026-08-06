package com.invault.inventory.common.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard response returned when one or more request fields are invalid.
 */
public record ApiValidationErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors
) {
}

/*
 * ApiValidationErrorResponse keeps validation errors structured so Angular can
 * associate each backend message with the corresponding form field.
 */
