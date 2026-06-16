package com.globo.subscription.adapter.outbound.cache;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

/**
 * Property-based tests for cache put-get-evict round-trip behavior.
 *
 * <p><b>Validates: Requirements 8.3, 8.4, 8.5, 8.6</b></p>
 *
 * <p>Property 12: Cache put-get-evict round-trip —
 * For any UUID userId and valid Subscription, (a) after putActiveSubscription(userId, subscription),
 * getActiveSubscription(userId) SHALL return the subscription; (b) after evictActiveSubscription(userId),
 * getActiveSubscription(userId) SHALL return empty.
 * Similarly for PlanCachePort: putAll→getAll returns plans, evict→getAll returns empty.</p>
 */
class CacheRoundTripPropertyTest {

    // --- Subscription Cache Tests ---

    @Property
    void subscriptionCache_putThenGet_shouldReturnSubscription(
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitrarySubscriptions") Subscription subscription) {

        var cache = new CaffeineSubscriptionCacheAdapter(5, 10000, new SimpleMeterRegistry());

        cache.putActiveSubscription(userId, subscription);
        Optional<Subscription> result = cache.getActiveSubscription(userId);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(subscription);
    }

    @Property
    void subscriptionCache_evictThenGet_shouldReturnEmpty(
            @ForAll("arbitraryUuids") UUID userId,
            @ForAll("arbitrarySubscriptions") Subscription subscription) {

        var cache = new CaffeineSubscriptionCacheAdapter(5, 10000, new SimpleMeterRegistry());

        cache.putActiveSubscription(userId, subscription);
        cache.evictActiveSubscription(userId);
        Optional<Subscription> result = cache.getActiveSubscription(userId);

        assertThat(result).isEmpty();
    }

    @Property
    void subscriptionCache_getWithoutPut_shouldReturnEmpty(
            @ForAll("arbitraryUuids") UUID userId) {

        var cache = new CaffeineSubscriptionCacheAdapter(5, 10000, new SimpleMeterRegistry());

        Optional<Subscription> result = cache.getActiveSubscription(userId);

        assertThat(result).isEmpty();
    }

    // --- Plan Cache Tests ---

    @Property
    void planCache_putAllThenGetAll_shouldReturnPlans(
            @ForAll("arbitraryPlanLists") List<Plan> plans) {

        var cache = new CaffeinePlanCacheAdapter(60, 100, new SimpleMeterRegistry());

        cache.putAllActivePlans(plans);
        Optional<List<Plan>> result = cache.getAllActivePlans();

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(plans);
    }

    @Property
    void planCache_evictThenGetAll_shouldReturnEmpty(
            @ForAll("arbitraryPlanLists") List<Plan> plans) {

        var cache = new CaffeinePlanCacheAdapter(60, 100, new SimpleMeterRegistry());

        cache.putAllActivePlans(plans);
        cache.evictAllPlans();
        Optional<List<Plan>> result = cache.getAllActivePlans();

        assertThat(result).isEmpty();
    }

    @Property
    void planCache_getAllWithoutPut_shouldReturnEmpty() {
        var cache = new CaffeinePlanCacheAdapter(60, 100, new SimpleMeterRegistry());

        Optional<List<Plan>> result = cache.getAllActivePlans();

        assertThat(result).isEmpty();
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<UUID> arbitraryUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Subscription> arbitrarySubscriptions() {
        return Arbitraries.create(() -> {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            Money price = new Money(BigDecimal.valueOf(19.90), "BRL");
            SubscriptionStatus status = SubscriptionStatus.ATIVA;
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate expirationDate = LocalDate.of(2024, 2, 1);
            Instant now = Instant.now();
            return new Subscription(id, userId, planId, price, status, startDate, expirationDate,
                    null, null, 0, 0L, now, now);
        });
    }

    @Provide
    Arbitrary<Plan> arbitraryPlans() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.of("BASICO", "PREMIUM", "FAMILIA");
        Arbitrary<String> displayNames = Arbitraries.of("Básico", "Premium", "Família");
        Arbitrary<Money> prices = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, new BigDecimal("999.99"))
                .map(amount -> new Money(amount, "BRL"));
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_800_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(ids, names, displayNames, prices, instants)
                .as((id, name, displayName, price, createdAt) ->
                        new Plan(id, name, displayName, price, true, createdAt));
    }

    @Provide
    Arbitrary<List<Plan>> arbitraryPlanLists() {
        return arbitraryPlans().list().ofMinSize(1).ofMaxSize(5);
    }
}
