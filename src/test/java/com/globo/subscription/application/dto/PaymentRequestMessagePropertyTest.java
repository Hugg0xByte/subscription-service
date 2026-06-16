package com.globo.subscription.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for PaymentRequestMessage construction correctness.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3</b></p>
 *
 * <p>Property 1: PaymentRequestMessage construction correctness —
 * For any valid combination of UUID, BigDecimal, String, int, and Instant values,
 * constructing a PaymentRequestMessage SHALL preserve all input fields exactly,
 * and messageId and timestamp SHALL be non-null.</p>
 */
class PaymentRequestMessagePropertyTest {

    @Property
    void allFieldsShouldBePreservedAfterConstruction(
            @ForAll("arbitraryUuids") UUID messageId,
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitraryUuids") UUID planId,
            @ForAll @BigRange(min = "0.01", max = "99999.99") BigDecimal amount,
            @ForAll("validCurrencies") String currency,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
            @ForAll("validIdempotencyKeys") String idempotencyKey,
            @ForAll("arbitraryInstants") Instant timestamp) {

        PaymentRequestMessage message = new PaymentRequestMessage(
                messageId, subscriptionId, userId, planId,
                amount, currency, attemptNumber, idempotencyKey, timestamp
        );

        assertThat(message.messageId()).isEqualTo(messageId);
        assertThat(message.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(message.userId()).isEqualTo(userId);
        assertThat(message.planId()).isEqualTo(planId);
        assertThat(message.amount()).isEqualByComparingTo(amount);
        assertThat(message.currency()).isEqualTo(currency);
        assertThat(message.attemptNumber()).isEqualTo(attemptNumber);
        assertThat(message.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(message.timestamp()).isEqualTo(timestamp);
    }

    @Property
    void messageIdShouldNeverBeNull(
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitraryUuids") UUID planId,
            @ForAll @BigRange(min = "0.01", max = "99999.99") BigDecimal amount,
            @ForAll("validCurrencies") String currency,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
            @ForAll("validIdempotencyKeys") String idempotencyKey,
            @ForAll("arbitraryInstants") Instant timestamp) {

        UUID messageId = UUID.randomUUID();

        PaymentRequestMessage message = new PaymentRequestMessage(
                messageId, subscriptionId, userId, planId,
                amount, currency, attemptNumber, idempotencyKey, timestamp
        );

        assertThat(message.messageId()).isNotNull();
    }

    @Property
    void timestampShouldNeverBeNull(
            @ForAll("arbitraryUuids") UUID messageId,
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitraryUuids") UUID planId,
            @ForAll @BigRange(min = "0.01", max = "99999.99") BigDecimal amount,
            @ForAll("validCurrencies") String currency,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
            @ForAll("validIdempotencyKeys") String idempotencyKey,
            @ForAll("arbitraryInstants") Instant timestamp) {

        PaymentRequestMessage message = new PaymentRequestMessage(
                messageId, subscriptionId, userId, planId,
                amount, currency, attemptNumber, idempotencyKey, timestamp
        );

        assertThat(message.timestamp()).isNotNull();
    }

    @Property
    void attemptNumberShouldBePreserved(
            @ForAll("arbitraryUuids") UUID messageId,
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitraryUuids") UUID planId,
            @ForAll @BigRange(min = "0.01", max = "99999.99") BigDecimal amount,
            @ForAll("validCurrencies") String currency,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
            @ForAll("validIdempotencyKeys") String idempotencyKey,
            @ForAll("arbitraryInstants") Instant timestamp) {

        PaymentRequestMessage message = new PaymentRequestMessage(
                messageId, subscriptionId, userId, planId,
                amount, currency, attemptNumber, idempotencyKey, timestamp
        );

        assertThat(message.attemptNumber()).isEqualTo(attemptNumber);
    }

    @Provide
    Arbitrary<UUID> arbitraryUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of("BRL", "USD", "EUR");
    }

    @Provide
    Arbitrary<String> validIdempotencyKeys() {
        return Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-15");
    }

    @Provide
    Arbitrary<Instant> arbitraryInstants() {
        return Arbitraries.longs()
                .between(0L, 4_102_444_800L) // from epoch to ~2100
                .map(Instant::ofEpochSecond);
    }
}
