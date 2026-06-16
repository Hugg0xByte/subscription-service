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

import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveSubscriptionUseCaseTest {

    @Mock
    private SubscriptionCachePort subscriptionCachePort;
    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;

    private GetActiveSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetActiveSubscriptionUseCase(subscriptionCachePort, subscriptionRepositoryPort,
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should return subscription from cache when cache hit")
    void shouldReturnFromCacheOnHit() {
        // Given
        UUID userId = UUID.randomUUID();
        Subscription cached = createSubscription(userId);

        when(subscriptionCachePort.getActiveSubscription(userId)).thenReturn(Optional.of(cached));

        // When
        Optional<Subscription> result = useCase.execute(userId);

        // Then
        assertThat(result).isPresent().contains(cached);
        verify(subscriptionRepositoryPort, never()).findActiveByUserId(userId);
    }

    @Test
    @DisplayName("Should query repository and populate cache on cache miss")
    void shouldQueryRepositoryOnCacheMiss() {
        // Given
        UUID userId = UUID.randomUUID();
        Subscription fromDb = createSubscription(userId);

        when(subscriptionCachePort.getActiveSubscription(userId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.findActiveByUserId(userId)).thenReturn(Optional.of(fromDb));

        // When
        Optional<Subscription> result = useCase.execute(userId);

        // Then
        assertThat(result).isPresent().contains(fromDb);
        verify(subscriptionCachePort).putActiveSubscription(userId, fromDb);
    }

    @Test
    @DisplayName("Should return empty when no active subscription exists (cache miss + repository miss)")
    void shouldReturnEmptyWhenNotFound() {
        // Given
        UUID userId = UUID.randomUUID();

        when(subscriptionCachePort.getActiveSubscription(userId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.findActiveByUserId(userId)).thenReturn(Optional.empty());

        // When
        Optional<Subscription> result = useCase.execute(userId);

        // Then
        assertThat(result).isEmpty();
        verify(subscriptionCachePort, never()).putActiveSubscription(any(UUID.class), any(Subscription.class));
    }

    private Subscription createSubscription(UUID userId) {
        return new Subscription(
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                new Money(new BigDecimal("39.90"), "BRL"),
                SubscriptionStatus.ATIVA,
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
