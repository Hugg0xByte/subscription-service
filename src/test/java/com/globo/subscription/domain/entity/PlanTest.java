package com.globo.subscription.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.globo.subscription.domain.vo.Money;

/**
 * Unit tests for Plan entity — creation, validation, monthlyPrice consistency.
 */
class PlanTest {

    private static final UUID VALID_ID = UUID.randomUUID();
    private static final String VALID_NAME = "PREMIUM";
    private static final String VALID_DISPLAY_NAME = "Premium";
    private static final Money VALID_PRICE = new Money(BigDecimal.valueOf(39.90), "BRL");
    private static final Instant NOW = Instant.now();

    @Test
    void shouldCreatePlanWithValidFields() {
        Plan plan = new Plan(VALID_ID, VALID_NAME, VALID_DISPLAY_NAME, VALID_PRICE, true, NOW);

        assertThat(plan.getId()).isEqualTo(VALID_ID);
        assertThat(plan.getName()).isEqualTo(VALID_NAME);
        assertThat(plan.getDisplayName()).isEqualTo(VALID_DISPLAY_NAME);
        assertThat(plan.getMonthlyPrice()).isEqualTo(VALID_PRICE);
        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldCreateInactivePlan() {
        Plan plan = new Plan(VALID_ID, VALID_NAME, VALID_DISPLAY_NAME, VALID_PRICE, false, NOW);

        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void shouldPreserveMonthlyPriceAmountAndCurrency() {
        Money price = new Money(new BigDecimal("19.90"), "BRL");
        Plan plan = new Plan(VALID_ID, "BASICO", "Básico", price, true, NOW);

        assertThat(plan.getMonthlyPrice().amount()).isEqualByComparingTo(new BigDecimal("19.90"));
        assertThat(plan.getMonthlyPrice().currency()).isEqualTo("BRL");
    }

    @Test
    void shouldCreatePlanWithZeroPrice() {
        Money freePrice = new Money(BigDecimal.ZERO, "BRL");
        Plan plan = new Plan(VALID_ID, "GRATIS", "Grátis", freePrice, true, NOW);

        assertThat(plan.getMonthlyPrice().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> new Plan(null, VALID_NAME, VALID_DISPLAY_NAME, VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new Plan(VALID_ID, null, VALID_DISPLAY_NAME, VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Plan(VALID_ID, "   ", VALID_DISPLAY_NAME, VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectEmptyName() {
        assertThatThrownBy(() -> new Plan(VALID_ID, "", VALID_DISPLAY_NAME, VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectNullDisplayName() {
        assertThatThrownBy(() -> new Plan(VALID_ID, VALID_NAME, null, VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName must not be blank");
    }

    @Test
    void shouldRejectBlankDisplayName() {
        assertThatThrownBy(() -> new Plan(VALID_ID, VALID_NAME, "   ", VALID_PRICE, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName must not be blank");
    }

    @Test
    void shouldRejectNullMonthlyPrice() {
        assertThatThrownBy(() -> new Plan(VALID_ID, VALID_NAME, VALID_DISPLAY_NAME, null, true, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthlyPrice must not be null");
    }

    @Test
    void shouldRejectNullCreatedAt() {
        assertThatThrownBy(() -> new Plan(VALID_ID, VALID_NAME, VALID_DISPLAY_NAME, VALID_PRICE, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt must not be null");
    }

    @Test
    void shouldCreateAllStandardPlans() {
        Plan basico = new Plan(UUID.randomUUID(), "BASICO", "Básico",
                new Money(new BigDecimal("19.90"), "BRL"), true, NOW);
        Plan premium = new Plan(UUID.randomUUID(), "PREMIUM", "Premium",
                new Money(new BigDecimal("39.90"), "BRL"), true, NOW);
        Plan familia = new Plan(UUID.randomUUID(), "FAMILIA", "Família",
                new Money(new BigDecimal("59.90"), "BRL"), true, NOW);

        assertThat(basico.getMonthlyPrice().amount()).isEqualByComparingTo(new BigDecimal("19.90"));
        assertThat(premium.getMonthlyPrice().amount()).isEqualByComparingTo(new BigDecimal("39.90"));
        assertThat(familia.getMonthlyPrice().amount()).isEqualByComparingTo(new BigDecimal("59.90"));
    }

    @Test
    void shouldConsistentlyReturnSameMonthlyPrice() {
        Plan plan = new Plan(VALID_ID, VALID_NAME, VALID_DISPLAY_NAME, VALID_PRICE, true, NOW);

        Money firstCall = plan.getMonthlyPrice();
        Money secondCall = plan.getMonthlyPrice();

        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(firstCall.amount()).isEqualByComparingTo(secondCall.amount());
        assertThat(firstCall.currency()).isEqualTo(secondCall.currency());
    }
}
