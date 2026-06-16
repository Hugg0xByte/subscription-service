package com.globo.subscription.adapter.inbound.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.dto.PaymentResultMessage.PaymentStatus;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.vo.Money;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for PaymentResultListener performing all side effects.
 *
 * <p><b>Validates: Requirements 3.3</b></p>
 *
 * <p>Property 6: Payment result listener performs all side effects —
 * For any valid PaymentResultMessage (APPROVED or FAILED) with an existing subscription
 * in PENDENTE_PAGAMENTO status, the listener SHALL persist the updated subscription,
 * evict the cache for the user, and publish all domain events generated.</p>
 */
class PaymentResultListenerSideEffectsPropertyTest {

    @Property(tries = 50)
    void shouldPerformAllSideEffectsForApprovedPayment(
            @ForAll("approvedPaymentResults") PaymentResultMessage resultMessage) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Subscription subscription = buildSubscription(resultMessage.subscriptionId(), resultMessage.userId());
        when(subscriptionRepositoryPort.findById(resultMessage.subscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResultListener listener = new PaymentResultListener(
                pubSubTemplate, objectMapper, subscriptionRepositoryPort,
                subscriptionCachePort, eventPublisherPort,
                "pagamento-processado-sub", meterRegistry
        );

        // Build mock Pub/Sub message
        String payload = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        listener.handleMessage(message);

        // Assert - persist side effect
        verify(subscriptionRepositoryPort).save(any(Subscription.class));

        // Assert - cache eviction side effect
        verify(subscriptionCachePort).evictActiveSubscription(resultMessage.userId());

        // Assert - domain event publication side effect
        // processSuccessfulPayment produces PaymentApproved + SubscriptionRenewed events
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisherPort, atLeastOnce()).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).isNotEmpty();
    }

    @Property(tries = 50)
    void shouldPerformAllSideEffectsForFailedPayment(
            @ForAll("failedPaymentResults") PaymentResultMessage resultMessage) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Subscription subscription = buildSubscription(resultMessage.subscriptionId(), resultMessage.userId());
        when(subscriptionRepositoryPort.findById(resultMessage.subscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResultListener listener = new PaymentResultListener(
                pubSubTemplate, objectMapper, subscriptionRepositoryPort,
                subscriptionCachePort, eventPublisherPort,
                "pagamento-processado-sub", meterRegistry
        );

        // Build mock Pub/Sub message
        String payload = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        listener.handleMessage(message);

        // Assert - persist side effect
        verify(subscriptionRepositoryPort).save(any(Subscription.class));

        // Assert - cache eviction side effect
        verify(subscriptionCachePort).evictActiveSubscription(resultMessage.userId());

        // Assert - domain event publication side effect
        // processFailedPayment produces PaymentFailed event (and possibly SubscriptionSuspended)
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisherPort, atLeastOnce()).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).isNotEmpty();
    }

    private Subscription buildSubscription(UUID subscriptionId, UUID userId) {
        return new Subscription(
                subscriptionId,
                userId,
                UUID.randomUUID(),
                new Money(BigDecimal.valueOf(29.90), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.now().minusMonths(1),
                LocalDate.now().minusDays(1),
                null,
                null,
                0,
                1L,
                Instant.now(),
                Instant.now()
        );
    }

    private BasicAcknowledgeablePubsubMessage mockPubsubMessage(String payload) {
        BasicAcknowledgeablePubsubMessage message = mock(BasicAcknowledgeablePubsubMessage.class);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .build();
        when(message.getPubsubMessage()).thenReturn(pubsubMessage);
        return message;
    }

    @Provide
    Arbitrary<PaymentResultMessage> approvedPaymentResults() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> providerTxIds = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "tx-" + uuid);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(1_000_000_000L, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, providerTxIds, idempotencyKeys, timestamps)
                .as((subscriptionId, userId, providerTxId, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId, userId,
                                PaymentStatus.APPROVED,
                                providerTxId, null, null,
                                idempotencyKey, processedAt
                        ));
    }

    @Provide
    Arbitrary<PaymentResultMessage> failedPaymentResults() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> errorCodes = Arbitraries.of(
                "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "NETWORK_ERROR", "GATEWAY_TIMEOUT");
        Arbitrary<String> errorMessages = Arbitraries.of(
                "Card declined", "Payment timeout", "Gateway unavailable", "Insufficient balance");
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(1_000_000_000L, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, errorCodes, errorMessages, idempotencyKeys, timestamps)
                .as((subscriptionId, userId, errorCode, errorMessage, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId, userId,
                                PaymentStatus.FAILED,
                                null, errorCode, errorMessage,
                                idempotencyKey, processedAt
                        ));
    }
}
