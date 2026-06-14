package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.springframework.stereotype.Component;

import com.globo.subscription.adapter.outbound.persistence.entity.UserJpaEntity;
import com.globo.subscription.domain.entity.User;

/**
 * Mapper that converts between User domain entity and UserJpaEntity.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Component
public class UserPersistenceMapper {

    /**
     * Converts a domain User entity to a JPA entity for persistence.
     *
     * @param domain the domain entity
     * @return the JPA entity
     */
    public UserJpaEntity toJpaEntity(User domain) {
        if (domain == null) {
            return null;
        }

        UserJpaEntity jpa = new UserJpaEntity();
        jpa.setId(domain.getId());
        jpa.setName(domain.getName());
        jpa.setEmail(domain.getEmail());
        jpa.setActive(domain.isActive());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setUpdatedAt(domain.getUpdatedAt());
        return jpa;
    }

    /**
     * Converts a JPA entity to a domain User entity.
     *
     * @param jpa the JPA entity
     * @return the domain entity
     */
    public User toDomainEntity(UserJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }

        return new User(
                jpa.getId(),
                jpa.getName(),
                jpa.getEmail(),
                jpa.isActive(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}
