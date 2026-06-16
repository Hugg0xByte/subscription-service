package com.globo.subscription.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message published to the "pendente-de-pagamento" topic.
 * Contains all data needed for payment processing.
 */
public record PaymentRequestMessage(
    UUID messageId,
    UUID subscriptionId,
    UUID userId,
    UUID planId,
    BigDecimal amount,
    String currency,
    int attemptNumber,
    String idempotencyKey,
    Instant timestamp
) {
    public PaymentRequestMessage {
        if (messageId == null) throw new IllegalArgumentException("messageId must not be null");
        if (subscriptionId == null) throw new IllegalArgumentException("subscriptionId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (planId == null) throw new IllegalArgumentException("planId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
    }
}
