package com.globo.subscription.adapter.inbound.rest.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new subscription.
 */
public record CreateSubscriptionRequest(
        @NotNull UUID userId,
        @NotNull UUID planId
) {}
