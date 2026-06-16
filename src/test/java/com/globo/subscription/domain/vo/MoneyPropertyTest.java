package com.globo.subscription.domain.vo;

import java.math.BigDecimal;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.BigRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for Money value object non-negativity invariant.
 *
 * <p><b>Validates: Requirements 2.5</b></p>
 *
 * <p>Property 1: Money value object non-negativity invariant —
 * For any BigDecimal value, constructing a Money instance with a negative amount
 * SHALL throw IllegalArgumentException, and constructing with a non-negative amount
 * SHALL succeed and preserve the exact value.</p>
 */
class MoneyPropertyTest {

    private static final String VALID_CURRENCY = "BRL";

    @Property
    void negativeAmountsShouldThrowIllegalArgumentException(
            @ForAll @BigRange(min = "-999999999", max = "-0.01") BigDecimal negativeAmount) {
        assertThatThrownBy(() -> new Money(negativeAmount, VALID_CURRENCY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be non-negative");
    }

    @Property
    void nonNegativeAmountsShouldSucceedAndPreserveValue(
            @ForAll @BigRange(min = "0", max = "999999999") BigDecimal nonNegativeAmount) {
        Money money = new Money(nonNegativeAmount, VALID_CURRENCY);

        assertThat(money.amount()).isEqualByComparingTo(nonNegativeAmount);
        assertThat(money.currency()).isEqualTo(VALID_CURRENCY);
    }

    @Property
    void nullAmountShouldThrowIllegalArgumentException(@ForAll("validCurrencies") String currency) {
        assertThatThrownBy(() -> new Money(null, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be non-negative");
    }

    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of("BRL", "USD", "EUR");
    }
}
