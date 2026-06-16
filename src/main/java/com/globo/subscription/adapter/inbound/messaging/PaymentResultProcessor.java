package com.globo.subscription.adapter.inbound.messaging;

import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.event.DomainEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles the transactional processing of payment results.
 * Extracted from PaymentResultListener to ensure @Transactional works correctly
 * (Spring proxies only intercept calls from outside the bean).
 */
@Service
public class PaymentResultProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultProcessor.class);

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final EventPublisherPort eventPublisherPort;
    private final Counter approvedCounter;
    private final Counter failedCounter;

    public PaymentResultProcessor(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                   SubscriptionCachePort subscriptionCachePort,
                                   EventPublisherPort eventPublisherPort,
                                   MeterRegistry meterRegistry) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.eventPublisherPort = eventPublisherPort;
        this.approvedCounter = Counter.builder("pubsub.message.consumed")
                .tag("outcome", "approved").register(meterRegistry);
        this.failedCounter = Counter.builder("pubsub.message.consumed")
                .tag("outcome", "failed").register(meterRegistry);
    }

    @Transactional
    public void process(PaymentResultMessage resultMessage) {
        Optional<Subscription> optSubscription = subscriptionRepositoryPort
                .findById(resultMessage.subscriptionId());

        if (optSubscription.isEmpty()) {
            log.warn("Subscription {} not found. Acknowledging message without processing.",
                    resultMessage.subscriptionId());
            return;
        }

        Subscription subscription = optSubscription.get();

        switch (resultMessage.status()) {
            case APPROVED -> {
                subscription.processSuccessfulPayment();
                approvedCounter.increment();
                log.info("Payment APPROVED for subscription {}. Status -> ATIVA, expiration -> {}",
                        subscription.getId(), subscription.getExpirationDate());
            }
            case FAILED -> {
                subscription.processFailedPayment();
                failedCounter.increment();
                log.info("Payment FAILED for subscription {}. Status -> {}, failedAttempts -> {}",
                        subscription.getId(), subscription.getStatus(), subscription.getFailedAttempts());
            }
        }

        subscriptionRepositoryPort.save(subscription);
        subscriptionCachePort.evictActiveSubscription(subscription.getUserId());

        for (DomainEvent event : subscription.getDomainEvents()) {
            eventPublisherPort.publish(event);
        }
        subscription.clearDomainEvents();
    }
}
