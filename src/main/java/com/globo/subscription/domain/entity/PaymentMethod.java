package com.globo.subscription.domain.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity representing a user's payment method.
 * Pure domain object with no framework dependencies.
 */
public class PaymentMethod {

    private final UUID id;
    private final UUID userId;
    private final String provider;
    private final String token;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PaymentMethod(UUID id, UUID userId, String provider, String token,
                         boolean active, Instant createdAt, Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("PaymentMethod id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("PaymentMethod userId must not be null");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("PaymentMethod provider must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("PaymentMethod token must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("PaymentMethod createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("PaymentMethod updatedAt must not be null");
        }

        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.token = token;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getToken() {
        return token;
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
