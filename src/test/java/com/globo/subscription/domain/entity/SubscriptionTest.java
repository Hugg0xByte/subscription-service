package com.globo.subscription.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.PaymentApproved;
import com.globo.subscription.domain.event.PaymentFailed;
import com.globo.subscription.domain.event.SubscriptionCanceled;
import com.globo.subscription.domain.event.SubscriptionRenewed;
import com.globo.subscription.domain.event.SubscriptionSuspended;
import com.globo.subscription.domain.vo.Money;

class SubscriptionTest {

    private Subscription createActiveSubscription() {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(BigDecimal.valueOf(39.90), "BRL"),
                SubscriptionStatus.ATIVA,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 1),
                null,
                null,
                0,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    private Subscription createSubscriptionWithStatus(SubscriptionStatus status) {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(BigDecimal.valueOf(39.90), "BRL"),
                status,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 1),
                null,
                null,
                0,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    // --- processSuccessfulPayment tests ---

    @Test
    void processSuccessfulPayment_shouldAdvanceExpirationDateByOneMonth() {
        Subscription subscription = createActiveSubscription();
        LocalDate originalExpiration = subscription.getExpirationDate();

        subscription.processSuccessfulPayment();

        assertThat(subscription.getExpirationDate()).isEqualTo(originalExpiration.plusMonths(1));
    }

    @Test
    void processSuccessfulPayment_shouldResetFailedAttemptsToZero() {
        Subscription subscription = new Subscription(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(BigDecimal.valueOf(19.90), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                null, null, 2, 0L, Instant.now(), Instant.now()
        );

        subscription.processSuccessfulPayment();

        assertThat(subscription.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void processSuccessfulPayment_shouldSetStatusToAtiva() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.PENDENTE_PAGAMENTO);

        subscription.processSuccessfulPayment();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
    }

    @Test
    void processSuccessfulPayment_shouldRegisterPaymentApprovedAndSubscriptionRenewedEvents() {
        Subscription subscription = createActiveSubscription();

        subscription.processSuccessfulPayment();

        assertThat(subscription.getDomainEvents()).hasSize(2);
        assertThat(subscription.getDomainEvents().get(0)).isInstanceOf(PaymentApproved.class);
        assertThat(subscription.getDomainEvents().get(1)).isInstanceOf(SubscriptionRenewed.class);
    }

    @Test
    void processSuccessfulPayment_shouldRejectWhenStatusIsCancelada() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELADA);

