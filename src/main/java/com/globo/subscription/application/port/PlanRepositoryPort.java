package com.globo.subscription.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.globo.subscription.domain.entity.Plan;

/**
 * Port interface for plan persistence operations.
 * Implemented by outbound adapters in the persistence layer.
 */
public interface PlanRepositoryPort {

    Optional<Plan> findById(UUID id);

    Optional<Plan> findByName(String name);

    List<Plan> findAllActive();
}
