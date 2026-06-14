package com.globo.subscription.adapter.outbound.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.globo.subscription.adapter.outbound.persistence.entity.UserJpaEntity;

/**
 * Spring Data JPA repository for UserJpaEntity.
 * Provides standard CRUD and custom queries for user persistence.
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);
}
