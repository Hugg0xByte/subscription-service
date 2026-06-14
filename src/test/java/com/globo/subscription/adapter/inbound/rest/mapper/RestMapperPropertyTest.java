package com.globo.subscription.adapter.inbound.rest.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.adapter.inbound.rest.dto.SubscriptionResponse;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for REST mapper field preservation.
 *
 * <p><b>Validates: Requirements 5.10</b></p>
 *
 * <p>Property 10: REST mapper field preservation —
 * For any valid Subscription domain entity, mapping it to a SubscriptionResponse DTO
 * SHALL preserve all exposed fields (id, userId, planName, priceAtPurchase, currency,
 * status name, startDate, expirationDate, createdAt) with exact value equality.</p>
 */
class RestMapperPropertyTest {

    private final SubscriptionRestMapper mapper = Mappers.getMapper(SubscriptionRestMapper.class);

    @Property
    void mappingPreservesAllExposedFields(
            @ForAll("arbitrarySubscriptions") Subscription subscription,
            @ForAll("arbitraryPlans") Plan plan) {

        SubscriptionResponse response = mapper.toResponse(subscription, plan);

        assertThat(response.id()).isEqualTo(subscription.getId());
        assertThat(response.userId()).isEqualTo(subscription.getUserId());
        assertThat(response.planName()).isEqualTo(plan.getName());
        assertThat(response.priceAtPurchase())
                .isEqualByComparingTo(subscription.getPriceAtPurchase().amount());
        assertThat(response.currency()).isEqualTo(subscription.getPriceAtPurchase().currency());
        assertThat(response.status()).isEqualTo(subscription.getStatus().name());
        assertThat(response.startDate()).isEqualTo(subscription.getStartDate());
        assertThat(response.expirationDate()).isEqualTo(subscription.getExpirationDate());
        assertThat(response.createdAt()).isEqualTo(subscription.getCreatedAt());
    }

    // --- Providers ---

    @Provide
    Arbitrary<Subscription> arbitrarySubscriptions() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<SubscriptionStatus> statuses = Arbitraries.of(SubscriptionStatus.values());
        Arbitrary<Money> moneys = Combinators.combine(
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("99999.99")),
                Arbitraries.of("BRL", "USD", "EUR")
        ).as(Money::new);
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 730)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
        Arbitrary<Integer> failedAttemptValues = Arbitraries.integers().between(0, 5);
        Arbitrary<Long> versions = Arbitraries.longs().between(0L, 1000L);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);
        Arbitrary<Instant> nullableInstants = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, instants),
                net.jqwik.api.Tuple.of(2, Arbitraries.just(null))
        );

        return Combinators.combine(uuids, uuids, uuids, moneys, statuses, dates, dates, nullableInstants)
                .flatAs((id, userId, planId, price, status, startDate, expirationDate, cancelRequestedAt) ->
                        Combinators.combine(nullableInstants, failedAttemptValues, versions, instants, instants)
                                .as((suspendedAt, failedAttempts, version, createdAt, updatedAt) ->
                                        new Subscription(
                                                id, userId, planId, price,
                                                status,
                                                startDate, expirationDate,
                                                cancelRequestedAt, suspendedAt,
                                                failedAttempts,
                                                version,
                                                createdAt, updatedAt))
                );
    }

    @Provide
    Arbitrary<Plan> arbitraryPlans() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.of("BASICO", "PREMIUM", "FAMILIA");
        Arbitrary<String> displayNames = Arbitraries.of("Básico", "Premium", "Família");
        Arbitrary<Money> moneys = Combinators.combine(
                Arbitraries.bigDecimals().between(BigDecimal.ONE, new BigDecimal("999.99")),
                Arbitraries.of("BRL", "USD", "EUR")
        ).as(Money::new);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, names, displayNames, moneys, Arbitraries.of(true, false), instants)
                .as(Plan::new);
    }
}
