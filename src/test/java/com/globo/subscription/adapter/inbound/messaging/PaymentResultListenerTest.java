package com.globo.subscription.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentResultListenerTest {

    private PubSubTemplate pubSubTemplate;
    private ObjectMapper objectMapper;
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    private SubscriptionCachePort subscriptionCachePort;
    private EventPublisherPort eventPublisherPort;
    private SimpleMeterRegistry meterRegistry;
    private PaymentResultListener listener;

    private static final String PROCESSED_SUBSCRIPTION = "pagamento-processado-sub";

    @BeforeEach
    void setUp() {
        pubSubTemplate = mock(PubSubTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        subscriptionRepositoryPort = mock(SubscriptionRepositoryPort.class);
        subscriptionCachePort = mock(SubscriptionCachePort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        meterRegistry = new SimpleMeterRegistry();

        listener = new PaymentResultListener(
                pubSubTemplate,
                objectMapper,
                subscriptionRepositoryPort,
                subscriptionCachePort,
                eventPublisherPort,
                PROCESSED_SUBSCRIPTION,
                meterRegistry
        );
    }

    @Test
    @DisplayName("APPROVED message invokes processSuccessfulPayment, saves, evicts cache, publishes events, and acks")
    void shouldProcessApprovedMessage() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.APPROVED,
                "txn-123", null, null, idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(subscriptionRepositoryPort).save(subscription);
        verify(subscriptionCachePort).evictActiveSubscription(userId);
        verify(eventPublisherPort, atLeastOnce()).publish(any(DomainEvent.class));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(subscription.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("FAILED message invokes processFailedPayment, saves, evicts cache, publishes events, and acks")
    void shouldProcessFailedMessage() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.FAILED,
                null, "INSUFFICIENT_FUNDS", "Not enough balance",
                idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(subscriptionRepositoryPort).save(subscription);
        verify(subscriptionCachePort).evictActiveSubscription(userId);
        verify(eventPublisherPort, atLeastOnce()).publish(any(DomainEvent.class));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
        assertThat(subscription.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Subscription not found: logs warning, acks message, does not save")
    void shouldAckWhenSubscriptionNotFound() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.empty());

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.APPROVED,
                "txn-123", null, null, idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(subscriptionRepositoryPort, never()).save(any());
        verify(subscriptionCachePort, never()).evictActiveSubscription(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    @DisplayName("Duplicate message with same idempotencyKey is skipped and acked without processing")
    void shouldSkipDuplicateMessage() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.APPROVED,
                "txn-123", null, null, idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage firstMessage = createMockMessage(json);
        BasicAcknowledgeablePubsubMessage secondMessage = createMockMessage(json);

        // Act - process first message
        listener.handleMessage(firstMessage);
        // Reset mock interactions to isolate second call
        reset(subscriptionRepositoryPort, subscriptionCachePort, eventPublisherPort);

        // Act - process duplicate message
        listener.handleMessage(secondMessage);

        // Assert - second message should be acked without processing
        verify(secondMessage).ack();
        verify(secondMessage, never()).nack();
        verify(subscriptionRepositoryPort, never()).findById(any());
        verify(subscriptionRepositoryPort, never()).save(any());
        verify(subscriptionCachePort, never()).evictActiveSubscription(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    @DisplayName("Deserialization failure: acks message without retry, increments error counter")
    void shouldAckOnDeserializationFailure() {
        // Arrange
        String invalidJson = "this is not valid json at all!!!";
        BasicAcknowledgeablePubsubMessage message = createMockMessage(invalidJson);

        // Act
        listener.handleMessage(message);

        // Assert
        verify(message).ack();
        verify(message, never()).nack();
        verify(subscriptionRepositoryPort, never()).findById(any());
        verify(subscriptionRepositoryPort, never()).save(any());

        // Verify error counter incremented
        Counter errorCounter = meterRegistry.find("pubsub.message.consumed")
                .tag("outcome", "error").counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Metrics: approved counter increments on APPROVED message")
    void shouldIncrementApprovedCounter() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.APPROVED,
                "txn-123", null, null, idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        Counter approvedCounter = meterRegistry.find("pubsub.message.consumed")
                .tag("outcome", "approved").counter();
        assertThat(approvedCounter).isNotNull();
        assertThat(approvedCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Metrics: failed counter increments on FAILED message")
    void shouldIncrementFailedCounter() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.FAILED,
                null, "ERR_CODE", "Error msg",
                idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        Counter failedCounter = meterRegistry.find("pubsub.message.consumed")
                .tag("outcome", "failed").counter();
        assertThat(failedCounter).isNotNull();
        assertThat(failedCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Metrics: timer records processing duration for each message")
    void shouldRecordProcessingDuration() throws Exception {
        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        Subscription subscription = createSubscription(subscriptionId, userId, SubscriptionStatus.PENDENTE_PAGAMENTO);
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResultMessage resultMessage = new PaymentResultMessage(
                subscriptionId, userId, PaymentStatus.APPROVED,
                "txn-123", null, null, idempotencyKey, Instant.now()
        );

        String json = objectMapper.writeValueAsString(resultMessage);
        BasicAcknowledgeablePubsubMessage message = createMockMessage(json);

        // Act
        listener.handleMessage(message);

        // Assert
        Timer timer = meterRegistry.find("pubsub.message.processing.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    // --- Helper methods ---

    private BasicAcknowledgeablePubsubMessage createMockMessage(String payload) {
        BasicAcknowledgeablePubsubMessage message = mock(BasicAcknowledgeablePubsubMessage.class);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .build();
        when(message.getPubsubMessage()).thenReturn(pubsubMessage);
        return message;
    }

    private Subscription createSubscription(UUID id, UUID userId, SubscriptionStatus status) {
        return new Subscription(
                id,
                userId,
                UUID.randomUUID(),
                new Money(new BigDecimal("39.90"), "BRL"),
                status,
                LocalDate.now().minusMonths(1),
                LocalDate.now(),
                null,
                null,
                0,
                0L,
                Instant.now(),
                Instant.now()
        );
    }
}
