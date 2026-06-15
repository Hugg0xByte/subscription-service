package com.globo.subscription.adapter.outbound.payment;

import java.time.LocalDate;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for idempotency key deterministic generation.
 *
 * <p><b>Validates: Requirements 6.8</b></p>
 *
 * <p>Property 11: Idempotency key deterministic generation —
 * For any subscription UUID and expiration date, the generated idempotency key
 * SHALL equal exactly "subscription:{subscriptionId}:billing-cycle:{expirationDate}"
 * and the same inputs SHALL always produce the same output (deterministic).</p>
 */
class IdempotencyKeyPropertyTest {

    @Property
    void idempotencyKeyShouldMatchExpectedFormat(
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryDates") LocalDate expirationDate) {

        String key = MockPaymentGatewayAdapter.generateIdempotencyKey(subscriptionId, expirationDate);

        String expectedKey = "subscription:" + subscriptionId + ":billing-cycle:" + expirationDate;
        assertThat(key).isEqualTo(expectedKey);
    }

    @Property
    void sameInputsShouldAlwaysProduceSameOutput(
            @ForAll("arbitraryUuids") UUID subscriptionId,
            @ForAll("arbitraryDates") LocalDate expirationDate) {

        String key1 = MockPaymentGatewayAdapter.generateIdempotencyKey(subscriptionId, expirationDate);
        String key2 = MockPaymentGatewayAdapter.generateIdempotencyKey(subscriptionId, expirationDate);

        assertThat(key1).isEqualTo(key2);
    }

    @Provide
    Arbitrary<UUID> arbitraryUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<LocalDate> arbitraryDates() {
        return Arbitraries.integers()
                .between(0, 730)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
    }
}
