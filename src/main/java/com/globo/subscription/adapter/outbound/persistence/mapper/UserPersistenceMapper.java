package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.mapstruct.Mapper;

import com.globo.subscription.adapter.outbound.persistence.entity.UserJpaEntity;
import com.globo.subscription.domain.entity.User;

/**
 * MapStruct mapper that converts between User domain entity and UserJpaEntity.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {

    /**
     * Converts a domain User entity to a JPA entity for persistence.
     */
    UserJpaEntity toJpaEntity(User domain);

    /**
     * Converts a JPA entity to a domain User entity.
     * Uses a factory method because the domain entity has an all-args constructor with no setters.
     */
    default User toDomainEntity(UserJpaEntity jpa) {
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
