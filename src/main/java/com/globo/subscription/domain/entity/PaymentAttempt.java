package com.globo.subscription.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.globo.subscription.domain.enums.PaymentAttemptStatus;
import com.globo.subscription.domain.vo.Money;

/**
 * Domain entity representing a payment attempt for a subscription renewal.
 * Pure domain object with no framework dependencies.
 */
public class PaymentAttempt {

    private final UUID id;
    private final UUID subscriptionId;
    private final Money amount;
    private final PaymentAttemptStatus status;
    private final int attemptNumber;
    private final String idempotencyKey;
    private final String providerTransactionId;  // nullable
    private final String errorCode;              // nullable
    private final String errorMessage;           // nullable
    private final Instant createdAt;
    private final Instant processedAt;           // nullable

    public PaymentAttempt(UUID id, UUID subscriptionId, Money amount, PaymentAttemptStatus status,
                          int attemptNumber, String idempotencyKey, String providerTransactionId,
                          String errorCode, String errorMessage, Instant createdAt, Instant processedAt) {
        if (id == null) {
            throw new IllegalArgumentException("PaymentAttempt id must not be null");
        }
        if (subscriptionId == null) {
            throw new IllegalArgumentException("PaymentAttempt subscriptionId must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("PaymentAttempt amount must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("PaymentAttempt status must not be null");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("PaymentAttempt attemptNumber must be at least 1");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("PaymentAttempt idempotencyKey must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("PaymentAttempt createdAt must not be null");
        }

        this.id = id;
        this.subscriptionId = subscriptionId;
        this.amount = amount;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.idempotencyKey = idempotencyKey;
        this.providerTransactionId = providerTransactionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
