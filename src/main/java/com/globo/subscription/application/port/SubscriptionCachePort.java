package com.globo.subscription.application.port;

import java.util.Optional;
import java.util.UUID;

import com.globo.subscription.domain.entity.Subscription;

/**
 * Port interface for subscription caching operations.
 * Implemented by outbound adapters (e.g., CaffeineSubscriptionCacheAdapter).
 */
public interface SubscriptionCachePort {

    Optional<Subscription> getActiveSubscription(UUID userId);

    void putActiveSubscription(UUID userId, Subscription subscription);

    void evictActiveSubscription(UUID userId);
}
