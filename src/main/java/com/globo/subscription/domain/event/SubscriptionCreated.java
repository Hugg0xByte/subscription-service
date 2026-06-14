package com.globo.subscription.domain.event;

import com.globo.subscription.domain.vo.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted when a new subscription is created.
 */
public record SubscriptionCreated(
        UUID subscriptionId,
        UUID userId,
        UUID planId,
        Money priceAtPurchase,
        LocalDate startDate,
        LocalDate expirationDate,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "SUBSCRIPTION_CREATED";
    }
}
