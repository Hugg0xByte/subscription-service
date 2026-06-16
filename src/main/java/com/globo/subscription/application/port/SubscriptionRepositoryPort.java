package com.globo.subscription.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.globo.subscription.domain.entity.Subscription;

/**
 * Port interface for subscription persistence operations.
 * Implemented by outbound adapters in the persistence layer.
 */
public interface SubscriptionRepositoryPort {

    Subscription save(Subscription subscription);

    Optional<Subscription> findById(UUID id);

    Optional<Subscription> findActiveByUserId(UUID userId);

    List<Subscription> findSubscriptionsDueForRenewal(LocalDate date, int batchSize);

    List<Subscription> findSubscriptionsDueForCancellation(LocalDate date, int batchSize);

    boolean existsActiveForUser(UUID userId);
}
