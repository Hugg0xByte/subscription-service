package com.globo.subscription.adapter.outbound.messaging;

import com.google.cloud.spring.pubsub.PubSubAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Initializes Pub/Sub topics and subscriptions on application startup.
 * Creates topics and subscriptions if they don't already exist.
 * Intended for local development with the Pub/Sub emulator.
 */
@Component
public class PubSubInitializer {

    private static final Logger log = LoggerFactory.getLogger(PubSubInitializer.class);

    private final PubSubAdmin pubSubAdmin;
    private final String pendingTopic;
    private final String pendingSubscription;
    private final String processedTopic;
    private final String processedSubscription;
    private final String pendingDlqTopic;
    private final String processedDlqTopic;

    public PubSubInitializer(PubSubAdmin pubSubAdmin,
                              @Value("${pubsub.topic.pendente-pagamento}") String pendingTopic,
                              @Value("${pubsub.subscription.pendente-pagamento}") String pendingSubscription,
                              @Value("${pubsub.topic.pagamento-processado}") String processedTopic,
                              @Value("${pubsub.subscription.pagamento-processado}") String processedSubscription,
                              @Value("${pubsub.topic.pendente-pagamento-dlq}") String pendingDlqTopic,
                              @Value("${pubsub.topic.pagamento-processado-dlq}") String processedDlqTopic) {
        this.pubSubAdmin = pubSubAdmin;
        this.pendingTopic = pendingTopic;
        this.pendingSubscription = pendingSubscription;
        this.processedTopic = processedTopic;
        this.processedSubscription = processedSubscription;
        this.pendingDlqTopic = pendingDlqTopic;
        this.processedDlqTopic = processedDlqTopic;
    }

    @PostConstruct
    public void initializeTopicsAndSubscriptions() {
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                createTopicIfNotExists(pendingTopic);
                createTopicIfNotExists(processedTopic);
                createTopicIfNotExists(pendingDlqTopic);
                createTopicIfNotExists(processedDlqTopic);
                createSubscriptionIfNotExists(pendingSubscription, pendingTopic);
                createSubscriptionIfNotExists(processedSubscription, processedTopic);
                log.info("Pub/Sub topics and subscriptions initialized successfully");
                return;
            } catch (Exception e) {
                log.warn("Pub/Sub initialization attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Failed to initialize Pub/Sub topics after {} attempts. Topics/subscriptions may not exist.", maxRetries);
    }

    private void createTopicIfNotExists(String topicName) {
        try {
            if (pubSubAdmin.getTopic(topicName) == null) {
                pubSubAdmin.createTopic(topicName);
                log.info("Created topic: {}", topicName);
            }
        } catch (Exception e) {
            log.warn("Could not create topic '{}': {}", topicName, e.getMessage());
        }
    }

    private void createSubscriptionIfNotExists(String subscriptionName, String topicName) {
        try {
            if (pubSubAdmin.getSubscription(subscriptionName) == null) {
                pubSubAdmin.createSubscription(subscriptionName, topicName);
                log.info("Created subscription: {} -> {}", subscriptionName, topicName);
            }
        } catch (Exception e) {
            log.warn("Could not create subscription '{}': {}", subscriptionName, e.getMessage());
        }
    }
}
