package com.globo.subscription.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.application.dto.PaymentRequestMessage;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.port.PaymentGatewayPort;
import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.domain.entity.PaymentAttempt;
import com.globo.subscription.domain.enums.PaymentAttemptStatus;
import com.globo.subscription.domain.vo.Money;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;

/**
 * Mock payment consumer that subscribes to "pendente-de-pagamento",
 * simulates payment processing, and publishes result to "pagamento-processado".
 * Runs as a Spring component within the same application for local development.
 */
@Component
public class PaymentConsumerMock {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumerMock.class);

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentGatewayPort paymentGatewayPort;
    private final String pendingSubscription;
    private final String processedTopic;

    public PaymentConsumerMock(PubSubTemplate pubSubTemplate,
                               ObjectMapper objectMapper,
                               PaymentGatewayPort paymentGatewayPort,
                               @Value("${pubsub.subscription.pendente-pagamento}") String pendingSubscription,
                               @Value("${pubsub.topic.pagamento-processado}") String processedTopic) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.paymentGatewayPort = paymentGatewayPort;
        this.pendingSubscription = pendingSubscription;
        this.processedTopic = processedTopic;
    }

    @PostConstruct
    public void startListening() {
        pubSubTemplate.subscribe(pendingSubscription, this::handleMessage);
        log.info("PaymentConsumerMock subscribed to '{}'", pendingSubscription);
    }

    void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        try {
            String payload = message.getPubsubMessage().getData().toStringUtf8();
            PaymentRequestMessage request = objectMapper.readValue(payload, PaymentRequestMessage.class);

            log.info("Processing payment for subscription {} with idempotencyKey {}",
                    request.subscriptionId(), request.idempotencyKey());

            PaymentAttempt attempt = new PaymentAttempt(
                    UUID.randomUUID(),
                    request.subscriptionId(),
                    new Money(request.amount(), request.currency()),
                    PaymentAttemptStatus.PROCESSING,
                    request.attemptNumber(),
                    request.idempotencyKey(),
                    null,
                    null,
                    null,
                    Instant.now(),
                    null
            );

            PaymentResult result = paymentGatewayPort.processPayment(attempt);

            PaymentResultMessage resultMessage = buildResultMessage(request, result);
            String resultJson = objectMapper.writeValueAsString(resultMessage);
            pubSubTemplate.publish(processedTopic, resultJson).get();

            message.ack();
            log.info("Payment result published for subscription {}: {}",
                    request.subscriptionId(), resultMessage.status());
        } catch (Exception e) {
            log.error("Failed to process payment message: {}", e.getMessage(), e);
            message.nack();
        }
    }

    private PaymentResultMessage buildResultMessage(PaymentRequestMessage request, PaymentResult result) {
        return switch (result) {
            case PaymentResult.Approved approved -> new PaymentResultMessage(
                    request.subscriptionId(),
                    request.userId(),
                    PaymentResultMessage.PaymentStatus.APPROVED,
                    approved.providerTransactionId(),
                    null,
                    null,
                    request.idempotencyKey(),
                    Instant.now()
            );
            case PaymentResult.Failed failed -> new PaymentResultMessage(
                    request.subscriptionId(),
                    request.userId(),
                    PaymentResultMessage.PaymentStatus.FAILED,
                    null,
                    failed.errorCode(),
                    failed.errorMessage(),
                    request.idempotencyKey(),
                    Instant.now()
            );
        };
    }
}
