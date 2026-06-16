package com.globo.subscription.application.usecase;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.globo.subscription.application.dto.PaymentRequestMessage;
import com.globo.subscription.application.port.LockManagerPort;
import com.globo.subscription.application.port.MessagePublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewExpiredSubscriptionsUseCaseTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    @Mock
    private MessagePublisherPort messagePublisherPort;
    @Mock
    private SubscriptionCachePort subscriptionCachePort;
    @Mock
    private LockManagerPort lockManagerPort;

    private static final String PENDING_PAYMENT_TOPIC = "pendente-de-pagamento";

    private RenewExpiredSubscriptionsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenewExpiredSubscriptionsUseCase(
                subscriptionRepositoryPort, messagePublisherPort,
                subscriptionCachePort, lockManagerPort,
                PENDING_PAYMENT_TOPIC,
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should publish payment request messages and mark subscriptions as pending payment")
    void shouldPublishMessagesAndMarkAsPendingPayment() {
        // Given
        LocalDate today = LocalDate.now();
        int batchSize = 100;

        Subscription sub1 = createSubscription(SubscriptionStatus.ATIVA, today);
        Subscription sub2 = createSubscription(SubscriptionStatus.ATIVA, today);

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(subscriptionRepositoryPort.findSubscriptionsDueForRenewal(today, batchSize))
                .thenReturn(List.of(sub1, sub2));

        // When
        useCase.execute(today, batchSize);

        // Then
        verify(messagePublisherPort, times(2)).publish(eq(PENDING_PAYMENT_TOPIC), any(PaymentRequestMessage.class));
        verify(subscriptionRepositoryPort, times(2)).save(any(Subscription.class));
        verify(subscriptionCachePort, times(2)).evictActiveSubscription(any(UUID.class));
        verify(lockManagerPort).releaseLock(anyString());
    }

    @Test
    @DisplayName("Should skip execution when lock is not acquired")
    void shouldSkipWhenLockNotAcquired() {
        // Given
        LocalDate today = LocalDate.now();

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(false);

        // When
        useCase.execute(today, 100);

        // Then
        verify(subscriptionRepositoryPort, never()).findSubscriptionsDueForRenewal(any(), anyInt());
        verify(messagePublisherPort, never()).publish(anyString(), any());
        verify(subscriptionRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should continue processing batch when publish fails for individual subscription")
    void shouldContinueBatchOnPublishFailure() {
        // Given
        LocalDate today = LocalDate.now();
        int batchSize = 100;

        Subscription sub1 = createSubscription(SubscriptionStatus.ATIVA, today);
        Subscription sub2 = createSubscription(SubscriptionStatus.ATIVA, today);

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(subscriptionRepositoryPort.findSubscriptionsDueForRenewal(today, batchSize))
                .thenReturn(List.of(sub1, sub2));

        // First subscription: publish throws exception
        // Second subscription: publish succeeds
        doThrow(new RuntimeException("Pub/Sub unavailable"))
                .doNothing()
                .when(messagePublisherPort).publish(eq(PENDING_PAYMENT_TOPIC), any(PaymentRequestMessage.class));

        // When
        useCase.execute(today, batchSize);

        // Then - second subscription should still be processed
        verify(subscriptionRepositoryPort, times(1)).save(any(Subscription.class));
        verify(subscriptionCachePort, times(1)).evictActiveSubscription(any(UUID.class));
        verify(lockManagerPort).releaseLock(anyString());
    }

    @Test
    @DisplayName("Should always release lock even when exception occurs")
    void shouldAlwaysReleaseLock() {
        // Given
        LocalDate today = LocalDate.now();

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(subscriptionRepositoryPort.findSubscriptionsDueForRenewal(any(), anyInt()))
                .thenThrow(new RuntimeException("Database error"));

        // When / Then
        try {
            useCase.execute(today, 100);
        } catch (RuntimeException ignored) {
            // expected
        }

        verify(lockManagerPort).releaseLock(anyString());
    }

    private Subscription createSubscription(SubscriptionStatus status, LocalDate expirationDate) {
        return new Subscription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(new BigDecimal("39.90"), "BRL"),
                status,
                expirationDate.minusMonths(1),
                expirationDate,
                null,
                null,
                0,
                0L,
                Instant.now(),
                Instant.now()
        );
    }
}
