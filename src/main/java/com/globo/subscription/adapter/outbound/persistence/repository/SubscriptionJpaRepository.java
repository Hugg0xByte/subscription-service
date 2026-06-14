package com.globo.subscription.adapter.outbound.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionJpaEntity;

/**
 * Spring Data JPA repository for SubscriptionJpaEntity.
 * Provides standard CRUD and custom queries for subscription persistence.
 */
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    @Query("SELECT s FROM SubscriptionJpaEntity s WHERE s.userId = :userId AND s.status IN ('ATIVA', 'PENDENTE_PAGAMENTO')")
    Optional<SubscriptionJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SubscriptionJpaEntity s WHERE s.userId = :userId AND s.status IN ('ATIVA', 'PENDENTE_PAGAMENTO')")
    boolean existsActiveForUser(@Param("userId") UUID userId);

    /**
     * Finds subscriptions due for renewal using FOR UPDATE SKIP LOCKED to enable
     * concurrent batch processing without conflicts.
     * Selects subscriptions with status ATIVA or PENDENTE_PAGAMENTO whose expiration
     * date is on or before the given date.
     */
    @Query(value = "SELECT * FROM subscriptions WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO') " +
            "AND expiration_date <= :date ORDER BY expiration_date ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<SubscriptionJpaEntity> findSubscriptionsDueForRenewal(@Param("date") LocalDate date,
                                                               @Param("batchSize") int batchSize);
}
