package com.globo.subscription.application.port;

import com.globo.subscription.domain.event.DomainEvent;

/**
 * Port interface for publishing domain events.
 * Implemented by outbound adapters (e.g., LocalEventPublisherAdapter using outbox pattern).
 */
public interface EventPublisherPort {

    void publish(DomainEvent event);
}
