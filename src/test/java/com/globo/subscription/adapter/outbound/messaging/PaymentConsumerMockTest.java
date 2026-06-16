package com.globo.subscription.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentConsumerMockTest {

    private PubSubTemplate pubSubTemplate;
    private ObjectMapper objectMapper;
    private PaymentGatewayPort paymentGatewayPort;
    private PaymentConsumerMock consumer;

    private static final String PENDING_SUBSCRIPTION = "pendente-pagamento-sub";
    private static final String PROCESSED_TOPIC = "pagamento-processado";

    @BeforeEach
    void setUp() {
        pubSubTemplate = mock(PubSubTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        paymentGatewayPort = mock(PaymentGatewayPort.class);
        consumer = new PaymentConsumerMock(
                pubSubTemplate, objectMapper, paymentGatewayPort,
                PENDING_SUBSCRIPTION, PROCESSED_TOPIC);
    }

    @Test
    @DisplayName("Should ack message and publish result when payment is APPROVED")
    void shouldAckAndPublishWhenPaymentApproved() throws Exception {
        // Arrange
        String payload = "{\"valid\":\"json\"}";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(payload);

        PaymentRequestMessage request = createSampleRequest();
        when(objectMapper.readValue(payload, PaymentRequestMessage.class)).thenReturn(request);

        String txnId = "txn-" + UUID.randomUUID();
        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Approved(txnId));

        String resultJson = "{\"status\":\"APPROVED\"}";
        when(objectMapper.writeValueAsString(any(PaymentResultMessage.class))).thenReturn(resultJson);
        when(pubSubTemplate.publish(eq(PROCESSED_TOPIC), eq(resultJson)))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        // Act
        consumer.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(pubSubTemplate).publish(PROCESSED_TOPIC, resultJson);
    }

    @Test
    @DisplayName("Should ack message and publish result when payment is FAILED")
    void shouldAckAndPublishWhenPaymentFailed() throws Exception {
        // Arrange
        String payload = "{\"valid\":\"json\"}";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(payload);

        PaymentRequestMessage request = createSampleRequest();
        when(objectMapper.readValue(payload, PaymentRequestMessage.class)).thenReturn(request);

        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Failed("INSUFFICIENT_FUNDS", "Not enough balance"));

        String resultJson = "{\"status\":\"FAILED\"}";
        when(objectMapper.writeValueAsString(any(PaymentResultMessage.class))).thenReturn(resultJson);
        when(pubSubTemplate.publish(eq(PROCESSED_TOPIC), eq(resultJson)))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        // Act
        consumer.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(pubSubTemplate).publish(PROCESSED_TOPIC, resultJson);
    }

    @Test
    @DisplayName("Should nack message when deserialization fails")
    void shouldNackWhenDeserializationFails() throws Exception {
        // Arrange
        String payload = "invalid-json";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(payload);

        when(objectMapper.readValue(payload, PaymentRequestMessage.class))
                .thenThrow(new JsonProcessingException("Malformed JSON") {});

        // Act
        consumer.handleMessage(message);

        // Assert
        verify(message).nack();
        verify(message, never()).ack();
        verify(pubSubTemplate, never()).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("Should include providerTransactionId in result when payment is APPROVED")
    void shouldIncludeProviderTransactionIdWhenApproved() throws Exception {
        // Arrange
        String payload = "{\"valid\":\"json\"}";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(payload);

        PaymentRequestMessage request = createSampleRequest();
        when(objectMapper.readValue(payload, PaymentRequestMessage.class)).thenReturn(request);

        String expectedTxnId = "provider-txn-12345";
        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Approved(expectedTxnId));

        when(objectMapper.writeValueAsString(any(PaymentResultMessage.class))).thenReturn("{}");
        when(pubSubTemplate.publish(eq(PROCESSED_TOPIC), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        // Act
        consumer.handleMessage(message);

        // Assert - capture the PaymentResultMessage passed to writeValueAsString
        ArgumentCaptor<PaymentResultMessage> captor = ArgumentCaptor.forClass(PaymentResultMessage.class);
        verify(objectMapper).writeValueAsString(captor.capture());

        PaymentResultMessage resultMessage = captor.getValue();
        assertThat(resultMessage.status()).isEqualTo(PaymentResultMessage.PaymentStatus.APPROVED);
        assertThat(resultMessage.providerTransactionId()).isEqualTo(expectedTxnId);
        assertThat(resultMessage.errorCode()).isNull();
        assertThat(resultMessage.errorMessage()).isNull();
    }

    @Test
    @DisplayName("Should include errorCode in result when payment is FAILED")
    void shouldIncludeErrorCodeWhenFailed() throws Exception {
        // Arrange
        String payload = "{\"valid\":\"json\"}";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(payload);

        PaymentRequestMessage request = createSampleRequest();
        when(objectMapper.readValue(payload, PaymentRequestMessage.class)).thenReturn(request);

        String expectedErrorCode = "CARD_DECLINED";
        String expectedErrorMessage = "Card was declined by issuer";
        when(paymentGatewayPort.processPayment(any(PaymentAttempt.class)))
                .thenReturn(new PaymentResult.Failed(expectedErrorCode, expectedErrorMessage));

        when(objectMapper.writeValueAsString(any(PaymentResultMessage.class))).thenReturn("{}");
        when(pubSubTemplate.publish(eq(PROCESSED_TOPIC), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        // Act
        consumer.handleMessage(message);

        // Assert - capture the PaymentResultMessage passed to writeValueAsString
        ArgumentCaptor<PaymentResultMessage> captor = ArgumentCaptor.forClass(PaymentResultMessage.class);
        verify(objectMapper).writeValueAsString(captor.capture());

        PaymentResultMessage resultMessage = captor.getValue();
        assertThat(resultMessage.status()).isEqualTo(PaymentResultMessage.PaymentStatus.FAILED);
        assertThat(resultMessage.errorCode()).isEqualTo(expectedErrorCode);
        assertThat(resultMessage.errorMessage()).isEqualTo(expectedErrorMessage);
        assertThat(resultMessage.providerTransactionId()).isNull();
    }

    private BasicAcknowledgeablePubsubMessage createMockMessage(String payload) {
        BasicAcknowledgeablePubsubMessage message = mock(BasicAcknowledgeablePubsubMessage.class);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .build();
        when(message.getPubsubMessage()).thenReturn(pubsubMessage);
        return message;
    }

    private PaymentRequestMessage createSampleRequest() {
        return new PaymentRequestMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                "BRL",
                1,
                "idem-key-" + UUID.randomUUID(),
                Instant.now()
        );
    }
}
