package com.globo.subscription.adapter.outbound.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.globo.subscription.adapter.outbound.persistence.mapper.PlanPersistenceMapper;
import com.globo.subscription.adapter.outbound.persistence.repository.PlanJpaRepository;
import com.globo.subscription.application.port.PlanRepositoryPort;
import com.globo.subscription.domain.entity.Plan;

/**
 * JPA adapter implementing PlanRepositoryPort.
 * Bridges the domain layer with Spring Data JPA for plan persistence.
 */
@Repository
public class JpaPlanRepositoryAdapter implements PlanRepositoryPort {

    private final PlanJpaRepository planJpaRepository;
    private final PlanPersistenceMapper mapper;

    public JpaPlanRepositoryAdapter(PlanJpaRepository planJpaRepository, PlanPersistenceMapper mapper) {
        this.planJpaRepository = planJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return planJpaRepository.findById(id)
                .map(mapper::toDomainEntity);
    }

    @Override
    public Optional<Plan> findByName(String name) {
        return planJpaRepository.findByName(name)
                .map(mapper::toDomainEntity);
    }

    @Override
    public List<Plan> findAllActive() {
        return planJpaRepository.findByActiveTrue().stream()
                .map(mapper::toDomainEntity)
                .toList();
    }
}
