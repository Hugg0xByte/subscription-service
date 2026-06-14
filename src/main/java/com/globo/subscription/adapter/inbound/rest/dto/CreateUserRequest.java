package com.globo.subscription.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new user.
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {}
