package com.globo.subscription.adapter.inbound.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.dto.PaymentResultMessage.PaymentStatus;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for PaymentResultListener routing based on payment status.
 *
 * <p><b>Validates: Requirements 3.1, 3.2</b></p>
 *
 * <p>Property 5: Payment result listener routes correctly based on status —
 * For any valid PaymentResultMessage with status APPROVED, the listener SHALL invoke
 * processSuccessfulPayment on the corresponding Subscription (resulting in ATIVA status).
 * For any valid PaymentResultMessage with status FAILED, the listener SHALL invoke
 * processFailedPayment on the corresponding Subscription.</p>
 */
class PaymentResultListenerPropertyTest {

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Property(tries = 50)
    void whenStatusIsApproved_shouldInvokeProcessSuccessfulPayment(
            @ForAll("approvedPaymentResults") PaymentResultMessage resultMessage) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        PaymentResultListener listener = new PaymentResultListener(
                pubSubTemplate, objectMapper, subscriptionRepositoryPort,
                subscriptionCachePort, eventPublisherPort,
                "pagamento-processado-sub", meterRegistry
        );

        // Create a subscription in PENDENTE_PAGAMENTO status (eligible for processSuccessfulPayment)
        Subscription subscription = new Subscription(
                resultMessage.subscriptionId(),
                resultMessage.userId(),
                UUID.randomUUID(),
                new Money(new BigDecimal("29.90"), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.now().minusMonths(1),
                LocalDate.now(),
                null, null, 0, 1L,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60)
        );

        when(subscriptionRepositoryPort.findById(resultMessage.subscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Build mock Pub/Sub message with serialized payload
        String payload = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        listener.handleMessage(message);

        // Assert - subscription should be ATIVA after processSuccessfulPayment
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(subscription.getFailedAttempts()).isEqualTo(0);

        // Verify persistence and cache eviction
        verify(subscriptionRepositoryPort).save(subscription);
        verify(subscriptionCachePort).evictActiveSubscription(resultMessage.userId());
        verify(message).ack();
    }

    @Property(tries = 50)
    void whenStatusIsFailed_shouldInvokeProcessFailedPayment(
            @ForAll("failedPaymentResults") PaymentResultMessage resultMessage) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        PaymentResultListener listener = new PaymentResultListener(
                pubSubTemplate, objectMapper, subscriptionRepositoryPort,
                subscriptionCachePort, eventPublisherPort,
                "pagamento-processado-sub", meterRegistry
        );

        // Create a subscription in PENDENTE_PAGAMENTO status (eligible for processFailedPayment)
        // With 0 failed attempts so it stays in PENDENTE_PAGAMENTO after one failure
        Subscription subscription = new Subscription(
                resultMessage.subscriptionId(),
                resultMessage.userId(),
                UUID.randomUUID(),
                new Money(new BigDecimal("29.90"), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.now().minusMonths(1),
                LocalDate.now(),
                null, null, 0, 1L,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60)
        );

        when(subscriptionRepositoryPort.findById(resultMessage.subscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Build mock Pub/Sub message with serialized payload
        String payload = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        listener.handleMessage(message);

        // Assert - subscription should have incremented failedAttempts
        // With 0 initial failed attempts + 1 = 1, status should be PENDENTE_PAGAMENTO (< 3 threshold)
        assertThat(subscription.getFailedAttempts()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);

        // Verify persistence and cache eviction
        verify(subscriptionRepositoryPort).save(subscription);
        verify(subscriptionCachePort).evictActiveSubscription(resultMessage.userId());
        verify(message).ack();
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
        Arbitrary<String> transactionIds = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "tx-" + uuid);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(1_000_000_000L, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, transactionIds, idempotencyKeys, timestamps)
                .as((subscriptionId, userId, txId, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId, userId,
                                PaymentStatus.APPROVED,
                                txId, null, null,
                                idempotencyKey, processedAt
                        ));
    }

    @Provide
    Arbitrary<PaymentResultMessage> failedPaymentResults() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> errorCodes = Arbitraries.of(
                "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "DECLINED", "NETWORK_ERROR");
        Arbitrary<String> errorMessages = Arbitraries.of(
                "Card declined", "Insufficient balance", "Card expired", "Network timeout");
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
