package com.globo.subscription.adapter.outbound.payment;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.globo.subscription.application.port.PaymentGatewayPort;
import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.domain.entity.PaymentAttempt;

/**
 * Mock implementation of PaymentGatewayPort for local development and testing.
 * Simulates payment processing with configurable success/failure/timeout ratios.
 * <p>
 * Default ratios: 80% approve, 15% reject, 5% timeout.
 * Configurable via application properties under {@code payment.mock.*}.
 */
@Component
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayAdapter.class);

    private final int approvePercentage;
    private final int rejectPercentage;
    private final int timeoutPercentage;
    private final long minDelayMs;
    private final long maxDelayMs;
    private final Random random;

    @Autowired
    public MockPaymentGatewayAdapter(
            @Value("${payment.mock.approve-percentage:80}") int approvePercentage,
            @Value("${payment.mock.reject-percentage:15}") int rejectPercentage,
            @Value("${payment.mock.timeout-percentage:5}") int timeoutPercentage,
            @Value("${payment.mock.min-delay-ms:100}") long minDelayMs,
            @Value("${payment.mock.max-delay-ms:500}") long maxDelayMs) {
        this.approvePercentage = approvePercentage;
        this.rejectPercentage = rejectPercentage;
        this.timeoutPercentage = timeoutPercentage;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.random = new Random();

        int total = approvePercentage + rejectPercentage + timeoutPercentage;
        if (total != 100) {
            log.warn("Payment mock ratios do not sum to 100% (got {}%). Normalizing behavior.", total);
        }

        log.info("MockPaymentGatewayAdapter initialized with ratios: approve={}%, reject={}%, timeout={}%",
                approvePercentage, rejectPercentage, timeoutPercentage);
    }

    /**
     * Constructor for testing, allowing injection of a custom Random instance.
     */
    MockPaymentGatewayAdapter(int approvePercentage, int rejectPercentage, int timeoutPercentage,
                              long minDelayMs, long maxDelayMs, Random random) {
        this.approvePercentage = approvePercentage;
        this.rejectPercentage = rejectPercentage;
        this.timeoutPercentage = timeoutPercentage;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.random = random;
    }

    @Override
    public PaymentResult processPayment(PaymentAttempt paymentAttempt) {
        log.info("Processing payment for subscription {} with idempotency key: {}",
                paymentAttempt.getSubscriptionId(), paymentAttempt.getIdempotencyKey());

        simulateProcessingDelay();

        int roll = random.nextInt(100);

        if (roll < approvePercentage) {
            String transactionId = UUID.randomUUID().toString();
            log.info("Payment APPROVED for subscription {}. Transaction ID: {}",
                    paymentAttempt.getSubscriptionId(), transactionId);
            return new PaymentResult.Approved(transactionId);
        } else if (roll < approvePercentage + rejectPercentage) {
            log.warn("Payment REJECTED for subscription {}. Error: INSUFFICIENT_FUNDS",
                    paymentAttempt.getSubscriptionId());
            return new PaymentResult.Failed("INSUFFICIENT_FUNDS", "Payment was rejected by the provider");
        } else {
            log.warn("Payment TIMEOUT for subscription {}.",
                    paymentAttempt.getSubscriptionId());
            return new PaymentResult.Failed("GATEWAY_TIMEOUT", "Payment gateway did not respond in time");
        }
    }

    /**
     * Generates an idempotency key in the format: subscription:{subscriptionId}:billing-cycle:{expirationDate}
     *
     * @param subscriptionId the subscription UUID
     * @param expirationDate the expiration date of the current billing cycle
     * @return the formatted idempotency key
     */
    public static String generateIdempotencyKey(UUID subscriptionId, LocalDate expirationDate) {
        return String.format("subscription:%s:billing-cycle:%s", subscriptionId, expirationDate);
    }

    private void simulateProcessingDelay() {
        if (maxDelayMs <= 0) {
            return;
        }
        try {
            long delay = minDelayMs + random.nextLong(Math.max(1, maxDelayMs - minDelayMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Processing delay interrupted");
        }
    }
}
