package com.globo.subscription.application.exception;

import java.util.UUID;

/**
 * Thrown when a user cannot be found by their identifier.
 */
public class UserNotFoundException extends DomainException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException(UUID userId) {
        super(ERROR_CODE, "User not found: " + userId);
    }
}
