package com.globo.subscription.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PubSubMessagePublisherAdapterTest {

    private PubSubTemplate pubSubTemplate;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private PubSubMessagePublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        pubSubTemplate = mock(PubSubTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        meterRegistry = new SimpleMeterRegistry();
        adapter = new PubSubMessagePublisherAdapter(pubSubTemplate, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("Should publish message successfully and increment success counter")
    void shouldPublishSuccessfullyAndIncrementSuccessCounter() throws Exception {
        String topic = "pendente-de-pagamento";
        Object payload = new TestPayload("test-id", "test-data");
        String json = "{\"id\":\"test-id\",\"data\":\"test-data\"}";

        when(objectMapper.writeValueAsString(payload)).thenReturn(json);
        when(pubSubTemplate.publish(eq(topic), eq(json))).thenReturn(CompletableFuture.completedFuture("msg-id"));

        adapter.publish(topic, payload);

        verify(objectMapper).writeValueAsString(payload);
        verify(pubSubTemplate).publish(topic, json);

        Counter successCounter = meterRegistry.find("pubsub.message.published")
                .tag("outcome", "success")
                .counter();
        Counter failureCounter = meterRegistry.find("pubsub.message.published")
                .tag("outcome", "failure")
                .counter();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should increment failure counter and throw RuntimeException when PubSubTemplate fails")
    void shouldIncrementFailureCounterAndThrowWhenPublishFails() throws Exception {
        String topic = "pendente-de-pagamento";
        Object payload = new TestPayload("test-id", "test-data");
        String json = "{\"id\":\"test-id\",\"data\":\"test-data\"}";

        when(objectMapper.writeValueAsString(payload)).thenReturn(json);
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Pub/Sub unavailable"));
        when(pubSubTemplate.publish(eq(topic), eq(json))).thenReturn(failedFuture);

        assertThatThrownBy(() -> adapter.publish(topic, payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish message to topic: " + topic);

        Counter successCounter = meterRegistry.find("pubsub.message.published")
                .tag("outcome", "success")
                .counter();
        Counter failureCounter = meterRegistry.find("pubsub.message.published")
                .tag("outcome", "failure")
                .counter();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(0.0);
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment failure counter and throw RuntimeException when serialization fails")
    void shouldIncrementFailureCounterAndThrowWhenSerializationFails() throws Exception {
        String topic = "pagamento-processado";
        Object payload = new TestPayload("test-id", "test-data");

        when(objectMapper.writeValueAsString(payload))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        assertThatThrownBy(() -> adapter.publish(topic, payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish message to topic: " + topic);

        Counter failureCounter = meterRegistry.find("pubsub.message.published")
                .tag("outcome", "failure")
                .counter();

        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);

        verify(pubSubTemplate, never()).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("Should serialize payload with ObjectMapper before publishing")
    void shouldSerializePayloadWithObjectMapper() throws Exception {
        String topic = "pendente-de-pagamento";
        Object payload = new TestPayload("sub-123", "payment-data");
        String expectedJson = "{\"id\":\"sub-123\",\"data\":\"payment-data\"}";

        when(objectMapper.writeValueAsString(payload)).thenReturn(expectedJson);
        when(pubSubTemplate.publish(eq(topic), eq(expectedJson)))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));

        adapter.publish(topic, payload);

        verify(objectMapper).writeValueAsString(payload);
        verify(pubSubTemplate).publish(topic, expectedJson);
    }

    @Test
    @DisplayName("Should publish to the correct topic")
    void shouldPublishToCorrectTopic() throws Exception {
        String topic = "pagamento-processado";
        Object payload = new TestPayload("id", "data");
        String json = "{}";

        when(objectMapper.writeValueAsString(payload)).thenReturn(json);
        when(pubSubTemplate.publish(eq(topic), eq(json))).thenReturn(CompletableFuture.completedFuture("msg-id"));

        adapter.publish(topic, payload);

        verify(pubSubTemplate).publish(eq(topic), eq(json));
    }

    /**
     * Simple test payload for unit testing.
     */
    private record TestPayload(String id, String data) {}
}
