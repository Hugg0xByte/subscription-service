package com.globo.subscription.adapter.inbound.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.globo.subscription.adapter.inbound.messaging.PaymentResultProcessor;
import com.globo.subscription.application.dto.PaymentResultMessage;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based test for idempotent message processing in PaymentResultListener.
 *
 * <p><b>Validates: Requirements 3.5</b></p>
 *
 * <p>Property 7: Idempotent message processing —
 * For any valid PaymentResultMessage, processing the same message twice (same idempotencyKey)
 * SHALL result in subscriptionRepositoryPort.save being called exactly once.
 * The second processing does not modify the subscription or persist again.</p>
 */
class PaymentResultListenerIdempotencyPropertyTest {

    private final ObjectMapper objectMapper;

    PaymentResultListenerIdempotencyPropertyTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Property(tries = 50)
    void secondProcessingOfSameMessageDoesNotPersistAgain(
            @ForAll("validPaymentResultMessages") PaymentResultMessage resultMessage) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        SubscriptionRepositoryPort subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        SubscriptionCachePort subscriptionCachePort = mock(SubscriptionCachePort.class);
        EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Create a subscription with PENDENTE_PAGAMENTO status (eligible for payment processing)
        Subscription subscription = new Subscription(
                resultMessage.subscriptionId(),
                resultMessage.userId(),
                UUID.randomUUID(),
                new Money(new BigDecimal("29.90"), "BRL"),
                SubscriptionStatus.PENDENTE_PAGAMENTO,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusDays(5),
                null,
                null,
                0,
                1L,
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60)
        );

        when(subscriptionRepositoryPort.findById(resultMessage.subscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepositoryPort.save(any(Subscription.class)))
                .thenReturn(subscription);

        PaymentResultProcessor paymentResultProcessor = new PaymentResultProcessor(
                subscriptionRepositoryPort, subscriptionCachePort, eventPublisherPort, meterRegistry
        );

        PaymentResultListener listener = new PaymentResultListener(
                pubSubTemplate,
                objectMapper,
                paymentResultProcessor,
                "pagamento-processado-sub",
                meterRegistry
        );

        // Build mock PubSub message
        String payload = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage firstMessage = mockPubsubMessage(payload);
        BasicAcknowledgeablePubsubMessage secondMessage = mockPubsubMessage(payload);

        // Act - process the same message twice
        listener.handleMessage(firstMessage);
        listener.handleMessage(secondMessage);

        // Assert - save is called exactly once (first processing only)
        verify(subscriptionRepositoryPort, times(1)).save(any(Subscription.class));

        // Assert - both messages are acknowledged
        verify(firstMessage).ack();
        verify(secondMessage).ack();
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
    Arbitrary<PaymentResultMessage> validPaymentResultMessages() {
        Arbitrary<PaymentResultMessage> approvedMessages = approvedVariant();
        Arbitrary<PaymentResultMessage> failedMessages = failedVariant();

        return Arbitraries.oneOf(approvedMessages, failedMessages);
    }

    private Arbitrary<PaymentResultMessage> approvedVariant() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> providerTransactionIds = Arbitraries.create(UUID::randomUUID)
                .map(UUID::toString);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> processedAts = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, providerTransactionIds, idempotencyKeys, processedAts)
                .as((subscriptionId, userId, providerTransactionId, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId,
                                userId,
                                PaymentResultMessage.PaymentStatus.APPROVED,
                                providerTransactionId,
                                null,
                                null,
                                idempotencyKey,
                                processedAt
                        )
                );
    }

    private Arbitrary<PaymentResultMessage> failedVariant() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> errorCodes = Arbitraries.of(
                "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "NETWORK_ERROR", "FRAUD_DETECTED");
        Arbitrary<String> errorMessages = Arbitraries.of(
                "Payment declined due to insufficient funds",
                "Card has expired",
                "Network timeout during processing",
                "Transaction flagged as potentially fraudulent");
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> processedAts = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, errorCodes, errorMessages, idempotencyKeys, processedAts)
                .as((subscriptionId, userId, errorCode, errorMessage, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId,
                                userId,
                                PaymentResultMessage.PaymentStatus.FAILED,
                                null,
                                errorCode,
                                errorMessage,
                                idempotencyKey,
                                processedAt
                        )
                );
    }
}
