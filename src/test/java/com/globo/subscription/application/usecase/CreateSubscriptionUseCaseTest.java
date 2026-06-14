package com.globo.subscription.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.globo.subscription.application.exception.ActiveSubscriptionExistsException;
import com.globo.subscription.application.exception.PlanNotFoundException;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.PlanCachePort;
import com.globo.subscription.application.port.PlanRepositoryPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.SubscriptionCreated;
import com.globo.subscription.domain.vo.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSubscriptionUseCaseTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    @Mock
    private PlanCachePort planCachePort;
    @Mock
    private PlanRepositoryPort planRepositoryPort;
    @Mock
    private SubscriptionCachePort subscriptionCachePort;
    @Mock
    private EventPublisherPort eventPublisherPort;

    private CreateSubscriptionUseCase useCase;

    private UUID userId;
    private UUID planId;
    private Plan plan;

    @BeforeEach
    void setUp() {
        useCase = new CreateSubscriptionUseCase(
                subscriptionRepositoryPort, planCachePort, planRepositoryPort,
                subscriptionCachePort, eventPublisherPort);

        userId = UUID.randomUUID();
        planId = UUID.randomUUID();
        plan = new Plan(planId, "PREMIUM", "Premium", new Money(new BigDecimal("39.90"), "BRL"), true, Instant.now());
    }

    @Test
    @DisplayName("Should create subscription successfully when no active subscription exists")
    void shouldCreateSubscriptionSuccessfully() {
        // Given
        when(subscriptionRepositoryPort.existsActiveForUser(userId)).thenReturn(false);
        when(planCachePort.getAllActivePlans()).thenReturn(Optional.of(List.of(plan)));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Subscription result = useCase.execute(userId, planId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPlanId()).isEqualTo(planId);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(result.getPriceAtPurchase()).isEqualTo(plan.getMonthlyPrice());
        assertThat(result.getFailedAttempts()).isZero();

        verify(subscriptionCachePort).evictActiveSubscription(userId);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(SubscriptionCreated.class);
    }

    @Test
    @DisplayName("Should throw ActiveSubscriptionExistsException when user already has active subscription")
    void shouldThrowWhenActiveSubscriptionExists() {
        // Given
        when(subscriptionRepositoryPort.existsActiveForUser(userId)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> useCase.execute(userId, planId))
                .isInstanceOf(ActiveSubscriptionExistsException.class);

        verify(subscriptionRepositoryPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    @DisplayName("Should retrieve plan from repository when cache miss and populate cache")
    void shouldFallbackToRepositoryOnCacheMiss() {
        // Given
        when(subscriptionRepositoryPort.existsActiveForUser(userId)).thenReturn(false);
        when(planCachePort.getAllActivePlans()).thenReturn(Optional.empty());
        when(planRepositoryPort.findAllActive()).thenReturn(List.of(plan));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Subscription result = useCase.execute(userId, planId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPlanId()).isEqualTo(planId);

        verify(planCachePort).putAllActivePlans(List.of(plan));
    }

    @Test
    @DisplayName("Should throw PlanNotFoundException when plan does not exist")
    void shouldThrowWhenPlanNotFound() {
        // Given
        UUID unknownPlanId = UUID.randomUUID();
        when(subscriptionRepositoryPort.existsActiveForUser(userId)).thenReturn(false);
        when(planCachePort.getAllActivePlans()).thenReturn(Optional.of(List.of(plan)));

        // When / Then
        assertThatThrownBy(() -> useCase.execute(userId, unknownPlanId))
                .isInstanceOf(PlanNotFoundException.class);

        verify(subscriptionRepositoryPort, never()).save(any());
    }
}
