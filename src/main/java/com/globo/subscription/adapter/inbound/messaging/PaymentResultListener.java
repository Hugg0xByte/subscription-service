package com.globo.subscription.adapter.inbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbound adapter that listens to "pagamento-processado" topic
 * and updates subscription state based on payment results.
 * Processes messages idempotently using the idempotencyKey.
 * Delegates transactional processing to PaymentResultProcessor.
 */
@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentResultProcessor paymentResultProcessor;
    private final String processedSubscription;
    private final Counter errorCounter;
    private final Timer processingTimer;
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public PaymentResultListener(PubSubTemplate pubSubTemplate,
                                  ObjectMapper objectMapper,
                                  PaymentResultProcessor paymentResultProcessor,
                                  @Value("${pubsub.subscription.pagamento-processado}") String processedSubscription,
                                  MeterRegistry meterRegistry) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.paymentResultProcessor = paymentResultProcessor;
        this.processedSubscription = processedSubscription;
        this.errorCounter = Counter.builder("pubsub.message.consumed")
                .tag("outcome", "error").register(meterRegistry);
        this.processingTimer = Timer.builder("pubsub.message.processing.duration")
                .register(meterRegistry);
    }

    @PostConstruct
    public void startListening() {
        pubSubTemplate.subscribe(processedSubscription, this::handleMessage);
        log.info("PaymentResultListener subscribed to '{}'", processedSubscription);
    }

    void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        Timer.Sample sample = Timer.start();
        try {
            String payload = message.getPubsubMessage().getData().toStringUtf8();
            PaymentResultMessage resultMessage = objectMapper.readValue(payload, PaymentResultMessage.class);

            MDC.put("subscriptionId", resultMessage.subscriptionId().toString());
            MDC.put("idempotencyKey", resultMessage.idempotencyKey());

            // Idempotency check
            if (processedKeys.contains(resultMessage.idempotencyKey())) {
                log.info("Duplicate message detected for idempotencyKey '{}'. Skipping.", resultMessage.idempotencyKey());
                message.ack();
                return;
            }

            paymentResultProcessor.process(resultMessage);
            processedKeys.add(resultMessage.idempotencyKey());
            message.ack();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to deserialize payment result message: {}", e.getMessage());
            errorCounter.increment();
            message.ack(); // Don't retry malformed messages
        } catch (Exception e) {
            log.error("Error processing payment result: {}", e.getMessage(), e);
            errorCounter.increment();
            message.nack(); // Retry via Pub/Sub
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
}
