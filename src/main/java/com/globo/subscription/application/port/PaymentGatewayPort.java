package com.globo.subscription.application.port;

import com.globo.subscription.domain.entity.PaymentAttempt;

/**
 * Port interface for payment gateway operations.
 * Implemented by outbound adapters (e.g., MockPaymentGatewayAdapter).
 */
public interface PaymentGatewayPort {

    PaymentResult processPayment(PaymentAttempt paymentAttempt);
}
