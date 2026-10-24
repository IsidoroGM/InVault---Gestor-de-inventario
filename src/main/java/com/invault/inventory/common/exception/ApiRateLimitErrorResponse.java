package com.invault.inventory.common.exception;

import java.time.LocalDateTime;

public record ApiRateLimitErrorResponse(int status, String error, String message, String path,
        LocalDateTime timestamp, long retryAfterSeconds) {}
