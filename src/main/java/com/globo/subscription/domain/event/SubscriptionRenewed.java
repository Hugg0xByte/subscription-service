package com.globo.subscription.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted when a subscription is successfully renewed.
 */
public record SubscriptionRenewed(
        UUID subscriptionId,
        LocalDate newExpirationDate,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "SUBSCRIPTION_RENEWED";
    }
}
