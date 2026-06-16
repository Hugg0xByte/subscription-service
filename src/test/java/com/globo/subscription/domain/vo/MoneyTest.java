package com.globo.subscription.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Money value object — edge cases (zero, boundary, currency validation).
 */
class MoneyTest {

    @Test
    void shouldCreateMoneyWithZeroAmount() {
        Money money = new Money(BigDecimal.ZERO, "BRL");

        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void shouldCreateMoneyWithPositiveAmount() {
        Money money = new Money(BigDecimal.valueOf(19.90), "BRL");

        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.valueOf(19.90));
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void shouldCreateMoneyWithLargeAmount() {
        BigDecimal largeAmount = new BigDecimal("999999999.99");
        Money money = new Money(largeAmount, "USD");

        assertThat(money.amount()).isEqualByComparingTo(largeAmount);
    }

    @Test
    void shouldCreateMoneyWithSmallestPositiveAmount() {
        BigDecimal smallAmount = new BigDecimal("0.01");
        Money money = new Money(smallAmount, "EUR");

        assertThat(money.amount()).isEqualByComparingTo(smallAmount);
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.valueOf(-0.01), "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be non-negative");
    }

    @Test
    void shouldRejectLargeNegativeAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.valueOf(-1000), "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be non-negative");
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be non-negative");
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    void shouldRejectEmptyCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    void shouldRejectBlankCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must not be blank");
    }

    @Test
    void shouldPreserveExactDecimalPrecision() {
        BigDecimal precise = new BigDecimal("39.90");
        Money money = new Money(precise, "BRL");

        assertThat(money.amount()).isEqualByComparingTo(precise);
        assertThat(money.amount().scale()).isEqualTo(precise.scale());
    }

    @Test
    void shouldSupportEqualityForSameValues() {
        Money money1 = new Money(BigDecimal.valueOf(19.90), "BRL");
        Money money2 = new Money(BigDecimal.valueOf(19.90), "BRL");

        assertThat(money1).isEqualTo(money2);
    }

    @Test
    void shouldNotBeEqualForDifferentCurrencies() {
        Money brl = new Money(BigDecimal.TEN, "BRL");
        Money usd = new Money(BigDecimal.TEN, "USD");

        assertThat(brl).isNotEqualTo(usd);
    }

    @Test
    void shouldNotBeEqualForDifferentAmounts() {
        Money ten = new Money(BigDecimal.TEN, "BRL");
        Money twenty = new Money(BigDecimal.valueOf(20), "BRL");

        assertThat(ten).isNotEqualTo(twenty);
    }
}
