package com.globo.subscription.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.globo.subscription.adapter.outbound.persistence.mapper.SubscriptionPersistenceMapper;
import com.globo.subscription.adapter.outbound.persistence.repository.SubscriptionJpaRepository;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;

/**
 * JPA adapter implementing SubscriptionRepositoryPort.
 * Bridges the domain layer with Spring Data JPA for subscription persistence.
 * Uses FOR UPDATE SKIP LOCKED for concurrent renewal batch processing.
 */
@Repository
public class JpaSubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final SubscriptionPersistenceMapper mapper;

    public JpaSubscriptionRepositoryAdapter(SubscriptionJpaRepository subscriptionJpaRepository,
                                            SubscriptionPersistenceMapper mapper) {
        this.subscriptionJpaRepository = subscriptionJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Subscription save(Subscription subscription) {
        var jpaEntity = mapper.toJpaEntity(subscription);
        var savedEntity = subscriptionJpaRepository.save(jpaEntity);
        return mapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return subscriptionJpaRepository.findById(id)
                .map(mapper::toDomainEntity);
    }

    @Override
    public Optional<Subscription> findActiveByUserId(UUID userId) {
        return subscriptionJpaRepository.findActiveByUserId(userId)
                .map(mapper::toDomainEntity);
    }

    /**
     * Finds subscriptions due for renewal using a native query with FOR UPDATE SKIP LOCKED.
     * This ensures concurrent batch processors do not pick up the same subscriptions,
     * enabling safe parallel processing without conflicts.
     */
    @Override
    public List<Subscription> findSubscriptionsDueForRenewal(LocalDate date, int batchSize) {
        return subscriptionJpaRepository.findSubscriptionsDueForRenewal(date, batchSize).stream()
                .map(mapper::toDomainEntity)
                .toList();
    }

    @Override
    public boolean existsActiveForUser(UUID userId) {
        return subscriptionJpaRepository.existsActiveForUser(userId);
    }
}
