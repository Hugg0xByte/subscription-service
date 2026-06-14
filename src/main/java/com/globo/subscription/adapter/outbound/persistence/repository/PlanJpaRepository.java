package com.globo.subscription.adapter.outbound.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.globo.subscription.adapter.outbound.persistence.entity.PlanJpaEntity;

/**
 * Spring Data JPA repository for PlanJpaEntity.
 * Provides standard CRUD and custom queries for plan persistence.
 */
public interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, UUID> {

    Optional<PlanJpaEntity> findByName(String name);

    List<PlanJpaEntity> findByActiveTrue();
}
