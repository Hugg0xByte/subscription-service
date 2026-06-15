package com.globo.subscription.adapter.outbound.event;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionEventJpaEntity;
import com.globo.subscription.adapter.outbound.persistence.repository.SubscriptionEventJpaRepository;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.domain.event.DomainEvent;

/**
 * Local event publisher adapter implementing the outbox pattern.
 * Serializes domain events to JSON and inserts them into the subscription_events table
 * with published_at=NULL, ready for future async publication.
 *
 * This adapter participates in the same transaction as the calling use case,
 * guaranteeing atomicity between state changes and event persistence.
 */
@Component
public class LocalEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(LocalEventPublisherAdapter.class);

    private final SubscriptionEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper;

    public LocalEventPublisherAdapter(SubscriptionEventJpaRepository eventRepository,
                                      ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        String payload = serializeToJson(event);

        var entity = new SubscriptionEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setSubscriptionId(event.subscriptionId());
        entity.setEventType(event.eventType());
        entity.setPayload(payload);
        entity.setOccurredAt(event.occurredAt());
        entity.setPublishedAt(null);

        eventRepository.save(entity);

        log.info("Published domain event to outbox: type={}, subscriptionId={}",
                event.eventType(), event.subscriptionId());
    }

    private String serializeToJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize domain event to JSON: " + event.eventType(), e);
        }
    }
}
