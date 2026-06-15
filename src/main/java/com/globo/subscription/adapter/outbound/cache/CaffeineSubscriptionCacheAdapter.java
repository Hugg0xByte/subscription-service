package com.globo.subscription.adapter.outbound.cache;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.domain.entity.Subscription;

/**
 * Caffeine-based implementation of SubscriptionCachePort.
 * Provides in-memory caching for active subscriptions with configurable TTL and max size.
 */
@Component
public class CaffeineSubscriptionCacheAdapter implements SubscriptionCachePort {

    private static final Logger log = LoggerFactory.getLogger(CaffeineSubscriptionCacheAdapter.class);

    private final Cache<UUID, Subscription> cache;

    public CaffeineSubscriptionCacheAdapter(
            @Value("${cache.subscription.ttl-minutes:5}") long ttlMinutes,
            @Value("${cache.subscription.max-size:10000}") long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        log.info("Subscription cache initialized with TTL={}min, maxSize={}", ttlMinutes, maxSize);
    }

    @Override
    public Optional<Subscription> getActiveSubscription(UUID userId) {
        Subscription subscription = cache.getIfPresent(userId);
        if (subscription != null) {
            log.debug("Cache HIT for userId={}", userId);
            return Optional.of(subscription);
        }
        log.debug("Cache MISS for userId={}", userId);
        return Optional.empty();
    }

    @Override
    public void putActiveSubscription(UUID userId, Subscription subscription) {
        cache.put(userId, subscription);
        log.debug("Cached active subscription for userId={}", userId);
    }

    @Override
    public void evictActiveSubscription(UUID userId) {
        cache.invalidate(userId);
        log.debug("Evicted cache entry for userId={}", userId);
    }
}
