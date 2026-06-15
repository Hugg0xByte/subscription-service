package com.globo.subscription.adapter.outbound.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionEventJpaEntity;
import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionJpaEntity;
import com.globo.subscription.adapter.outbound.persistence.entity.UserJpaEntity;
import com.globo.subscription.adapter.outbound.persistence.repository.PlanJpaRepository;
import com.globo.subscription.adapter.outbound.persistence.repository.SubscriptionEventJpaRepository;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.PaymentApproved;
import com.globo.subscription.domain.event.PaymentFailed;
import com.globo.subscription.domain.event.SubscriptionCanceled;
import com.globo.subscription.domain.event.SubscriptionCreated;
import com.globo.subscription.domain.event.SubscriptionRenewed;
import com.globo.subscription.domain.event.SubscriptionSuspended;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.vo.Money;

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

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for event publication round-trip.
 *
 * <p><b>Validates: Requirements 8.8</b></p>
 *
 * <p>Property 8: Event publication round-trip —
 * For any domain event published through the EventPublisherPort, querying the subscription_events
 * table SHALL yield a record with matching subscription_id, event_type, and payload content
 * (JSON-serialized fields).</p>
 */
class EventPublicationPropertyTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.globo.subscription.adapter.outbound.persistence.entity")
    @EnableJpaRepositories(basePackages = "com.globo.subscription.adapter.outbound.persistence.repository")
    @ComponentScan(basePackages = {
            "com.globo.subscription.adapter.outbound.event",
            "com.globo.subscription.adapter.outbound.persistence"
    })
    static class EventTestConfig {
    }

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    static LocalEventPublisherAdapter eventPublisher;
    static SubscriptionEventJpaRepository eventRepository;
    static EntityManager entityManager;
    static TransactionTemplate transactionTemplate;
    static ObjectMapper objectMapper;
    static UUID fixedSubscriptionId;

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
        props.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("spring.main.allow-bean-definition-overriding", true);
        props.put("server.port", "0");

        context = new SpringApplicationBuilder(EventTestConfig.class)
                .properties(props)
                .run();

        eventPublisher = context.getBean(LocalEventPublisherAdapter.class);
        eventRepository = context.getBean(SubscriptionEventJpaRepository.class);
        entityManager = context.getBean(EntityManager.class);
        transactionTemplate = context.getBean(TransactionTemplate.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Seed a user and subscription for FK constraint
        fixedSubscriptionId = UUID.randomUUID();
        PlanJpaRepository planRepo = context.getBean(PlanJpaRepository.class);
        UUID planId = planRepo.findByActiveTrue().getFirst().getId();

        transactionTemplate.executeWithoutResult(txStatus -> {
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            // Create user
            UserJpaEntity user = new UserJpaEntity();
            user.setId(userId);
            user.setName("Test User");
            user.setEmail("event-test-" + userId.toString().substring(0, 8) + "@test.com");
            user.setActive(true);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            entityManager.persist(user);

            // Create subscription
            SubscriptionJpaEntity subscription = new SubscriptionJpaEntity();
            subscription.setId(fixedSubscriptionId);
            subscription.setUserId(userId);
            subscription.setPlanId(planId);
            subscription.setPriceAtPurchase(new BigDecimal("19.90"));
            subscription.setCurrencyAtPurchase("BRL");
            subscription.setStatus(SubscriptionStatus.ATIVA);
            subscription.setStartDate(LocalDate.of(2024, 1, 1));
            subscription.setExpirationDate(LocalDate.of(2024, 2, 1));
            subscription.setFailedAttempts(0);
            subscription.setVersion(0L);
            subscription.setCreatedAt(now);
            subscription.setUpdatedAt(now);
            entityManager.persist(subscription);

            entityManager.flush();
        });
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
    void eventPublicationRoundTrip_subscriptionCreated(
            @ForAll("arbitrarySubscriptionCreated") SubscriptionCreated event) {
        verifyEventRoundTrip(event);
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void eventPublicationRoundTrip_subscriptionRenewed(
            @ForAll("arbitrarySubscriptionRenewed") SubscriptionRenewed event) {
        verifyEventRoundTrip(event);
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void eventPublicationRoundTrip_subscriptionCanceled(
            @ForAll("arbitrarySubscriptionCanceled") SubscriptionCanceled event) {
        verifyEventRoundTrip(event);
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void eventPublicationRoundTrip_subscriptionSuspended(
            @ForAll("arbitrarySubscriptionSuspended") SubscriptionSuspended event) {
        verifyEventRoundTrip(event);
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void eventPublicationRoundTrip_paymentFailed(
            @ForAll("arbitraryPaymentFailed") PaymentFailed event) {
        verifyEventRoundTrip(event);
    }

    @Property(tries = 20, shrinking = net.jqwik.api.ShrinkingMode.OFF)
    void eventPublicationRoundTrip_paymentApproved(
            @ForAll("arbitraryPaymentApproved") PaymentApproved event) {
        verifyEventRoundTrip(event);
    }

    private void verifyEventRoundTrip(DomainEvent event) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            // Publish the event through the adapter
            eventPublisher.publish(event);

            // Flush to DB and clear first-level cache
            entityManager.flush();
            entityManager.clear();

            // Query all events and find the one matching our subscription_id and event_type
            List<SubscriptionEventJpaEntity> events = eventRepository.findAll();
            List<SubscriptionEventJpaEntity> matchingEvents = events.stream()
                    .filter(e -> e.getSubscriptionId().equals(event.subscriptionId()))
                    .filter(e -> e.getEventType().equals(event.eventType()))
                    .toList();

            assertThat(matchingEvents)
                    .as("Expected at least one event with subscription_id=%s and event_type=%s",
                            event.subscriptionId(), event.eventType())
                    .isNotEmpty();

            SubscriptionEventJpaEntity persisted = matchingEvents.getLast();

            // Verify subscription_id
            assertThat(persisted.getSubscriptionId()).isEqualTo(event.subscriptionId());

            // Verify event_type
            assertThat(persisted.getEventType()).isEqualTo(event.eventType());

            // Verify published_at is NULL (outbox pattern)
            assertThat(persisted.getPublishedAt()).isNull();

            // Verify payload contains the event's key fields as JSON
            String payload = persisted.getPayload();
            assertThat(payload).isNotNull();
            assertThat(payload).isNotBlank();

            // Parse and verify key fields in the JSON payload
            try {
                JsonNode payloadNode = objectMapper.readTree(payload);
                assertThat(payloadNode.get("subscriptionId").asText())
                        .isEqualTo(event.subscriptionId().toString());
            } catch (Exception e1) {
                throw new AssertionError("Failed to parse event payload JSON", e1);
            }

            // Rollback to keep DB clean for next try
            txStatus.setRollbackOnly();
        });
    }

    // --- Arbitrary Providers ---
    // All events use the fixedSubscriptionId to satisfy FK constraint

    @Provide
    Arbitrary<SubscriptionCreated> arbitrarySubscriptionCreated() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<Money> moneys = Combinators.combine(
                Arbitraries.bigDecimals().between(BigDecimal.ONE, new BigDecimal("999.99")).ofScale(2),
                Arbitraries.of("BRL", "USD", "EUR")
        ).as(Money::new);
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 730)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, moneys, dates, dates, instants)
                .as((userId, planId, price, startDate, expirationDate, occurredAt) ->
                        new SubscriptionCreated(fixedSubscriptionId, userId, planId, price,
                                startDate, expirationDate, occurredAt));
    }

    @Provide
    Arbitrary<SubscriptionRenewed> arbitrarySubscriptionRenewed() {
        Arbitrary<LocalDate> dates = Arbitraries.integers()
                .between(0, 730)
                .map(offset -> LocalDate.of(2024, 1, 1).plusDays(offset));
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(dates, instants)
                .as((newExpirationDate, occurredAt) ->
                        new SubscriptionRenewed(fixedSubscriptionId, newExpirationDate, occurredAt));
    }

    @Provide
    Arbitrary<SubscriptionCanceled> arbitrarySubscriptionCanceled() {
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(instants, instants)
                .as((cancelRequestedAt, occurredAt) ->
                        new SubscriptionCanceled(fixedSubscriptionId, cancelRequestedAt, occurredAt));
    }

    @Provide
    Arbitrary<SubscriptionSuspended> arbitrarySubscriptionSuspended() {
        Arbitrary<Integer> failedAttempts = Arbitraries.integers().between(1, 5);
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(failedAttempts, instants, instants)
                .as((attempts, suspendedAt, occurredAt) ->
                        new SubscriptionSuspended(fixedSubscriptionId, attempts, suspendedAt, occurredAt));
    }

    @Provide
    Arbitrary<PaymentFailed> arbitraryPaymentFailed() {
        Arbitrary<Integer> failedAttempts = Arbitraries.integers().between(1, 5);
        Arbitrary<String> errorCodes = Arbitraries.of(
                "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "NETWORK_ERROR", "TIMEOUT");
        Arbitrary<String> errorMessages = Arbitraries.of(
                "Payment declined", "Card expired", "Network timeout");
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(failedAttempts, errorCodes, errorMessages, instants)
                .as((attempts, code, message, occurredAt) ->
                        new PaymentFailed(fixedSubscriptionId, attempts, code, message, occurredAt));
    }

    @Provide
    Arbitrary<PaymentApproved> arbitraryPaymentApproved() {
        Arbitrary<String> transactionIds = Arbitraries.create(UUID::randomUUID)
                .map(id -> "txn-" + id.toString().substring(0, 12));
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(1_700_000_000L, 1_750_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(transactionIds, instants)
                .as((txnId, occurredAt) ->
                        new PaymentApproved(fixedSubscriptionId, txnId, occurredAt));
    }
}
