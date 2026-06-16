package com.globo.subscription.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a subscription is suspended due to reaching the maximum number of failed payment attempts.
 */
public record SubscriptionSuspended(
        UUID subscriptionId,
        int failedAttempts,
        Instant suspendedAt,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "SUBSCRIPTION_SUSPENDED";
    }
}
