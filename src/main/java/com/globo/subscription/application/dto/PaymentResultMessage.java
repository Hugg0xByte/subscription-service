package com.globo.subscription.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Message published to the "pagamento-processado" topic.
 * Contains the result of payment processing.
 */
public record PaymentResultMessage(
    UUID subscriptionId,
    UUID userId,
    PaymentStatus status,
    String providerTransactionId,   // non-null when APPROVED
    String errorCode,               // non-null when FAILED
    String errorMessage,            // non-null when FAILED
    String idempotencyKey,
    Instant processedAt
) {
    public enum PaymentStatus {
        APPROVED, FAILED
    }

    public PaymentResultMessage {
        if (subscriptionId == null) throw new IllegalArgumentException("subscriptionId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        if (processedAt == null) throw new IllegalArgumentException("processedAt must not be null");
    }
}
