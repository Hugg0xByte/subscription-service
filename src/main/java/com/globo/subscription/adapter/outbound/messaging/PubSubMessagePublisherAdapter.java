package com.globo.subscription.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.application.port.MessagePublisherPort;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub implementation of MessagePublisherPort.
 * Serializes payloads to JSON and publishes via PubSubTemplate.
 * Retries with exponential backoff on failure.
 */
@Component
public class PubSubMessagePublisherAdapter implements MessagePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(PubSubMessagePublisherAdapter.class);

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public PubSubMessagePublisherAdapter(PubSubTemplate pubSubTemplate,
                                          ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.publishSuccessCounter = Counter.builder("pubsub.message.published")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("pubsub.message.published")
                .tag("outcome", "failure")
                .register(meterRegistry);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void publish(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            pubSubTemplate.publish(topic, json).get();
            publishSuccessCounter.increment();
            log.info("Message published to topic '{}' successfully", topic);
        } catch (Exception e) {
            publishFailureCounter.increment();
            log.error("Failed to publish message to topic '{}': {}", topic, e.getMessage());
            throw new RuntimeException("Failed to publish message to topic: " + topic, e);
        }
    }
}
