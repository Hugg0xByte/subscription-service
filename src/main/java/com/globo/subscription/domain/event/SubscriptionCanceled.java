package com.globo.subscription.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a subscription cancellation is requested.
 */
public record SubscriptionCanceled(
        UUID subscriptionId,
        Instant cancelRequestedAt,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "SUBSCRIPTION_CANCELED";
    }
}
