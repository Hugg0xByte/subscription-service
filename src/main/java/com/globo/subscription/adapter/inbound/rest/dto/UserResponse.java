package com.globo.subscription.adapter.inbound.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for user data.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        boolean active,
        Instant createdAt
) {}
