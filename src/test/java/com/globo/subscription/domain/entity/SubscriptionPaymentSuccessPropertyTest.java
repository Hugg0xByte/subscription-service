package com.globo.subscription.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.PaymentApproved;
import com.globo.subscription.domain.event.SubscriptionRenewed;
import com.globo.subscription.domain.vo.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for successful payment postconditions.
 *
 * <p><b>Validates: Requirements 2.10, 2.14</b></p>
 *
 * <p>Property 3: Successful payment postconditions —
 * For any Subscription with status ATIVA or PENDENTE_PAGAMENTO and any expirationDate
 * and failedAttempts value, after processing a successful payment:
 * (a) expirationDate SHALL equal the previous expirationDate plus exactly 1 month;
 * (b) failedAttempts SHALL equal 0;
 * (c) status SHALL equal ATIVA;
 * (d) a SubscriptionRenewed or PaymentApproved domain event SHALL be registered.</p>
 */
class SubscriptionPaymentSuccessPropertyTest {

    @Property
    void expirationDateAdvancesExactlyOneMonth(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        LocalDate originalExpiration = subscription.getExpirationDate();

        subscription.processSuccessfulPayment();

        assertThat(subscription.getExpirationDate())
                .isEqualTo(originalExpiration.plusMonths(1));
    }

    @Property
    void failedAttemptsResetsToZero(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        subscription.processSuccessfulPayment();

        assertThat(subscription.getFailedAttempts()).isZero();
    }

    @Property
    void statusBecomesAtiva(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        subscription.processSuccessfulPayment();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
    }

    @Property
    void domainEventsAreRegistered(
            @ForAll("renewalEligibleSubscriptions") Subscription subscription) {
        subscription.processSuccessfulPayment();

        assertThat(subscription.getDomainEvents()).isNotEmpty();

        boolean hasPaymentApproved = subscription.getDomainEvents().stream()
                .anyMatch(event -> event instanceof PaymentApproved);
        boolean hasSubscriptionRenewed = subscription.getDomainEvents().stream()
                .anyMatch(event -> event instanceof SubscriptionRenewed);

        assertThat(hasPaymentApproved)
                .as("A PaymentApproved domain event should be registered")
                .isTrue();
        assertThat(hasSubscriptionRenewed)
                .as("A SubscriptionRenewed domain event should be registered")
                .isTrue();
    }

    // --- Provider ---

    @Provide
    Arbitrary<Subscription> renewalEligibleSubscriptions() {
        return Arbitraries.of(SubscriptionStatus.ATIVA, SubscriptionStatus.PENDENTE_PAGAMENTO)
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
