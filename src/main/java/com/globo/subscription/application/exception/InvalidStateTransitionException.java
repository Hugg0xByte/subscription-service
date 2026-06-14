package com.globo.subscription.application.exception;

/**
 * Thrown when an invalid state transition is attempted on a domain entity.
 */
public class InvalidStateTransitionException extends DomainException {

    private static final String ERROR_CODE = "INVALID_STATE_TRANSITION";

    public InvalidStateTransitionException(String currentState, String attemptedOperation) {
        super(ERROR_CODE, "Cannot perform '" + attemptedOperation + "' on subscription with status: " + currentState);
    }
}
