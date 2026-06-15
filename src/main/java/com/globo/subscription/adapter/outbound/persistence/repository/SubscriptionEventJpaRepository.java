package com.globo.subscription.adapter.outbound.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionEventJpaEntity;

/**
 * Spring Data JPA repository for subscription events (outbox table).
 */
public interface SubscriptionEventJpaRepository extends JpaRepository<SubscriptionEventJpaEntity, UUID> {
}
