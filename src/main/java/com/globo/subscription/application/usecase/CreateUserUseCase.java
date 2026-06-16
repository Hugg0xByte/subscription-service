package com.globo.subscription.application.usecase;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.exception.EmailAlreadyExistsException;
import com.globo.subscription.application.port.UserRepositoryPort;
import com.globo.subscription.domain.entity.User;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Use case responsible for creating a new user.
 * Validates email uniqueness before persisting.
 */
@Service
@Transactional
public class CreateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateUserUseCase.class);

    private final UserRepositoryPort userRepositoryPort;
    private final Timer executionTimer;
    private final Counter executionCounter;

    public CreateUserUseCase(UserRepositoryPort userRepositoryPort,
                             MeterRegistry meterRegistry) {
        this.userRepositoryPort = userRepositoryPort;
        this.executionTimer = Timer.builder("subscription.usecase.duration")
                .tag("usecase", "create_user")
                .description("Duration of CreateUserUseCase execution")
                .register(meterRegistry);
        this.executionCounter = Counter.builder("subscription.usecase.count")
                .tag("usecase", "create_user")
                .description("Number of CreateUserUseCase executions")
                .register(meterRegistry);
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
        executionCounter.increment();
        return executionTimer.record(() -> {
            log.info("Creating user with email={}", email);

            userRepositoryPort.findByEmail(email).ifPresent(existing -> {
                throw new EmailAlreadyExistsException(email);
            });

            Instant now = Instant.now();
            User user = new User(UUID.randomUUID(), name, email, true, now, now);

            User persisted = userRepositoryPort.save(user);
            log.info("User created successfully: id={}", persisted.getId());
            return persisted;
        });
    }
}
