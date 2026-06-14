package com.globo.subscription.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.SubscriptionCanceled;
import com.globo.subscription.domain.vo.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for cancellation request preserving status.
 *
 * <p><b>Validates: Requirements 2.12</b></p>
 *
 * <p>Property 5: Cancellation request preserves status —
 * For any Subscription in a cancellable state (ATIVA, PENDENTE_PAGAMENTO, SUSPENSA, EXPIRADA),
 * calling requestCancellation() SHALL set cancelRequestedAt to a non-null value AND the status
 * SHALL remain unchanged from its value before the operation. A SubscriptionCanceled domain event
 * SHALL be registered.</p>
 */
class SubscriptionCancellationPropertyTest {

    @Property
    void cancellationRequestPreservesStatus(
            @ForAll("cancellableSubscriptions") Subscription subscription) {
        SubscriptionStatus originalStatus = subscription.getStatus();

        subscription.requestCancellation();

        assertThat(subscription.getStatus()).isEqualTo(originalStatus);
    }

    @Property
    void cancellationRequestSetsCancelRequestedAt(
            @ForAll("cancellableSubscriptions") Subscription subscription) {
        subscription.requestCancellation();

        assertThat(subscription.getCancelRequestedAt()).isNotNull();
    }

    @Property
    void cancellationRequestRegistersSubscriptionCanceledEvent(
            @ForAll("cancellableSubscriptions") Subscription subscription) {
        subscription.requestCancellation();

        assertThat(subscription.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(SubscriptionCanceled.class);
    }

    // --- Providers ---

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
