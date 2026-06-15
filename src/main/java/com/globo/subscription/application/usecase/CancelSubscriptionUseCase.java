package com.globo.subscription.application.usecase;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.exception.SubscriptionNotFoundException;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.event.DomainEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Use case responsible for canceling a subscription.
 * Finds the subscription, requests cancellation on the domain entity,
 * persists the change, evicts cache, and publishes domain events.
 */
@Service
@Transactional
public class CancelSubscriptionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelSubscriptionUseCase.class);

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final EventPublisherPort eventPublisherPort;
    private final Timer executionTimer;
    private final Counter executionCounter;

    public CancelSubscriptionUseCase(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                     SubscriptionCachePort subscriptionCachePort,
                                     EventPublisherPort eventPublisherPort,
                                     MeterRegistry meterRegistry) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.eventPublisherPort = eventPublisherPort;
        this.executionTimer = Timer.builder("subscription.usecase.duration")
                .tag("usecase", "cancel_subscription")
                .description("Duration of CancelSubscriptionUseCase execution")
                .register(meterRegistry);
        this.executionCounter = Counter.builder("subscription.usecase.count")
                .tag("usecase", "cancel_subscription")
                .description("Number of CancelSubscriptionUseCase executions")
                .register(meterRegistry);
    }

    /**
     * Cancels the subscription with the given ID.
     *
     * @param subscriptionId the ID of the subscription to cancel
     * @throws SubscriptionNotFoundException if no subscription exists with the given ID
     */
    public void execute(UUID subscriptionId) {
        executionCounter.increment();
        executionTimer.record(() -> {
            log.info("Cancelling subscription id={}", subscriptionId);

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

            log.info("Subscription cancelled successfully: id={}", subscriptionId);
        });
    }
}
