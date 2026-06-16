package com.globo.subscription.domain.vo;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * Immutable and validated at construction time.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
    }
}
