package com.globo.subscription.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.PaymentFailed;
import com.globo.subscription.domain.event.SubscriptionSuspended;
import com.globo.subscription.domain.vo.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for failed payment postconditions with suspension threshold.
 *
 * <p><b>Validates: Requirements 2.11, 2.14</b></p>
 *
 * <p>Property 4: Failed payment postconditions with suspension threshold —
 * For any Subscription with status ATIVA or PENDENTE_PAGAMENTO and failedAttempts in [0, 1, 2],
 * after processing a failed payment:
 * (a) failedAttempts SHALL equal the previous value + 1;
 * (b) IF the new failedAttempts equals 3, THEN status SHALL be SUSPENSA and suspendedAt SHALL be non-null;
 * (c) IF the new failedAttempts is less than 3, THEN status SHALL be PENDENTE_PAGAMENTO;
 * (d) the corresponding domain event (PaymentFailed or SubscriptionSuspended) SHALL be registered.</p>
 */
class SubscriptionFailedPaymentPropertyTest {

    @Property
    void failedAttemptsIncrementsOnFailedPayment(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        int originalFailedAttempts = subscription.getFailedAttempts();

        subscription.processFailedPayment();

        assertThat(subscription.getFailedAttempts())
                .isEqualTo(originalFailedAttempts + 1);
    }

    @Property
    void suspensionAtThreeFailures(
            @ForAll("subscriptionsAtTwoFailedAttempts") Subscription subscription) {
        subscription.processFailedPayment();

        assertThat(subscription.getFailedAttempts()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENSA);
        assertThat(subscription.getSuspendedAt()).isNotNull();
    }

    @Property
    void statusBecomesPendentePagamentoWhenBelowThreshold(
            @ForAll("subscriptionsBelowSuspensionThreshold") Subscription subscription) {
        subscription.processFailedPayment();

        assertThat(subscription.getFailedAttempts()).isLessThan(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
    }

    @Property
    void paymentFailedEventIsRegistered(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        subscription.processFailedPayment();

        boolean hasPaymentFailed = subscription.getDomainEvents().stream()
                .anyMatch(event -> event instanceof PaymentFailed);

        assertThat(hasPaymentFailed)
                .as("A PaymentFailed domain event should be registered")
                .isTrue();
    }

    @Property
    void subscriptionSuspendedEventIsRegisteredAtThreshold(
            @ForAll("subscriptionsAtTwoFailedAttempts") Subscription subscription) {
        subscription.processFailedPayment();

        boolean hasSubscriptionSuspended = subscription.getDomainEvents().stream()
                .anyMatch(event -> event instanceof SubscriptionSuspended);

        assertThat(hasSubscriptionSuspended)
                .as("A SubscriptionSuspended domain event should be registered when failedAttempts reaches 3")
                .isTrue();
    }

    // --- Providers ---

    @Provide
    Arbitrary<Subscription> renewalEligibleSubscriptions() {
        return Arbitraries.of(SubscriptionStatus.ATIVA, SubscriptionStatus.PENDENTE_PAGAMENTO)
                .flatMap(status -> Arbitraries.integers().between(0, 2)
                        .flatMap(failedAttempts -> subscriptionWithStatusAndFailedAttempts(status, failedAttempts)));
    }

    @Provide
    Arbitrary<Subscription> subscriptionsAtTwoFailedAttempts() {
        return Arbitraries.of(SubscriptionStatus.ATIVA, SubscriptionStatus.PENDENTE_PAGAMENTO)
                .flatMap(status -> subscriptionWithStatusAndFailedAttempts(status, 2));
    }

    @Provide
    Arbitrary<Subscription> subscriptionsBelowSuspensionThreshold() {
        return Arbitraries.of(SubscriptionStatus.ATIVA, SubscriptionStatus.PENDENTE_PAGAMENTO)
                .flatMap(status -> Arbitraries.integers().between(0, 1)
                        .flatMap(failedAttempts -> subscriptionWithStatusAndFailedAttempts(status, failedAttempts)));
    }

    private Arbitrary<Subscription> subscriptionWithStatusAndFailedAttempts(
            SubscriptionStatus status, int failedAttempts) {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<Money> moneys = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("9999.99"))
                .map(amount -> new Money(amount, "BRL"));
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 365)
                .map(offset -> LocalDate.of(2025, 1, 1).plusDays(offset));
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, uuids, moneys, dates, dates, instants)
                .flatAs((id, userId, planId, price, startDate, expirationDate, createdAt) ->
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
