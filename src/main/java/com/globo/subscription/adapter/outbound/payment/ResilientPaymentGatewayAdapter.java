package com.globo.subscription.adapter.outbound.payment;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.globo.subscription.application.port.PaymentGatewayPort;
import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.domain.entity.PaymentAttempt;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Resilient wrapper around the PaymentGatewayPort that applies Resilience4j patterns.
 * <p>
 * Call stack: Retry(maxAttempts=3, exponential backoff) → CircuitBreaker(failureThreshold=5) → Timeout(10s) → delegate.processPayment
 * <p>
 * On exhausted retries or open circuit, returns PaymentResult.Failed (never throws unchecked exceptions to use case).
 */
@Component
@Primary
public class ResilientPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentGatewayAdapter.class);

    private static final String INSTANCE_NAME = "paymentGateway";

    private final PaymentGatewayPort delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;

    public ResilientPaymentGatewayAdapter(
            MockPaymentGatewayAdapter delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
        this.retry = retryRegistry.retry(INSTANCE_NAME);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(INSTANCE_NAME);
    }

    @Override
    public PaymentResult processPayment(PaymentAttempt paymentAttempt) {
        log.debug("Processing payment with resilience patterns for subscription {}",
                paymentAttempt.getSubscriptionId());

        try {
            // Build the decorated call: Retry → CircuitBreaker → Timeout → delegate
            Callable<PaymentResult> timeLimitedCall = TimeLimiter.decorateFutureSupplier(
                    timeLimiter,
                    () -> CompletableFuture.supplyAsync(() -> delegate.processPayment(paymentAttempt))
            );

            Callable<PaymentResult> circuitBreakerCall = CircuitBreaker.decorateCallable(
                    circuitBreaker,
                    timeLimitedCall
            );

            Callable<PaymentResult> retryCall = Retry.decorateCallable(
                    retry,
                    circuitBreakerCall
            );

            return retryCall.call();

        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN for payment gateway. Subscription: {}",
                    paymentAttempt.getSubscriptionId());
            return new PaymentResult.Failed("CIRCUIT_BREAKER_OPEN",
                    "Payment gateway circuit breaker is open, request rejected");

        } catch (TimeoutException e) {
            log.warn("Payment request timed out after all retries for subscription {}",
                    paymentAttempt.getSubscriptionId());
            return new PaymentResult.Failed("GATEWAY_TIMEOUT",
                    "Payment gateway timed out after all retry attempts");

        } catch (ExecutionException e) {
            log.warn("Payment processing failed after all retries for subscription {}: {}",
                    paymentAttempt.getSubscriptionId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return new PaymentResult.Failed("PAYMENT_PROCESSING_ERROR",
                    "Payment processing failed after all retry attempts: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during payment processing for subscription {}: {}",
                    paymentAttempt.getSubscriptionId(), e.getMessage(), e);
            return new PaymentResult.Failed("UNEXPECTED_ERROR",
                    "Unexpected error during payment processing: " + e.getMessage());
        }
    }
}
