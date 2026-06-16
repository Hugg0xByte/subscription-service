package com.globo.subscription.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for PaymentResultMessage serialization round-trip.
 *
 * <p><b>Validates: Requirements 8.5</b></p>
 *
 * <p>Property 3: PaymentResultMessage serialization round-trip —
 * For all valid PaymentResultMessage objects (both APPROVED and FAILED variants),
 * serializing to JSON and deserializing back SHALL produce an equivalent object.</p>
 */
class PaymentResultMessageSerializationPropertyTest {

    private final ObjectMapper objectMapper;

    PaymentResultMessageSerializationPropertyTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Property
    void serializationRoundTripProducesEquivalentObject(
            @ForAll("validPaymentResultMessages") PaymentResultMessage original) throws Exception {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back
        PaymentResultMessage deserialized = objectMapper.readValue(json, PaymentResultMessage.class);

        // Assert equivalence
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(deserialized.userId()).isEqualTo(original.userId());
        assertThat(deserialized.status()).isEqualTo(original.status());
        assertThat(deserialized.providerTransactionId()).isEqualTo(original.providerTransactionId());
        assertThat(deserialized.errorCode()).isEqualTo(original.errorCode());
        assertThat(deserialized.errorMessage()).isEqualTo(original.errorMessage());
        assertThat(deserialized.idempotencyKey()).isEqualTo(original.idempotencyKey());
        assertThat(deserialized.processedAt()).isEqualTo(original.processedAt());
    }

    @Provide
    Arbitrary<PaymentResultMessage> validPaymentResultMessages() {
        Arbitrary<PaymentResultMessage> approvedMessages = approvedVariant();
        Arbitrary<PaymentResultMessage> failedMessages = failedVariant();

        return Arbitraries.oneOf(approvedMessages, failedMessages);
    }

    private Arbitrary<PaymentResultMessage> approvedVariant() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> providerTransactionIds = Arbitraries.create(UUID::randomUUID)
                .map(UUID::toString);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-01");
        Arbitrary<Instant> processedAts = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, providerTransactionIds, idempotencyKeys, processedAts)
                .as((subscriptionId, userId, providerTransactionId, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId,
                                userId,
                                PaymentResultMessage.PaymentStatus.APPROVED,
                                providerTransactionId,
                                null,
                                null,
                                idempotencyKey,
                                processedAt
                        )
                );
    }

    private Arbitrary<PaymentResultMessage> failedVariant() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> errorCodes = Arbitraries.of(
                "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "NETWORK_ERROR", "FRAUD_DETECTED");
        Arbitrary<String> errorMessages = Arbitraries.of(
                "Payment declined due to insufficient funds",
                "Card has expired",
                "Network timeout during processing",
                "Transaction flagged as potentially fraudulent");
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-01");
        Arbitrary<Instant> processedAts = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, errorCodes, errorMessages, idempotencyKeys, processedAts)
                .as((subscriptionId, userId, errorCode, errorMessage, idempotencyKey, processedAt) ->
                        new PaymentResultMessage(
                                subscriptionId,
                                userId,
                                PaymentResultMessage.PaymentStatus.FAILED,
                                null,
                                errorCode,
                                errorMessage,
                                idempotencyKey,
                                processedAt
                        )
                );
    }
}
