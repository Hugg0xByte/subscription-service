package com.globo.subscription.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for subscription data.
 * Exposes plan name and price without leaking internal domain state
 * (no version field, no internal IDs of related entities).
 */
public record SubscriptionResponse(
        UUID id,
        UUID userId,
        String planName,
        BigDecimal priceAtPurchase,
        String currency,
        String status,
        LocalDate startDate,
        LocalDate expirationDate,
        Instant createdAt
) {}
