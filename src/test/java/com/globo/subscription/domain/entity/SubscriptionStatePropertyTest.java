package com.globo.subscription.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for Subscription state transition enforcement.
 *
 * <p><b>Validates: Requirements 2.9, 2.15</b></p>
 *
 * <p>Property 2: Subscription state transition enforcement —
 * For any Subscription in any given status, only the transitions defined in the state machine
 * are allowed. Specifically: (a) for any Subscription with status CANCELADA, ALL state-changing
 * operations SHALL be rejected; (b) for any Subscription with status NOT in (ATIVA, PENDENTE_PAGAMENTO),
 * renewal operations SHALL be rejected; (c) for any Subscription in a valid precondition state,
 * the corresponding operation SHALL succeed.</p>
 */
class SubscriptionStatePropertyTest {

    @Property
    void canceladaSubscriptionRejectsAllOperations(
            @ForAll("canceladaSubscriptions") Subscription subscription) {
        assertThatThrownBy(subscription::processSuccessfulPayment)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(subscription::processFailedPayment)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(subscription::requestCancellation)
                .isInstanceOf(IllegalStateException.class);
    }

    @Property
    void nonRenewalEligibleSubscriptionsRejectPaymentOperations(
            @ForAll("nonRenewalEligibleSubscriptions") Subscription subscription) {
        assertThatThrownBy(subscription::processSuccessfulPayment)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(subscription::processFailedPayment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Property
    void renewalEligibleSubscriptionsAcceptSuccessfulPayment(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        assertThatNoException().isThrownBy(subscription::processSuccessfulPayment);
    }

    @Property
    void renewalEligibleSubscriptionsAcceptFailedPayment(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        assertThatNoException().isThrownBy(subscription::processFailedPayment);
    }

    @Property
    void cancellableSubscriptionsAcceptCancellation(
            @ForAll("cancellableSubscriptions") Subscription subscription) {
        assertThatNoException().isThrownBy(subscription::requestCancellation);
    }

    // --- Providers ---

    @Provide
    Arbitrary<Subscription> canceladaSubscriptions() {
        return subscriptionWithStatus(SubscriptionStatus.CANCELADA);
    }

    @Provide
    Arbitrary<Subscription> nonRenewalEligibleSubscriptions() {
        return Arbitraries.of(SubscriptionStatus.SUSPENSA, SubscriptionStatus.EXPIRADA, SubscriptionStatus.CANCELADA)
                .flatMap(this::subscriptionWithStatus);
    }

    @Provide
    Arbitrary<Subscription> renewalEligibleSubscriptions() {
        return Arbitraries.of(SubscriptionStatus.ATIVA, SubscriptionStatus.PENDENTE_PAGAMENTO)
                .flatMap(this::subscriptionWithStatus);
    }

    @Provide
    Arbitrary<Subscription> cancellableSubscriptions() {
        return Arbitraries.of(
                        SubscriptionStatus.ATIVA,
                        SubscriptionStatus.PENDENTE_PAGAMENTO,
                        SubscriptionStatus.SUSPENSA,
                        SubscriptionStatus.EXPIRADA)
                .flatMap(this::subscriptionWithStatus);
    }

    private Arbitrary<Subscription> subscriptionWithStatus(SubscriptionStatus status) {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<Money> moneys = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("9999.99"))
                .map(amount -> new Money(amount, "BRL"));
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 365)
                .map(offset -> LocalDate.of(2025, 1, 1).plusDays(offset));
        Arbitrary<Integer> failedAttemptValues = Arbitraries.integers().between(0, 2);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, uuids, moneys, dates, dates, failedAttemptValues, instants)
                .flatAs((id, userId, planId, price, startDate, expirationDate, failedAttempts, createdAt) ->
                        instants.map(updatedAt -> new Subscription(
                                id, userId, planId, price,
                                status,
                                startDate, expirationDate,
                                null, null,
                                failedAttempts,
                                0L,
                                createdAt, updatedAt)));
    }
}
