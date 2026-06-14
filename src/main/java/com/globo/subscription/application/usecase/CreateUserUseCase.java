package com.globo.subscription.application.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.exception.EmailAlreadyExistsException;
import com.globo.subscription.application.port.UserRepositoryPort;
import com.globo.subscription.domain.entity.User;

/**
 * Use case responsible for creating a new user.
 * Validates email uniqueness before persisting.
 */
@Service
@Transactional
public class CreateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    public CreateUserUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    /**
     * Creates a new user with the given name and email.
     *
     * @param name  the user's name
     * @param email the user's email address
     * @return the persisted User entity
     * @throws EmailAlreadyExistsException if a user with the given email already exists
     */
    public User execute(String name, String email) {
        userRepositoryPort.findByEmail(email).ifPresent(existing -> {
            throw new EmailAlreadyExistsException(email);
        });

        Instant now = Instant.now();
        User user = new User(UUID.randomUUID(), name, email, true, now, now);

        return userRepositoryPort.save(user);
    }
}
