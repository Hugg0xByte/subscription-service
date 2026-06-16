package com.globo.subscription.adapter.inbound.rest.dto;

import java.time.Instant;

/**
 * Standardized error response DTO for REST API error handling.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
) {}
