package com.globo.subscription.adapter.outbound.persistence.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionJpaEntity;
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
 * Property-based tests for Subscription serialization round-trip.
 *
 * <p><b>Validates: Requirements 2.16</b></p>
 *
 * <p>Property 6: Subscription serialization round-trip —
 * For any valid Subscription entity state (with any combination of valid status, dates,
 * failedAttempts, and version), serializing the entity to its persistence representation
 * and deserializing back SHALL produce an entity equal to the original across all fields.</p>
 */
class SubscriptionSerializationPropertyTest {

    private final SubscriptionPersistenceMapper mapper = Mappers.getMapper(SubscriptionPersistenceMapper.class);

    @Property
    void domainToJpaToDomainPreservesAllFields(
            @ForAll("arbitrarySubscriptions") Subscription original) {
        // Domain → JPA
        SubscriptionJpaEntity jpaEntity = mapper.toJpaEntity(original);

        // JPA → Domain
        Subscription roundTripped = mapper.toDomainEntity(jpaEntity);

        // Verify all fields are preserved
        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getPlanId()).isEqualTo(original.getPlanId());
        assertThat(roundTripped.getPriceAtPurchase().amount())
                .isEqualByComparingTo(original.getPriceAtPurchase().amount());
        assertThat(roundTripped.getPriceAtPurchase().currency())
                .isEqualTo(original.getPriceAtPurchase().currency());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getStartDate()).isEqualTo(original.getStartDate());
        assertThat(roundTripped.getExpirationDate()).isEqualTo(original.getExpirationDate());
        assertThat(roundTripped.getCancelRequestedAt()).isEqualTo(original.getCancelRequestedAt());
        assertThat(roundTripped.getSuspendedAt()).isEqualTo(original.getSuspendedAt());
        assertThat(roundTripped.getFailedAttempts()).isEqualTo(original.getFailedAttempts());
        assertThat(roundTripped.getVersion()).isEqualTo(original.getVersion());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Property
    void jpaEntityFieldsMatchDomainAfterSerialization(
            @ForAll("arbitrarySubscriptions") Subscription original) {
        // Domain → JPA: verify JPA entity fields are correctly decomposed
        SubscriptionJpaEntity jpaEntity = mapper.toJpaEntity(original);

        assertThat(jpaEntity.getId()).isEqualTo(original.getId());
        assertThat(jpaEntity.getUserId()).isEqualTo(original.getUserId());
        assertThat(jpaEntity.getPlanId()).isEqualTo(original.getPlanId());
        assertThat(jpaEntity.getPriceAtPurchase())
                .isEqualByComparingTo(original.getPriceAtPurchase().amount());
        assertThat(jpaEntity.getCurrencyAtPurchase())
                .isEqualTo(original.getPriceAtPurchase().currency());
        assertThat(jpaEntity.getStatus()).isEqualTo(original.getStatus());
        assertThat(jpaEntity.getStartDate()).isEqualTo(original.getStartDate());
        assertThat(jpaEntity.getExpirationDate()).isEqualTo(original.getExpirationDate());
        assertThat(jpaEntity.getCancelRequestedAt()).isEqualTo(original.getCancelRequestedAt());
        assertThat(jpaEntity.getSuspendedAt()).isEqualTo(original.getSuspendedAt());
        assertThat(jpaEntity.getFailedAttempts()).isEqualTo(original.getFailedAttempts());
        assertThat(jpaEntity.getVersion()).isEqualTo(original.getVersion());
        assertThat(jpaEntity.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(jpaEntity.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
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
}
