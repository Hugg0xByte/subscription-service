package com.globo.subscription.application.port;

import java.util.List;
import java.util.Optional;

import com.globo.subscription.domain.entity.Plan;

/**
 * Port interface for plan caching operations.
 * Implemented by outbound adapters (e.g., CaffeinePlanCacheAdapter).
 */
public interface PlanCachePort {

    Optional<List<Plan>> getAllActivePlans();

    void putAllActivePlans(List<Plan> plans);

    void evictAllPlans();
}
