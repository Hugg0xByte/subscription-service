package com.globo.subscription.application.dto;

import java.math.BigDecimal;
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
 * Property-based test for PaymentRequestMessage serialization round-trip.
 *
 * <p><b>Validates: Requirements 8.4</b></p>
 *
 * <p>Property 2: PaymentRequestMessage serialization round-trip —
 * For all valid PaymentRequestMessage objects, serializing to JSON and deserializing
 * back SHALL produce an equivalent object.</p>
 */
class PaymentRequestMessageSerializationPropertyTest {

    private final ObjectMapper objectMapper;

    PaymentRequestMessageSerializationPropertyTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Property
    void serializationRoundTripProducesEquivalentObject(
            @ForAll("validPaymentRequestMessages") PaymentRequestMessage original) throws Exception {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back
        PaymentRequestMessage deserialized = objectMapper.readValue(json, PaymentRequestMessage.class);

        // Assert equivalence
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.messageId()).isEqualTo(original.messageId());
        assertThat(deserialized.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(deserialized.userId()).isEqualTo(original.userId());
        assertThat(deserialized.planId()).isEqualTo(original.planId());
        assertThat(deserialized.amount()).isEqualByComparingTo(original.amount());
        assertThat(deserialized.currency()).isEqualTo(original.currency());
        assertThat(deserialized.attemptNumber()).isEqualTo(original.attemptNumber());
        assertThat(deserialized.idempotencyKey()).isEqualTo(original.idempotencyKey());
        assertThat(deserialized.timestamp()).isEqualTo(original.timestamp());
    }

    @Provide
    Arbitrary<PaymentRequestMessage> validPaymentRequestMessages() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("99999.99"))
                .ofScale(2);
        Arbitrary<String> currencies = Arbitraries.of("BRL", "USD", "EUR");
        Arbitrary<Integer> attemptNumbers = Arbitraries.integers().between(1, 5);
        Arbitrary<String> idempotencyKeys = Arbitraries.create(UUID::randomUUID)
                .map(uuid -> "subscription:" + uuid + ":billing-cycle:2024-01-01");
        Arbitrary<Instant> timestamps = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, uuids, uuids, amounts, currencies, attemptNumbers, idempotencyKeys)
                .flatAs((messageId, subscriptionId, userId, planId, amount, currency, attemptNumber, idempotencyKey) ->
                        timestamps.map(timestamp ->
                                new PaymentRequestMessage(
                                        messageId,
                                        subscriptionId,
                                        userId,
                                        planId,
                                        amount,
                                        currency,
                                        attemptNumber,
                                        idempotencyKey,
                                        timestamp
                                )
                        )
                );
    }
}
