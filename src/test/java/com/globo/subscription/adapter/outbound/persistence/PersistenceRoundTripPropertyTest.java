package com.globo.subscription.adapter.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.globo.subscription.adapter.outbound.persistence.entity.PlanJpaEntity;
import com.globo.subscription.adapter.outbound.persistence.repository.PlanJpaRepository;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.entity.User;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

import jakarta.persistence.EntityManager;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for persistence adapter round-trip.
 *
 * <p><b>Validates: Requirements 4.13</b></p>
 *
 * <p>Property 7: Persistence adapter round-trip —
 * For any valid domain entity (User or Subscription), persisting it via the repository adapter
 * and then retrieving it by ID SHALL produce an entity with all fields equal to the original.
 * This validates that the domain→JPA→domain mapper chain preserves data integrity.</p>
 *
 * <p>Each property test executes inside a transaction that is rolled back after assertions,
 * ensuring complete isolation between tries and avoiding unique constraint violations
 * or optimistic locking conflicts from accumulated data.</p>
 */
class PersistenceRoundTripPropertyTest {

    /**
     * Minimal Spring configuration that only loads persistence-related beans.
     * Avoids loading use cases, controllers, and other adapters that have unsatisfied dependencies.
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.globo.subscription.adapter.outbound.persistence.entity")
    @EnableJpaRepositories(basePackages = "com.globo.subscription.adapter.outbound.persistence.repository")
    @ComponentScan(basePackages = "com.globo.subscription.adapter.outbound.persistence")
    static class PersistenceTestConfig {
    }

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    static JpaUserRepositoryAdapter userRepositoryAdapter;
    static JpaSubscriptionRepositoryAdapter subscriptionRepositoryAdapter;
    static PlanJpaRepository planJpaRepository;
    static EntityManager entityManager;
    static TransactionTemplate transactionTemplate;
    static UUID fixedPlanId;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("subscription_db_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", postgres.getJdbcUrl());
        props.put("spring.datasource.username", postgres.getUsername());
        props.put("spring.datasource.password", postgres.getPassword());
        props.put("spring.liquibase.enabled", true);
        props.put("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml");
        props.put("spring.jpa.hibernate.ddl-auto", "none");
        props.put("spring.jpa.show-sql", false);
        props.put("server.port", "0");

        context = new SpringApplicationBuilder(PersistenceTestConfig.class)
                .properties(props)
                .run();

        userRepositoryAdapter = context.getBean(JpaUserRepositoryAdapter.class);
        subscriptionRepositoryAdapter = context.getBean(JpaSubscriptionRepositoryAdapter.class);
        planJpaRepository = context.getBean(PlanJpaRepository.class);
        entityManager = context.getBean(EntityManager.class);
        transactionTemplate = context.getBean(TransactionTemplate.class);

        // Seed plan for FK constraint on subscriptions (committed — persists across tries)
        var existingPlans = planJpaRepository.findByActiveTrue();
        if (!existingPlans.isEmpty()) {
            fixedPlanId = existingPlans.getFirst().getId();
        } else {
            PlanJpaEntity plan = new PlanJpaEntity();
            plan.setId(UUID.randomUUID());
            plan.setName("TEST_PLAN");
            plan.setDisplayName("Test Plan");
            plan.setMonthlyPrice(new BigDecimal("29.90"));
            plan.setCurrency("BRL");
            plan.setActive(true);
            plan.setCreatedAt(Instant.now());
            planJpaRepository.saveAndFlush(plan);
            fixedPlanId = plan.getId();
        }
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void userPersistenceRoundTripPreservesAllFields(@ForAll("arbitraryUsers") User original) {
        // Execute inside a transaction that rolls back — isolates each try
        transactionTemplate.executeWithoutResult(txStatus -> {
            // Persist
            userRepositoryAdapter.save(original);

            // Flush to DB and clear first-level cache to force re-read
            entityManager.flush();
            entityManager.clear();

            // Retrieve
            Optional<User> retrieved = userRepositoryAdapter.findById(original.getId());

            // Verify round-trip equality
            assertThat(retrieved).isPresent();
            User found = retrieved.get();
            assertThat(found.getId()).isEqualTo(original.getId());
            assertThat(found.getName()).isEqualTo(original.getName());
            assertThat(found.getEmail()).isEqualTo(original.getEmail());
            assertThat(found.isActive()).isEqualTo(original.isActive());
            assertThat(found.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(found.getUpdatedAt()).isEqualTo(original.getUpdatedAt());

            // Rollback to keep DB clean for next try
            txStatus.setRollbackOnly();
        });
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void subscriptionPersistenceRoundTripPreservesAllFields(
            @ForAll("arbitrarySubscriptions") Subscription original) {
        // Execute inside a transaction that rolls back — isolates each try
        transactionTemplate.executeWithoutResult(txStatus -> {
            // Ensure user exists for FK constraint
            User user = new User(
                    original.getUserId(),
                    "Test User",
                    "user-" + original.getUserId().toString().substring(0, 8) + "@test.com",
                    true,
                    Instant.ofEpochSecond(1_700_000_000L),
                    Instant.ofEpochSecond(1_700_000_000L)
            );
            userRepositoryAdapter.save(user);

            // Persist — use the returned entity as ground truth (JPA @Version increments on save)
            Subscription saved = subscriptionRepositoryAdapter.save(original);

            // Flush to DB and clear first-level cache to force re-read
            entityManager.flush();
            entityManager.clear();

            // Retrieve
            Optional<Subscription> retrieved = subscriptionRepositoryAdapter.findById(original.getId());

            // Verify round-trip: save() result == findById() result
            assertThat(retrieved).isPresent();
            Subscription found = retrieved.get();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getUserId()).isEqualTo(saved.getUserId());
            assertThat(found.getPlanId()).isEqualTo(saved.getPlanId());
            assertThat(found.getPriceAtPurchase().amount())
                    .isEqualByComparingTo(saved.getPriceAtPurchase().amount());
            assertThat(found.getPriceAtPurchase().currency())
                    .isEqualTo(saved.getPriceAtPurchase().currency());
            assertThat(found.getStatus()).isEqualTo(saved.getStatus());
            assertThat(found.getStartDate()).isEqualTo(saved.getStartDate());
            assertThat(found.getExpirationDate()).isEqualTo(saved.getExpirationDate());
            assertThat(found.getCancelRequestedAt()).isEqualTo(saved.getCancelRequestedAt());
            assertThat(found.getSuspendedAt()).isEqualTo(saved.getSuspendedAt());
            assertThat(found.getFailedAttempts()).isEqualTo(saved.getFailedAttempts());
            assertThat(found.getVersion()).isEqualTo(saved.getVersion());
            assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
            assertThat(found.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());

            // Verify non-version fields match original input (version managed by JPA)
            assertThat(found.getId()).isEqualTo(original.getId());
            assertThat(found.getUserId()).isEqualTo(original.getUserId());
            assertThat(found.getPlanId()).isEqualTo(original.getPlanId());
            assertThat(found.getPriceAtPurchase().amount())
                    .isEqualByComparingTo(original.getPriceAtPurchase().amount());
            assertThat(found.getPriceAtPurchase().currency())
                    .isEqualTo(original.getPriceAtPurchase().currency());
            assertThat(found.getStatus()).isEqualTo(original.getStatus());
            assertThat(found.getStartDate()).isEqualTo(original.getStartDate());
            assertThat(found.getExpirationDate()).isEqualTo(original.getExpirationDate());
            assertThat(found.getCancelRequestedAt()).isEqualTo(original.getCancelRequestedAt());
            assertThat(found.getSuspendedAt()).isEqualTo(original.getSuspendedAt());
            assertThat(found.getFailedAttempts()).isEqualTo(original.getFailedAttempts());
            assertThat(found.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(found.getUpdatedAt()).isEqualTo(original.getUpdatedAt());

            // Rollback to keep DB clean for next try
            txStatus.setRollbackOnly();
        });
    }

    // --- Providers ---

    @Provide
    Arbitrary<User> arbitraryUsers() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(50);
        Arbitrary<String> emails = uuids.map(id -> "user-" + id.toString().substring(0, 8) + "@test.com");
        Arbitrary<Boolean> actives = Arbitraries.of(true, false);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, names, emails, actives, instants, instants)
                .as(User::new);
    }

    @Provide
    Arbitrary<Subscription> arbitrarySubscriptions() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<SubscriptionStatus> statuses = Arbitraries.of(SubscriptionStatus.values());
        Arbitrary<Money> moneys = Combinators.combine(
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("9999.99"))
                        .ofScale(2),
                Arbitraries.of("BRL", "USD", "EUR")
        ).as(Money::new);
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 730)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
        Arbitrary<Integer> failedAttemptValues = Arbitraries.integers().between(0, 5);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);
        Arbitrary<Instant> nullableInstants = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, instants),
                net.jqwik.api.Tuple.of(2, Arbitraries.just(null))
        );

        return Combinators.combine(uuids, uuids, moneys, statuses, dates, dates, nullableInstants)
                .flatAs((id, userId, price, status, startDate, expirationDate, cancelRequestedAt) ->
                        Combinators.combine(nullableInstants, failedAttemptValues, Arbitraries.just(0L), instants, instants)
                                .as((suspendedAt, failedAttempts, version, createdAt, updatedAt) ->
                                        new Subscription(
                                                id, userId, fixedPlanId,
                                                price, status,
                                                startDate, expirationDate,
                                                cancelRequestedAt, suspendedAt,
                                                failedAttempts, version,
                                                createdAt, updatedAt))
                );
    }
}
