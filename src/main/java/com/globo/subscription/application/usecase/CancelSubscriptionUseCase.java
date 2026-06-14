package com.globo.subscription.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.exception.SubscriptionNotFoundException;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.event.DomainEvent;

/**
 * Use case responsible for canceling a subscription.
 * Finds the subscription, requests cancellation on the domain entity,
 * persists the change, evicts cache, and publishes domain events.
 */
@Service
@Transactional
public class CancelSubscriptionUseCase {

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final EventPublisherPort eventPublisherPort;

    public CancelSubscriptionUseCase(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                     SubscriptionCachePort subscriptionCachePort,
                                     EventPublisherPort eventPublisherPort) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    /**
     * Cancels the subscription with the given ID.
     *
     * @param subscriptionId the ID of the subscription to cancel
     * @throws SubscriptionNotFoundException if no subscription exists with the given ID
     */
    public void execute(UUID subscriptionId) {
        // 1. Find subscription by ID
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        // 2. Request cancellation on domain entity
        subscription.requestCancellation();

        // 3. Persist updated subscription
        subscriptionRepositoryPort.save(subscription);

        // 4. Evict cache for this user's active subscription
        subscriptionCachePort.evictActiveSubscription(subscription.getUserId());

        // 5. Publish domain events
        List<DomainEvent> events = subscription.getDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(event);
        }
        subscription.clearDomainEvents();
    }
}
