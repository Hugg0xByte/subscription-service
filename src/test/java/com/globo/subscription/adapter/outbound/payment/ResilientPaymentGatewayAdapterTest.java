package com.globo.subscription.adapter.outbound.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.globo.subscription.application.port.PaymentResult;
import com.globo.subscription.domain.entity.PaymentAttempt;
import com.globo.subscription.domain.enums.PaymentAttemptStatus;
import com.globo.subscription.domain.vo.Money;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

class ResilientPaymentGatewayAdapterTest {

    private MockPaymentGatewayAdapter mockDelegate;
    private ResilientPaymentGatewayAdapter adapter;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(MockPaymentGatewayAdapter.class);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .failureRateThreshold(100)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100)) // short for tests
                .retryExceptions(RuntimeException.class)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(tlConfig);

        adapter = new ResilientPaymentGatewayAdapter(
                mockDelegate,
                circuitBreakerRegistry,
                retryRegistry,
                timeLimiterRegistry
        );
    }

    @Test
    @DisplayName("Should return PaymentResult.Approved when delegate succeeds")
    void shouldReturnApprovedWhenDelegateSucceeds() {
        PaymentAttempt attempt = createPaymentAttempt();
        when(mockDelegate.processPayment(any())).thenReturn(new PaymentResult.Approved("txn-123"));

        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isInstanceOf(PaymentResult.Approved.class);
        assertThat(((PaymentResult.Approved) result).providerTransactionId()).isEqualTo("txn-123");
        verify(mockDelegate, times(1)).processPayment(attempt);
    }

    @Test
    @DisplayName("Should return PaymentResult.Failed when delegate returns Failed")
    void shouldReturnFailedWhenDelegateReturnsFailed() {
        PaymentAttempt attempt = createPaymentAttempt();
        when(mockDelegate.processPayment(any()))
                .thenReturn(new PaymentResult.Failed("INSUFFICIENT_FUNDS", "Not enough balance"));

        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isInstanceOf(PaymentResult.Failed.class);
        PaymentResult.Failed failed = (PaymentResult.Failed) result;
        assertThat(failed.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("Should retry up to 3 times on RuntimeException and return Failed")
    void shouldRetryAndReturnFailedOnExhaustedRetries() {
        PaymentAttempt attempt = createPaymentAttempt();
        when(mockDelegate.processPayment(any()))
                .thenThrow(new RuntimeException("Connection refused"));

        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isInstanceOf(PaymentResult.Failed.class);
        PaymentResult.Failed failed = (PaymentResult.Failed) result;
        assertThat(failed.errorCode()).isNotBlank();
        // 3 attempts (max retries)
        verify(mockDelegate, times(3)).processPayment(attempt);
    }

    @Test
    @DisplayName("Should succeed on second attempt after first failure")
    void shouldSucceedOnRetry() {
        PaymentAttempt attempt = createPaymentAttempt();
        when(mockDelegate.processPayment(any()))
                .thenThrow(new RuntimeException("Temporary error"))
                .thenReturn(new PaymentResult.Approved("txn-retry-success"));

        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isInstanceOf(PaymentResult.Approved.class);
        assertThat(((PaymentResult.Approved) result).providerTransactionId()).isEqualTo("txn-retry-success");
        verify(mockDelegate, times(2)).processPayment(attempt);
    }

    @Test
    @DisplayName("Should return Failed when circuit breaker is open")
    void shouldReturnFailedWhenCircuitBreakerIsOpen() {
        PaymentAttempt attempt = createPaymentAttempt();

        // Force the circuit breaker to open by recording failures
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentGateway");
        cb.transitionToOpenState();

        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isInstanceOf(PaymentResult.Failed.class);
        PaymentResult.Failed failed = (PaymentResult.Failed) result;
        assertThat(failed.errorCode()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        // Delegate should NOT be called when circuit is open
        verify(mockDelegate, never()).processPayment(any());
    }

    @Test
    @DisplayName("Should never throw unhandled exceptions to caller")
    void shouldNeverThrowExceptions() {
        PaymentAttempt attempt = createPaymentAttempt();
        when(mockDelegate.processPayment(any()))
                .thenThrow(new RuntimeException("Catastrophic failure"));

        // Should not throw - always returns PaymentResult
        PaymentResult result = adapter.processPayment(attempt);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(PaymentResult.Failed.class);
    }

    private PaymentAttempt createPaymentAttempt() {
        return new PaymentAttempt(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(BigDecimal.valueOf(39.90), "BRL"),
                PaymentAttemptStatus.PROCESSING,
                1,
                "subscription:" + UUID.randomUUID() + ":billing-cycle:2025-02-15",
                null,
                null,
                null,
                Instant.now(),
                null
        );
    }
}
