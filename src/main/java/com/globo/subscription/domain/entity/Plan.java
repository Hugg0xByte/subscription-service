package com.globo.subscription.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.globo.subscription.domain.vo.Money;

/**
 * Domain entity representing a subscription plan.
 * Plans are persisted in the database and cached (not an enum).
 */
public class Plan {

    private final UUID id;
    private final String name;
    private final String displayName;
    private final Money monthlyPrice;
    private final boolean active;
    private final Instant createdAt;

    public Plan(UUID id, String name, String displayName, Money monthlyPrice, boolean active, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("Plan id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plan name must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Plan displayName must not be blank");
        }
        if (monthlyPrice == null) {
            throw new IllegalArgumentException("Plan monthlyPrice must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Plan createdAt must not be null");
        }

        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Money getMonthlyPrice() {
        return monthlyPrice;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