        assertThatThrownBy(subscription::processSuccessfulPayment)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELADA");
    }

    @Test
    void processSuccessfulPayment_shouldRejectWhenStatusIsSuspensa() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.SUSPENSA);

        assertThatThrownBy(subscription::processSuccessfulPayment)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible for renewal");
    }

    // --- processFailedPayment tests ---

    @Test
    void processFailedPayment_shouldIncrementFailedAttempts() {
        Subscription subscription = createActiveSubscription();

        subscription.processFailedPayment();

        assertThat(subscription.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void processFailedPayment_shouldSetStatusToPendentePagamentoWhenUnderThreshold() {
        Subscription subscription = createActiveSubscription();

        subscription.processFailedPayment();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
    }

    @Test
    void processFailedPayment_shouldSuspendWhenReachingThreeFailures() {
        Subscription subscription = new Subscription(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(BigDecimal.valueOf(19.90), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                null, null, 2, 0L, Instant.now(), Instant.now()
        );

        subscription.processFailedPayment();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENSA);
        assertThat(subscription.getSuspendedAt()).isNotNull();
        assertThat(subscription.getFailedAttempts()).isEqualTo(3);
    }

    @Test
    void processFailedPayment_shouldRegisterPaymentFailedAndSubscriptionSuspendedEventsOnSuspension() {
        Subscription subscription = new Subscription(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(BigDecimal.valueOf(19.90), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                null, null, 2, 0L, Instant.now(), Instant.now()
        );

        subscription.processFailedPayment();

        assertThat(subscription.getDomainEvents()).hasSize(2);
        assertThat(subscription.getDomainEvents().get(0)).isInstanceOf(PaymentFailed.class);
        assertThat(subscription.getDomainEvents().get(1)).isInstanceOf(SubscriptionSuspended.class);
    }

    @Test
    void processFailedPayment_shouldRegisterOnlyPaymentFailedEventWhenNotSuspended() {
        Subscription subscription = createActiveSubscription();

        subscription.processFailedPayment();

        assertThat(subscription.getDomainEvents()).hasSize(1);
        assertThat(subscription.getDomainEvents().get(0)).isInstanceOf(PaymentFailed.class);
    }

    @Test
    void processFailedPayment_shouldRejectWhenStatusIsCancelada() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELADA);

        assertThatThrownBy(subscription::processFailedPayment)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELADA");
    }

    // --- requestCancellation tests ---

    @Test
    void requestCancellation_shouldSetCancelRequestedAtWithoutChangingStatus() {
        Subscription subscription = createActiveSubscription();
        SubscriptionStatus originalStatus = subscription.getStatus();

        subscription.requestCancellation();

        assertThat(subscription.getCancelRequestedAt()).isNotNull();
        assertThat(subscription.getStatus()).isEqualTo(originalStatus);
    }

    @Test
    void requestCancellation_shouldRegisterSubscriptionCanceledEvent() {
        Subscription subscription = createActiveSubscription();

        subscription.requestCancellation();

        assertThat(subscription.getDomainEvents()).hasSize(1);
        assertThat(subscription.getDomainEvents().get(0)).isInstanceOf(SubscriptionCanceled.class);
    }

    @Test
    void requestCancellation_shouldRejectWhenStatusIsCancelada() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELADA);

        assertThatThrownBy(subscription::requestCancellation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELADA");
    }

    // --- isEligibleForRenewal tests ---

    @Test
    void isEligibleForRenewal_shouldReturnTrueForAtiva() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.ATIVA);

        assertThat(subscription.isEligibleForRenewal()).isTrue();
    }

    @Test
    void isEligibleForRenewal_shouldReturnTrueForPendentePagamento() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.PENDENTE_PAGAMENTO);

        assertThat(subscription.isEligibleForRenewal()).isTrue();
    }

    @Test
    void isEligibleForRenewal_shouldReturnFalseForSuspensa() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.SUSPENSA);

        assertThat(subscription.isEligibleForRenewal()).isFalse();
    }

    @Test
    void isEligibleForRenewal_shouldReturnFalseForExpirada() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.EXPIRADA);

        assertThat(subscription.isEligibleForRenewal()).isFalse();
    }

    @Test
    void isEligibleForRenewal_shouldReturnFalseForCancelada() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELADA);

        assertThat(subscription.isEligibleForRenewal()).isFalse();
    }

    // --- Domain events management tests ---

    @Test
    void getDomainEvents_shouldReturnUnmodifiableList() {
        Subscription subscription = createActiveSubscription();
        subscription.processSuccessfulPayment();

        assertThatThrownBy(() -> subscription.getDomainEvents().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearDomainEvents_shouldRemoveAllRegisteredEvents() {
        Subscription subscription = createActiveSubscription();
        subscription.processSuccessfulPayment();

        assertThat(subscription.getDomainEvents()).isNotEmpty();

        subscription.clearDomainEvents();

        assertThat(subscription.getDomainEvents()).isEmpty();
    }

    // --- Constructor validation tests ---

    @Test
    void constructor_shouldRejectNullId() {
        assertThatThrownBy(() -> new Subscription(
                null, UUID.randomUUID(), UUID.randomUUID(),
                new Money(BigDecimal.TEN, "BRL"), SubscriptionStatus.ATIVA,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                null, null, 0, 0L, Instant.now(), Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shouldRejectNullStatus() {
        assertThatThrownBy(() -> new Subscription(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(BigDecimal.TEN, "BRL"), null,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                null, null, 0, 0L, Instant.now(), Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    // --- CANCELADA invariant enforcement across all operations ---

    @Test
    void canceladaSubscription_shouldRejectAllStateChangingOperations() {
        Subscription subscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELADA);

        assertThatThrownBy(subscription::processSuccessfulPayment)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(subscription::processFailedPayment)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(subscription::requestCancellation)
                .isInstanceOf(IllegalStateException.class);
    }
}
