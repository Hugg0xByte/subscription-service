package com.globo.subscription.adapter.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.globo.subscription.adapter.outbound.persistence.mapper.UserPersistenceMapper;
import com.globo.subscription.adapter.outbound.persistence.repository.UserJpaRepository;
import com.globo.subscription.application.port.UserRepositoryPort;
import com.globo.subscription.domain.entity.User;

/**
 * JPA adapter implementing UserRepositoryPort.
 * Bridges the domain layer with Spring Data JPA for user persistence.
 */
@Repository
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;
    private final UserPersistenceMapper mapper;

    public JpaUserRepositoryAdapter(UserJpaRepository userJpaRepository, UserPersistenceMapper mapper) {
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        var jpaEntity = mapper.toJpaEntity(user);
        var savedEntity = userJpaRepository.save(jpaEntity);
        return mapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id)
                .map(mapper::toDomainEntity);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(mapper::toDomainEntity);
    }
}
