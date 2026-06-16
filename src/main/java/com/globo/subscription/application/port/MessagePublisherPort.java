package com.globo.subscription.application.port;

/**
 * Port interface for publishing messages to a messaging system.
 * Decouples the application layer from specific messaging infrastructure (Pub/Sub, Kafka, etc.).
 */
public interface MessagePublisherPort {

    /**
     * Publishes a message payload to the specified topic.
     *
     * @param topic   the topic identifier to publish to
     * @param payload the message object to be serialized and published
     * @throws RuntimeException if publication fails after retries
     */
    void publish(String topic, Object payload);
}
