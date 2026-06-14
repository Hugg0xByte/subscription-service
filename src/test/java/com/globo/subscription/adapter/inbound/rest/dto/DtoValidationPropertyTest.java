package com.globo.subscription.adapter.inbound.rest.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for DTO validation rejects invalid input.
 *
 * <p><b>Validates: Requirements 5.3</b></p>
 *
 * <p>Property 9: DTO validation rejects invalid input —
 * For any request DTO with at least one invalid field (null @NotNull field, blank @NotBlank field,
 * malformed @Email field), Bean Validation SHALL produce at least one constraint violation.
 * Conversely, for any DTO with all fields valid, validation SHALL produce zero violations.</p>
 */
class DtoValidationPropertyTest {

    private static final Validator validator;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // --- CreateSubscriptionRequest Tests ---

    @Property
    void createSubscriptionRequestWithNullUserIdShouldHaveViolations(
            @ForAll("arbitraryUuids") UUID planId) {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(null, planId);

        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Property
    void createSubscriptionRequestWithNullPlanIdShouldHaveViolations(
            @ForAll("arbitraryUuids") UUID userId) {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(userId, null);

        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Property
    void createSubscriptionRequestWithBothFieldsNullShouldHaveViolations() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(null, null);

        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Property
    void createSubscriptionRequestWithValidFieldsShouldHaveNoViolations(
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitraryUuids") UUID planId) {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(userId, planId);

        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    // --- CreateUserRequest Tests ---

    @Property
    void createUserRequestWithBlankNameShouldHaveViolations(
            @ForAll("blankStrings") String name,
            @ForAll("validEmails") String email) {
        CreateUserRequest request = new CreateUserRequest(name, email);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Property
    void createUserRequestWithBlankEmailShouldHaveViolations(
            @ForAll("validNames") String name,
            @ForAll("blankStrings") String blankEmail) {
        CreateUserRequest request = new CreateUserRequest(name, blankEmail);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Property
    void createUserRequestWithMalformedEmailShouldHaveViolations(
            @ForAll("validNames") String name,
            @ForAll("invalidEmails") String invalidEmail) {
        CreateUserRequest request = new CreateUserRequest(name, invalidEmail);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Property
    void createUserRequestWithValidFieldsShouldHaveNoViolations(
            @ForAll("validNames") String name,
            @ForAll("validEmails") String email) {
        CreateUserRequest request = new CreateUserRequest(name, email);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    // --- Providers ---

    @Provide
    Arbitrary<UUID> arbitraryUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", null);
    }

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> localParts = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10)
                .map(String::toLowerCase);
        Arbitrary<String> domains = Arbitraries.of("gmail.com", "email.com", "test.org", "example.net");

        return Combinators.combine(localParts, domains).as((local, domain) -> local + "@" + domain);
    }

    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.of(
                "notanemail",
                "missing@",
                "@nodomain",
                "spaces in@email.com",
                "double@@at.com"
        );
    }
}
