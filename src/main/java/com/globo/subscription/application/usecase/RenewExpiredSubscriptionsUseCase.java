package com.globo.subscription.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.dto.PaymentRequestMessage;
import com.globo.subscription.application.port.LockManagerPort;
import com.globo.subscription.application.port.MessagePublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Use case responsible for renewing expired subscriptions in batch.
 * Acquires a distributed lock, queries due subscriptions, publishes a payment request
 * message to the pending payment topic for each, marks the subscription as pending payment,
 * persists changes, and evicts cache.
 * Individual subscription failures do not abort the batch.
 */
@Service
@Transactional
public class RenewExpiredSubscriptionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RenewExpiredSubscriptionsUseCase.class);
    private static final String LOCK_NAME = "renewal-batch";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final MessagePublisherPort messagePublisherPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final LockManagerPort lockManagerPort;
    private final String pendingPaymentTopic;
    private final Timer renewalBatchTimer;
    private final Counter renewalBatchCounter;

    public RenewExpiredSubscriptionsUseCase(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                            MessagePublisherPort messagePublisherPort,
                                            SubscriptionCachePort subscriptionCachePort,
                                            LockManagerPort lockManagerPort,
                                            @Value("${pubsub.topic.pendente-pagamento}") String pendingPaymentTopic,
                                            MeterRegistry meterRegistry) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.messagePublisherPort = messagePublisherPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.lockManagerPort = lockManagerPort;
        this.pendingPaymentTopic = pendingPaymentTopic;
        this.renewalBatchTimer = Timer.builder("subscription.renewal.batch.duration")
                .description("Duration of renewal batch execution")
                .register(meterRegistry);
        this.renewalBatchCounter = Counter.builder("subscription.renewal.batch.count")
                .description("Number of renewal batch executions")
                .register(meterRegistry);
    }

    /**
     * Executes the batch renewal process for subscriptions due for renewal.
     *
     * @param currentDate the reference date to determine which subscriptions are due
     * @param batchSize   the maximum number of subscriptions to process in this batch
     */
    public void execute(LocalDate currentDate, int batchSize) {
        renewalBatchCounter.increment();
        renewalBatchTimer.record(() -> {
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
                    } catch (RuntimeException e) {
                        failures++;
                        log.error("Failed to publish payment request for subscription {}: {}",
                                subscription.getId(), e.getMessage(), e);
                    }
                }

                log.info("Renewal batch completed. Successes: {}, Failures: {}", successes, failures);

                // Process pending cancellations (cancel_requested_at IS NOT NULL and expired)
                List<Subscription> dueCancellations = subscriptionRepositoryPort
                        .findSubscriptionsDueForCancellation(currentDate, batchSize);

                int cancellations = 0;
                for (Subscription subscription : dueCancellations) {
                    try {
                        subscription.effectuateCancellation();
                        subscriptionRepositoryPort.save(subscription);
                        subscriptionCachePort.evictActiveSubscription(subscription.getUserId());
                        cancellations++;
                        log.info("Subscription {} cancelled (expiration reached with pending cancellation).",
                                subscription.getId());
                    } catch (RuntimeException e) {
                        log.error("Failed to effectuate cancellation for subscription {}: {}",
                                subscription.getId(), e.getMessage(), e);
                    }
                }

                if (cancellations > 0) {
                    log.info("Cancellations effectuated: {}", cancellations);
                }
            } finally {
                if (lockAcquired) {
                    lockManagerPort.releaseLock(LOCK_NAME);
                }
            }
        });
    }

    private void processSubscriptionRenewal(Subscription subscription) {
        String idempotencyKey = String.format("subscription:%s:billing-cycle:%s",
                subscription.getId(), subscription.getExpirationDate());

        PaymentRequestMessage message = new PaymentRequestMessage(
                UUID.randomUUID(),
                subscription.getId(),
                subscription.getUserId(),
                subscription.getPlanId(),
                subscription.getPriceAtPurchase().amount(),
                subscription.getPriceAtPurchase().currency(),
                subscription.getFailedAttempts() + 1,
                idempotencyKey,
                Instant.now()
        );

        messagePublisherPort.publish(pendingPaymentTopic, message);

        subscription.markAsPendingPayment();
        subscriptionRepositoryPort.save(subscription);
        subscriptionCachePort.evictActiveSubscription(subscription.getUserId());
    }
}
