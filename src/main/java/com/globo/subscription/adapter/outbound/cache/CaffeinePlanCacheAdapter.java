package com.globo.subscription.adapter.outbound.cache;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.globo.subscription.application.port.PlanCachePort;
import com.globo.subscription.domain.entity.Plan;

/**
 * Caffeine-based implementation of PlanCachePort.
 * Provides in-memory caching for active plans with configurable TTL and max size.
 * Uses a single well-known key since it stores one list of all active plans.
 */
@Component
public class CaffeinePlanCacheAdapter implements PlanCachePort {

    private static final Logger log = LoggerFactory.getLogger(CaffeinePlanCacheAdapter.class);
    private static final String ALL_ACTIVE_PLANS_KEY = "ALL_ACTIVE_PLANS";

    private final Cache<String, List<Plan>> cache;

    public CaffeinePlanCacheAdapter(
            @Value("${cache.plan.ttl-minutes:60}") long ttlMinutes,
            @Value("${cache.plan.max-size:100}") long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        log.info("Plan cache initialized with TTL={}min, maxSize={}", ttlMinutes, maxSize);
    }

    @Override
    public Optional<List<Plan>> getAllActivePlans() {
        List<Plan> plans = cache.getIfPresent(ALL_ACTIVE_PLANS_KEY);
        if (plans != null) {
            log.debug("Plan cache HIT — returning {} plans", plans.size());
            return Optional.of(plans);
        }
        log.debug("Plan cache MISS");
        return Optional.empty();
    }

    @Override
    public void putAllActivePlans(List<Plan> plans) {
        cache.put(ALL_ACTIVE_PLANS_KEY, plans);
        log.debug("Cached {} active plans", plans.size());
    }

    @Override
    public void evictAllPlans() {
        cache.invalidateAll();
        log.debug("Evicted all plan cache entries");
    }
}
