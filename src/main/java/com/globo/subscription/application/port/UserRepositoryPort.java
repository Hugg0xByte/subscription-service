package com.globo.subscription.application.port;

import java.util.Optional;
import java.util.UUID;

import com.globo.subscription.domain.entity.User;

/**
 * Port interface for user persistence operations.
 * Implemented by outbound adapters in the persistence layer.
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);
}
