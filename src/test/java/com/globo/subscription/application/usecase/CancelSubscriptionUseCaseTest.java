package com.globo.subscription.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.globo.subscription.application.exception.SubscriptionNotFoundException;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.SubscriptionCanceled;
import com.globo.subscription.domain.vo.Money;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelSubscriptionUseCaseTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    @Mock
    private SubscriptionCachePort subscriptionCachePort;
    @Mock
    private EventPublisherPort eventPublisherPort;

    private CancelSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelSubscriptionUseCase(
                subscriptionRepositoryPort, subscriptionCachePort, eventPublisherPort,
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should cancel subscription successfully when it exists and is active")
    void shouldCancelSubscriptionSuccessfully() {
        // Given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.ATIVA);

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        // When
        useCase.execute(subscriptionId);

        // Then
        verify(subscriptionRepositoryPort).save(argThat(sub ->
                sub.getCancelRequestedAt() != null));
        verify(subscriptionCachePort).evictActiveSubscription(userId);
        verify(eventPublisherPort).publish(argThat(event ->
                event instanceof SubscriptionCanceled));
    }

    @Test
    @DisplayName("Should throw SubscriptionNotFoundException when subscription does not exist")
    void shouldThrowWhenSubscriptionNotFound() {
        // Given
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> useCase.execute(subscriptionId))
                .isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepositoryPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when subscription is already cancelled")
    void shouldThrowWhenSubscriptionAlreadyCancelled() {
        // Given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.CANCELADA);

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        // When / Then
        assertThatThrownBy(() -> useCase.execute(subscriptionId))
                .isInstanceOf(IllegalStateException.class);

        verify(subscriptionRepositoryPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    private Subscription createSubscription(UUID id, UUID userId, SubscriptionStatus status) {
        return new Subscription(
                id,
                userId,
                UUID.randomUUID(),
                new Money(new BigDecimal("39.90"), "BRL"),
                status,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(1),
                null,
                null,
                0,
                0L,
                Instant.now(),
                Instant.now()
        );
    }
}
