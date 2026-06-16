package com.globo.subscription.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a payment is successfully approved.
 */
public record PaymentApproved(
        UUID subscriptionId,
        String providerTransactionId,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "PAYMENT_APPROVED";
    }
}
