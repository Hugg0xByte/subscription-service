package com.globo.subscription.application.exception;

import java.util.UUID;

/**
 * Thrown when a plan cannot be found by its identifier.
 */
public class PlanNotFoundException extends DomainException {

    private static final String ERROR_CODE = "PLAN_NOT_FOUND";

    public PlanNotFoundException(UUID planId) {
        super(ERROR_CODE, "Plan not found: " + planId);
    }
}
