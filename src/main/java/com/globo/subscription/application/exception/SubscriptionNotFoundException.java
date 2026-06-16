package com.globo.subscription.application.exception;

import java.util.UUID;

/**
 * Thrown when a subscription cannot be found by its identifier.
 */
public class SubscriptionNotFoundException extends DomainException {

    private static final String ERROR_CODE = "SUBSCRIPTION_NOT_FOUND";

    public SubscriptionNotFoundException(UUID subscriptionId) {
        super(ERROR_CODE, "Subscription not found: " + subscriptionId);
    }
}
