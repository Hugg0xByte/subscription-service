package com.globo.subscription.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a payment attempt fails.
 */
public record PaymentFailed(
        UUID subscriptionId,
        int failedAttempts,
        String errorCode,
        String errorMessage,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "PAYMENT_FAILED";
    }
}
