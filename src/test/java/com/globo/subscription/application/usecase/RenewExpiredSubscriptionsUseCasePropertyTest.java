package com.globo.subscription.application.usecase;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import org.mockito.ArgumentCaptor;

import com.globo.subscription.application.port.LockManagerPort;
import com.globo.subscription.application.port.MessagePublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for the refactored RenewExpiredSubscriptionsUseCase.
 *
 * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b></p>
 *
 * <p>Property 8: Refactored use case publishes to queue and updates status —
 * For any list of 1-10 eligible subscriptions with ATIVA status,
 * the use case SHALL publish exactly one message per subscription to the pending payment topic,
 * update each subscription's status to PENDENTE_PAGAMENTO, save each subscription,
 * and SHALL NOT reference PaymentGatewayPort.</p>
 */
class RenewExpiredSubscriptionsUseCasePropertyTest {

    private static final String PENDING_TOPIC = "pendente-de-pagamento";

    @Property(tries = 50)
    void shouldPublishOneMessagePerSubscriptionAndUpdateStatus(
            @ForAll("subscriptionLists") List<Subscription> subscriptions) {

        // Arrange
        MessagePublisherPort messagePublisherPort = mock(MessagePublisherPort.class);
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        LockManagerPort lockManagerPort = mock(LockManagerPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(subscriptionRepositoryPort.findSubscriptionsDueForRenewal(any(LocalDate.class), any(Integer.class)))
                .thenReturn(subscriptions);
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RenewExpiredSubscriptionsUseCase useCase = new RenewExpiredSubscriptionsUseCase(
                subscriptionRepositoryPort,
                messagePublisherPort,
                subscriptionCachePort,
                lockManagerPort,
                PENDING_TOPIC,
                meterRegistry
        );

        int expectedCount = subscriptions.size();

        // Act
        useCase.execute(LocalDate.now(), 100);

        // Assert - exactly one publish per subscription
        verify(messagePublisherPort, times(expectedCount)).publish(eq(PENDING_TOPIC), any());

        // Assert - each subscription's status is PENDENTE_PAGAMENTO after execution
        ArgumentCaptor<Subscription> savedCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepositoryPort, times(expectedCount)).save(savedCaptor.capture());

        List<Subscription> savedSubscriptions = savedCaptor.getAllValues();
        assertThat(savedSubscriptions).hasSize(expectedCount);
        for (Subscription saved : savedSubscriptions) {
            assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
        }
    }

    @Property(tries = 50)
    void shouldNotReferencePaymentGatewayPort(
            @ForAll("subscriptionLists") List<Subscription> subscriptions) {

        // Arrange
        MessagePublisherPort messagePublisherPort = mock(MessagePublisherPort.class);
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        LockManagerPort lockManagerPort = mock(LockManagerPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(lockManagerPort.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(subscriptionRepositoryPort.findSubscriptionsDueForRenewal(any(LocalDate.class), any(Integer.class)))
                .thenReturn(subscriptions);
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RenewExpiredSubscriptionsUseCase useCase = new RenewExpiredSubscriptionsUseCase(
                subscriptionRepositoryPort,
                messagePublisherPort,
                subscriptionCachePort,
                lockManagerPort,
                PENDING_TOPIC,
                meterRegistry
        );

        // Act
        useCase.execute(LocalDate.now(), 100);

        // Assert - the use case constructor does not accept PaymentGatewayPort
        // Verified structurally: the constructor only takes MessagePublisherPort,
        // SubscriptionRepositoryPort, SubscriptionCachePort, LockManagerPort, String, MeterRegistry.
        // No PaymentGatewayPort is present. This test confirms the use case completes
        // without any payment gateway interaction, publishing to queue instead.
        verify(messagePublisherPort, times(subscriptions.size())).publish(eq(PENDING_TOPIC), any());
    }

    @Provide
    Arbitrary<List<Subscription>> subscriptionLists() {
        return validSubscription().list().ofMinSize(1).ofMaxSize(10);
    }

    private Arbitrary<Subscription> validSubscription() {
        return Arbitraries.create(() -> {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            Money price = new Money(BigDecimal.valueOf(29.90), "BRL");
            LocalDate startDate = LocalDate.now().minusMonths(1);
            LocalDate expirationDate = LocalDate.now().minusDays(1);
            Instant now = Instant.now();

            return new Subscription(
                    id, userId, planId, price,
                    SubscriptionStatus.ATIVA,
                    startDate, expirationDate,
                    null, null, 0, 1L, now, now
            );
        });
    }
}
