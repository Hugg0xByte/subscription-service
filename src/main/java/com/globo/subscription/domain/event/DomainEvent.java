package com.globo.subscription.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface representing all domain events in the subscription system.
 * Each event captures a significant state change in the subscription lifecycle.
 */
public sealed interface DomainEvent permits
        SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled,
        SubscriptionSuspended, PaymentFailed, PaymentApproved {

    UUID subscriptionId();

    Instant occurredAt();

    String eventType();
}
