package com.globo.subscription.application.exception;

/**
 * Thrown when attempting to create a user with an email that is already registered.
 */
public class EmailAlreadyExistsException extends DomainException {

    private static final String ERROR_CODE = "EMAIL_ALREADY_EXISTS";

    public EmailAlreadyExistsException(String email) {
        super(ERROR_CODE, "A user with email '" + email + "' already exists");
    }
}
