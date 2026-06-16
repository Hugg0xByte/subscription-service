package com.globo.subscription.adapter.outbound.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.application.dto.PaymentRequestMessage;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.port.PaymentGatewayPort;
import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.domain.entity.PaymentAttempt;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for PaymentConsumerMock producing valid PaymentResultMessage.
 *
 * <p><b>Validates: Requirements 2.2, 2.3</b></p>
 *
 * <p>Property 4: Consumer mock produces valid PaymentResultMessage —
 * For any valid PaymentRequestMessage, the consumer mock SHALL produce a PaymentResultMessage
 * that preserves subscriptionId, userId, and idempotencyKey from the request,
 * has a non-null status and processedAt, and has conditional fields consistent with the status
 * (providerTransactionId non-null when APPROVED, errorCode non-null when FAILED).</p>
 */
class PaymentConsumerMockPropertyTest {

    @Property(tries = 50)
    void shouldProduceValidPaymentResultMessageWithApprovedStatus(
            @ForAll("validPaymentRequests") PaymentRequestMessage request) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        PaymentGatewayPort paymentGatewayPort = mock(PaymentGatewayPort.class);

        String providerTxId = "tx-" + UUID.randomUUID();
        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Approved(providerTxId));
        when(pubSubTemplate.publish(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        PaymentConsumerMock consumer = new PaymentConsumerMock(
                pubSubTemplate, objectMapper, paymentGatewayPort,
                "pendente-de-pagamento-sub", "pagamento-processado"
        );

        // Build mock message
        String payload = objectMapper.writeValueAsString(request);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        consumer.handleMessage(message);

        // Assert - capture the serialized result
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(pubSubTemplate).publish(anyString(), jsonCaptor.capture());

        PaymentResultMessage resultMessage = objectMapper.readValue(
                jsonCaptor.getValue(), PaymentResultMessage.class);

        // Verify preserved fields
        assertThat(resultMessage.subscriptionId()).isEqualTo(request.subscriptionId());
        assertThat(resultMessage.userId()).isEqualTo(request.userId());
        assertThat(resultMessage.idempotencyKey()).isEqualTo(request.idempotencyKey());

        // Verify non-null required fields
        assertThat(resultMessage.status()).isNotNull();
        assertThat(resultMessage.processedAt()).isNotNull();

        // Verify APPROVED-specific fields
        assertThat(resultMessage.status()).isEqualTo(PaymentResultMessage.PaymentStatus.APPROVED);
        assertThat(resultMessage.providerTransactionId()).isNotNull();
    }

    @Property(tries = 50)
    void shouldProduceValidPaymentResultMessageWithFailedStatus(
            @ForAll("validPaymentRequests") PaymentRequestMessage request) throws Exception {

        // Arrange
        PubSubTemplate pubSubTemplate = mock(PubSubTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        PaymentGatewayPort paymentGatewayPort = mock(PaymentGatewayPort.class);

        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Failed("INSUFFICIENT_FUNDS", "Card declined"));
        when(pubSubTemplate.publish(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        PaymentConsumerMock consumer = new PaymentConsumerMock(
                pubSubTemplate, objectMapper, paymentGatewayPort,
                "pendente-de-pagamento-sub", "pagamento-processado"
        );

        // Build mock message
        String payload = objectMapper.writeValueAsString(request);
        BasicAcknowledgeablePubsubMessage message = mockPubsubMessage(payload);

        // Act
        consumer.handleMessage(message);

        // Assert - capture the serialized result
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(pubSubTemplate).publish(anyString(), jsonCaptor.capture());

        PaymentResultMessage resultMessage = objectMapper.readValue(
                jsonCaptor.getValue(), PaymentResultMessage.class);

        // Verify preserved fields
        assertThat(resultMessage.subscriptionId()).isEqualTo(request.subscriptionId());
        assertThat(resultMessage.userId()).isEqualTo(request.userId());
        assertThat(resultMessage.idempotencyKey()).isEqualTo(request.idempotencyKey());

        // Verify non-null required fields
        assertThat(resultMessage.status()).isNotNull();
        assertThat(resultMessage.processedAt()).isNotNull();

        // Verify FAILED-specific fields
        assertThat(resultMessage.status()).isEqualTo(PaymentResultMessage.PaymentStatus.FAILED);
        assertThat(resultMessage.errorCode()).isNotNull();
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
    Arbitrary<PaymentRequestMessage> validPaymentRequests() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("99999.99"))
                .ofScale(2);
        Arbitrary<String> currencies = Arbitraries.of("BRL", "USD", "EUR");
        Arbitrary<Integer> attempts = Arbitraries.integers().between(1, 10);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(1_000_000_000L, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, uuids, uuids, amounts, currencies, attempts, idempotencyKeys)
                .as((messageId, subscriptionId, userId, planId, amount, currency, attemptNumber, idempotencyKey) ->
                        new Object[]{messageId, subscriptionId, userId, planId, amount, currency, attemptNumber, idempotencyKey})
                .flatMap(args -> timestamps.map(timestamp ->
                        new PaymentRequestMessage(
                                (UUID) args[0], (UUID) args[1], (UUID) args[2], (UUID) args[3],
                                (BigDecimal) args[4], (String) args[5], (Integer) args[6],
                                (String) args[7], timestamp)));
    }
}
