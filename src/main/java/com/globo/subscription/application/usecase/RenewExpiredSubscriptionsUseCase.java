package com.globo.subscription.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.LockManagerPort;
import com.globo.subscription.application.port.PaymentGatewayPort;
import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.PaymentAttempt;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.PaymentAttemptStatus;
import com.globo.subscription.domain.event.DomainEvent;

/**
 * Use case responsible for renewing expired subscriptions in batch.
 * Acquires a distributed lock, queries due subscriptions, processes payment for each,
 * updates entity state, persists changes, evicts cache, and publishes domain events.
 * Individual subscription failures do not abort the batch.
 */
@Service
@Transactional
public class RenewExpiredSubscriptionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RenewExpiredSubscriptionsUseCase.class);
    private static final String LOCK_NAME = "renewal-batch";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final EventPublisherPort eventPublisherPort;
    private final LockManagerPort lockManagerPort;

    public RenewExpiredSubscriptionsUseCase(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                            PaymentGatewayPort paymentGatewayPort,
                                            SubscriptionCachePort subscriptionCachePort,
                                            EventPublisherPort eventPublisherPort,
                                            LockManagerPort lockManagerPort) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.eventPublisherPort = eventPublisherPort;
        this.lockManagerPort = lockManagerPort;
    }

    /**
     * Executes the batch renewal process for subscriptions due for renewal.
     *
     * @param currentDate the reference date to determine which subscriptions are due
     * @param batchSize   the maximum number of subscriptions to process in this batch
     */
    public void execute(LocalDate currentDate, int batchSize) {
        boolean lockAcquired = false;
        try {
            lockAcquired = lockManagerPort.acquireLock(LOCK_NAME, LOCK_TTL);
            if (!lockAcquired) {
                log.warn("Could not acquire lock '{}'. Aborting renewal batch.", LOCK_NAME);
                return;
            }

            List<Subscription> dueSubscriptions = subscriptionRepositoryPort
                    .findSubscriptionsDueForRenewal(currentDate, batchSize);

            log.info("Renewal batch started. Found {} subscriptions due for renewal.", dueSubscriptions.size());

            int successes = 0;
            int failures = 0;

            for (Subscription subscription : dueSubscriptions) {
                try {
                    processSubscriptionRenewal(subscription);
                    successes++;
                } catch (Exception e) {
                    failures++;
                    log.error("Failed to renew subscription {}: {}", subscription.getId(), e.getMessage(), e);
                }
            }

            log.info("Renewal batch completed. Successes: {}, Failures: {}", successes, failures);
        } finally {
            if (lockAcquired) {
                lockManagerPort.releaseLock(LOCK_NAME);
            }
        }
    }

    private void processSubscriptionRenewal(Subscription subscription) {
        // Build idempotency key: subscription:{subscriptionId}:billing-cycle:{expirationDate}
        String idempotencyKey = String.format("subscription:%s:billing-cycle:%s",
                subscription.getId(), subscription.getExpirationDate());

        // Create PaymentAttempt
        PaymentAttempt paymentAttempt = new PaymentAttempt(
                UUID.randomUUID(),
                subscription.getId(),
                subscription.getPriceAtPurchase(),
                PaymentAttemptStatus.PROCESSING,
                subscription.getFailedAttempts() + 1,
                idempotencyKey,
                null,   // providerTransactionId
                null,   // errorCode
                null,   // errorMessage
                Instant.now(),
                null    // processedAt
        );

        // Process payment via gateway
        PaymentResult result = paymentGatewayPort.processPayment(paymentAttempt);

        // Update subscription state based on payment result
        switch (result) {
            case PaymentResult.Approved _ -> subscription.processSuccessfulPayment();
            case PaymentResult.Failed _ -> subscription.processFailedPayment();
        }

        // Persist updated subscription
        subscriptionRepositoryPort.save(subscription);

        // Evict cache for this user's active subscription
        subscriptionCachePort.evictActiveSubscription(subscription.getUserId());

        // Publish all domain events registered on the entity
        List<DomainEvent> events = subscription.getDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(event);
        }
        subscription.clearDomainEvents();
    }
}
