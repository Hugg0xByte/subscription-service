package com.globo.subscription.application.port;

/**
 * Sealed interface representing the outcome of a payment processing attempt.
 * Used by PaymentGatewayPort to communicate results without throwing exceptions.
 */
public sealed interface PaymentResult {

    record Approved(String providerTransactionId) implements PaymentResult {}

    record Failed(String errorCode, String errorMessage) implements PaymentResult {}
}
