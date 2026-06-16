package com.globo.subscription.application.exception;

import java.util.UUID;

/**
 * Thrown when attempting to create a subscription for a user who already has an active one.
 */
public class ActiveSubscriptionExistsException extends DomainException {

    private static final String ERROR_CODE = "SUBSCRIPTION_ALREADY_ACTIVE";

    public ActiveSubscriptionExistsException(UUID userId) {
        super(ERROR_CODE, "An active subscription already exists for user: " + userId);
    }
}
