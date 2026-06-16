package com.globo.subscription.domain.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity representing a system user.
 * Pure domain object with no framework dependencies.
 */
public class User {

    private final UUID id;
    private final String name;
    private final String email;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(UUID id, String name, String email, boolean active, Instant createdAt, Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("User id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("User name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("User email must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("User createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("User updatedAt must not be null");
        }

        this.id = id;
        this.name = name;
        this.email = email;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
