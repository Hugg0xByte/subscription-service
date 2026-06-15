package com.globo.subscription.application.usecase;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Use case responsible for retrieving the active subscription for a user.
 * First checks the cache, on cache miss queries the repository and populates the cache.
 */
@Service
@Transactional(readOnly = true)
public class GetActiveSubscriptionUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetActiveSubscriptionUseCase.class);

    private final SubscriptionCachePort subscriptionCachePort;
    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final Timer executionTimer;
    private final Counter executionCounter;

    public GetActiveSubscriptionUseCase(SubscriptionCachePort subscriptionCachePort,
                                        SubscriptionRepositoryPort subscriptionRepositoryPort,
                                        MeterRegistry meterRegistry) {
        this.subscriptionCachePort = subscriptionCachePort;
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.executionTimer = Timer.builder("subscription.usecase.duration")
                .tag("usecase", "get_active_subscription")
                .description("Duration of GetActiveSubscriptionUseCase execution")
                .register(meterRegistry);
        this.executionCounter = Counter.builder("subscription.usecase.count")
                .tag("usecase", "get_active_subscription")
                .description("Number of GetActiveSubscriptionUseCase executions")
                .register(meterRegistry);
    }

    /**
     * Retrieves the active subscription for the given user.
     * Checks cache first; on cache miss, queries the repository and populates the cache.
     *
     * @param userId the ID of the user
     * @return an Optional containing the active subscription, or empty if none exists
     */
    public Optional<Subscription> execute(UUID userId) {
        executionCounter.increment();
        return executionTimer.record(() -> {
            log.info("Getting active subscription for userId={}", userId);

            // 1. Check cache first
            Optional<Subscription> cached = subscriptionCachePort.getActiveSubscription(userId);
            if (cached.isPresent()) {
                return cached;
            }

            // 2. Cache miss: query the repository
            Optional<Subscription> fromRepository = subscriptionRepositoryPort.findActiveByUserId(userId);

            // 3. Populate cache on miss if subscription found
            fromRepository.ifPresent(subscription ->
                    subscriptionCachePort.putActiveSubscription(userId, subscription));

            return fromRepository;
        });
    }
}
