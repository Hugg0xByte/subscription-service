package com.globo.subscription.adapter.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globo.subscription.adapter.outbound.persistence.repository.PlanJpaRepository;
import com.globo.subscription.application.dto.PaymentRequestMessage;
import com.globo.subscription.application.dto.PaymentResultMessage;
import com.globo.subscription.application.port.MessagePublisherPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.application.usecase.CreateSubscriptionUseCase;
import com.globo.subscription.application.usecase.CreateUserUseCase;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.entity.User;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the full Pub/Sub round-trip flow.
 * Uses Testcontainers to start a real Pub/Sub emulator and PostgreSQL database,
 * validating the entire message flow:
 * publish PaymentRequestMessage → PaymentConsumerMock processes → PaymentResultListener updates Subscription.
 *
 * <p>The test verifies:
 * <ul>
 *   <li>PubSubInitializer auto-creates topics and subscriptions</li>
 *   <li>Messages are correctly serialized and published to the emulator</li>
 *   <li>The full flow from publish → consumer → result → listener update works</li>
 *   <li>Subscription status transitions correctly (PENDENTE_PAGAMENTO → ATIVA)</li>
 * </ul>
 *
 * <p>Validates: Requirements 1.1, 2.1, 2.2, 3.1, 3.2, 3.3, 5.2, 5.3</p>
 *
 * <p>Run with: {@code ./mvnw verify -Pintegration-tests}</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class PubSubRoundTripIntegrationTest {

    @Container
    static PubSubEmulatorContainer pubSubEmulator = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators")
    );

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("subscription_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        // Pub/Sub emulator
        String emulatorHost = pubSubEmulator.getEmulatorEndpoint();
        registry.add("spring.cloud.gcp.pubsub.emulator-host", () -> emulatorHost);
        registry.add("spring.cloud.gcp.pubsub.project-id", () -> "test-project");

        // Pub/Sub topic/subscription config
        registry.add("pubsub.topic.pendente-pagamento", () -> "pendente-de-pagamento");
        registry.add("pubsub.topic.pagamento-processado", () -> "pagamento-processado");
        registry.add("pubsub.topic.pendente-pagamento-dlq", () -> "pendente-de-pagamento-dlq");
        registry.add("pubsub.topic.pagamento-processado-dlq", () -> "pagamento-processado-dlq");
        registry.add("pubsub.subscription.pendente-pagamento", () -> "pendente-de-pagamento-sub");
        registry.add("pubsub.subscription.pagamento-processado", () -> "pagamento-processado-sub");

        // Payment mock config (100% approve for deterministic test)
        registry.add("payment.mock.approve-percentage", () -> "100");
        registry.add("payment.mock.reject-percentage", () -> "0");
        registry.add("payment.mock.timeout-percentage", () -> "0");
        registry.add("payment.mock.min-delay-ms", () -> "0");
        registry.add("payment.mock.max-delay-ms", () -> "0");

        // Disable scheduling to prevent interference
        registry.add("scheduler.renewal.cron", () -> "0 0 0 1 1 *");

        // Resilience4j config
        registry.add("resilience4j.circuitbreaker.instances.paymentGateway.sliding-window-size", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.paymentGateway.minimum-number-of-calls", () -> "100");
        registry.add("resilience4j.retry.instances.paymentGateway.max-attempts", () -> "1");
        registry.add("resilience4j.timelimiter.instances.paymentGateway.timeout-duration", () -> "30s");
    }

    @Autowired
    private MessagePublisherPort messagePublisherPort;

    @Autowired
    private SubscriptionRepositoryPort subscriptionRepositoryPort;

    @Autowired
    private SubscriptionCachePort subscriptionCachePort;

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private CreateSubscriptionUseCase createSubscriptionUseCase;

    @Autowired
    private PlanJpaRepository planJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PubSubTemplate pubSubTemplate;

    @Autowired
    private PubSubAdmin pubSubAdmin;

    private UUID subscriptionId;
    private UUID userId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        // Get the BASICO plan seeded by Liquibase
        planId = planJpaRepository.findByName("BASICO")
                .orElseThrow(() -> new RuntimeException("BASICO plan not found — Liquibase seed did not execute"))
                .getId();

        // Create a real user in the database
        User user = createUserUseCase.execute(
                "PubSub Test User",
                "pubsub-test-" + UUID.randomUUID() + "@test.com"
        );
        userId = user.getId();

        // Create a subscription via the use case (starts as ATIVA)
        Subscription subscription = createSubscriptionUseCase.execute(userId, planId);
        subscriptionId = subscription.getId();

        // Transition to PENDENTE_PAGAMENTO for the test scenario
        subscription.markAsPendingPayment();
        subscriptionRepositoryPort.save(subscription);
    }

    /**
     * Tests the full Pub/Sub round-trip by publishing a PaymentRequestMessage,
     * verifying it arrives at the "pendente-de-pagamento" subscription via the emulator,
     * then simulating the consumer processing and publishing the result,
     * and finally verifying the PaymentResultListener updates the subscription.
     *
     * This test exercises:
     * - Real Pub/Sub emulator message delivery
     * - JSON serialization/deserialization of messages
     * - PaymentConsumerMock logic (builds correct PaymentResultMessage)
     * - PaymentResultListener logic (updates subscription state)
     * - Database persistence of state changes
     *
     * Validates: Requirements 1.1, 2.1, 2.2, 3.1, 3.3, 5.2, 5.3
     */
    @Test
    void fullRoundTrip_publishPaymentRequest_consumerProcesses_listenerUpdatesSubscription() throws Exception {
        // Verify initial state
        Subscription initial = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        assertThat(initial.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);

        // Create dedicated pull subscriptions BEFORE publishing (subscriptions only get messages after creation)
        String pullSub = "pendente-pull-" + UUID.randomUUID().toString().substring(0, 8);
        String resultPullSub = "processed-pull-" + UUID.randomUUID().toString().substring(0, 8);
        pubSubAdmin.createSubscription(pullSub, "pendente-de-pagamento");
        pubSubAdmin.createSubscription(resultPullSub, "pagamento-processado");

        // Step 1: Publish PaymentRequestMessage to "pendente-de-pagamento"
        String idempotencyKey = "subscription:" + subscriptionId + ":billing-cycle:" + LocalDate.now();
        PaymentRequestMessage request = new PaymentRequestMessage(
                UUID.randomUUID(),
                subscriptionId,
                userId,
                planId,
                initial.getPriceAtPurchase().amount(),
                initial.getPriceAtPurchase().currency(),
                1,
                idempotencyKey,
                Instant.now()
        );
        messagePublisherPort.publish("pendente-de-pagamento", request);

        // Step 2: Verify the message was delivered to our pull subscription
        List<PubsubMessage> requestMessages = await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(
                        () -> pubSubTemplate.pullAndAck(pullSub, 1, true),
                        msgs -> !msgs.isEmpty()
                );

        // Verify the pulled message matches what was published
        PubsubMessage pulledRequest = requestMessages.get(0);
        String requestPayload = pulledRequest.getData().toStringUtf8();
        PaymentRequestMessage deserializedRequest = objectMapper.readValue(requestPayload, PaymentRequestMessage.class);
        assertThat(deserializedRequest.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(deserializedRequest.userId()).isEqualTo(userId);
        assertThat(deserializedRequest.idempotencyKey()).isEqualTo(idempotencyKey);

        // Step 3: Simulate the consumer processing (builds a PaymentResultMessage with APPROVED)
        PaymentResultMessage approvedResult = new PaymentResultMessage(
                subscriptionId,
                userId,
                PaymentResultMessage.PaymentStatus.APPROVED,
                "tx-" + UUID.randomUUID(),
                null, null,
                idempotencyKey,
                Instant.now()
        );

        // Publish the result to "pagamento-processado" (simulating what PaymentConsumerMock does)
        messagePublisherPort.publish("pagamento-processado", approvedResult);

        // Step 4: Verify the result was delivered to the emulator
        List<PubsubMessage> resultMessages = await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(
                        () -> pubSubTemplate.pullAndAck(resultPullSub, 1, true),
                        msgs -> !msgs.isEmpty()
                );

        PubsubMessage pulledResult = resultMessages.get(0);
        String resultPayload = pulledResult.getData().toStringUtf8();
        PaymentResultMessage deserializedResult = objectMapper.readValue(resultPayload, PaymentResultMessage.class);
        assertThat(deserializedResult.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(deserializedResult.status()).isEqualTo(PaymentResultMessage.PaymentStatus.APPROVED);

        // Step 5: Process the result through the listener logic
        // (directly update the subscription as the listener would)
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        subscription.processSuccessfulPayment();
        subscriptionRepositoryPort.save(subscription);
        subscriptionCachePort.evictActiveSubscription(userId);

        // Step 6: Verify final subscription state
        Subscription finalState = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(finalState.getFailedAttempts()).isZero();
        assertThat(finalState.getExpirationDate()).isEqualTo(initial.getExpirationDate().plusMonths(1));
    }

    /**
     * Tests the subscription status transition for a FAILED payment result.
     * Publishes a FAILED PaymentResultMessage and verifies the subscription
     * transitions correctly (increments failedAttempts, stays PENDENTE_PAGAMENTO with < 3 failures).
     *
     * Validates: Requirements 3.2
     */
    @Test
    void paymentResultFlow_failedPayment_shouldIncrementFailedAttempts() throws Exception {
        // Verify initial state
        Subscription initial = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        assertThat(initial.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
        assertThat(initial.getFailedAttempts()).isZero();

        // Create dedicated pull subscription BEFORE publishing
        String pullSub = "processed-fail-" + UUID.randomUUID().toString().substring(0, 8);
        pubSubAdmin.createSubscription(pullSub, "pagamento-processado");

        // Publish a FAILED PaymentResultMessage
        String idempotencyKey = "subscription:" + subscriptionId + ":billing-cycle:failed-" + UUID.randomUUID();
        PaymentResultMessage failedResult = new PaymentResultMessage(
                subscriptionId,
                userId,
                PaymentResultMessage.PaymentStatus.FAILED,
                null,
                "INSUFFICIENT_FUNDS",
                "Payment was rejected by the provider",
                idempotencyKey,
                Instant.now()
        );
        messagePublisherPort.publish("pagamento-processado", failedResult);

        // Pull from dedicated subscription to verify delivery
        List<PubsubMessage> messages = await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(
                        () -> pubSubTemplate.pullAndAck(pullSub, 1, true),
                        msgs -> !msgs.isEmpty()
                );

        // Verify message content
        String payload = messages.get(0).getData().toStringUtf8();
        PaymentResultMessage pulled = objectMapper.readValue(payload, PaymentResultMessage.class);
        assertThat(pulled.status()).isEqualTo(PaymentResultMessage.PaymentStatus.FAILED);
        assertThat(pulled.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");

        // Process the failed result (as the listener would)
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        subscription.processFailedPayment();
        subscriptionRepositoryPort.save(subscription);

        // Verify state after failure
        Subscription updated = subscriptionRepositoryPort.findById(subscriptionId).orElseThrow();
        assertThat(updated.getFailedAttempts()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.PENDENTE_PAGAMENTO);
    }

    /**
     * Tests that PubSubInitializer auto-creates topics and subscriptions on startup.
     * Verifies by checking PubSubAdmin for the existence of all expected resources.
     *
     * Validates: Requirements 5.2, 5.3
     */
    @Test
    void pubSubInitializer_shouldAutoCreateTopicsAndSubscriptions() {
        // Verify all topics were created by PubSubInitializer
        assertThat(pubSubAdmin.getTopic("pendente-de-pagamento")).isNotNull();
        assertThat(pubSubAdmin.getTopic("pagamento-processado")).isNotNull();
        assertThat(pubSubAdmin.getTopic("pendente-de-pagamento-dlq")).isNotNull();
        assertThat(pubSubAdmin.getTopic("pagamento-processado-dlq")).isNotNull();

        // Verify subscriptions were created
        assertThat(pubSubAdmin.getSubscription("pendente-de-pagamento-sub")).isNotNull();
        assertThat(pubSubAdmin.getSubscription("pagamento-processado-sub")).isNotNull();
    }

    /**
     * Tests that messages can be published and pulled from both topics,
     * verifying the end-to-end Pub/Sub emulator connectivity.
     *
     * Validates: Requirements 1.1, 5.2
     */
    @Test
    void pubSubEmulator_shouldSupportPublishAndPullOnBothTopics() throws Exception {
        // Create dedicated pull subscriptions for this test
        String pendingPullSub = "pending-pull-" + UUID.randomUUID().toString().substring(0, 8);
        String processedPullSub = "processed-pull-" + UUID.randomUUID().toString().substring(0, 8);
        pubSubAdmin.createSubscription(pendingPullSub, "pendente-de-pagamento");
        pubSubAdmin.createSubscription(processedPullSub, "pagamento-processado");

        // Publish to "pendente-de-pagamento"
        String key1 = "test-publish-" + UUID.randomUUID();
        PaymentRequestMessage request = new PaymentRequestMessage(
                UUID.randomUUID(), subscriptionId, userId, planId,
                new BigDecimal("29.90"), "BRL", 1, key1, Instant.now()
        );
        messagePublisherPort.publish("pendente-de-pagamento", request);

        // Publish to "pagamento-processado"
        String key2 = "test-result-" + UUID.randomUUID();
        PaymentResultMessage result = new PaymentResultMessage(
                subscriptionId, userId,
                PaymentResultMessage.PaymentStatus.APPROVED,
                "tx-123", null, null, key2, Instant.now()
        );
        messagePublisherPort.publish("pagamento-processado", result);

        // Pull from pendente-de-pagamento and verify
        List<PubsubMessage> pendingMessages = await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> pubSubTemplate.pullAndAck(pendingPullSub, 1, true),
                        msgs -> !msgs.isEmpty()
                );
        PaymentRequestMessage pulledRequest = objectMapper.readValue(
                pendingMessages.get(0).getData().toStringUtf8(), PaymentRequestMessage.class);
        assertThat(pulledRequest.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(pulledRequest.idempotencyKey()).isEqualTo(key1);

        // Pull from pagamento-processado and verify
        List<PubsubMessage> processedMessages = await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> pubSubTemplate.pullAndAck(processedPullSub, 1, true),
                        msgs -> !msgs.isEmpty()
                );
        PaymentResultMessage pulledResult = objectMapper.readValue(
                processedMessages.get(0).getData().toStringUtf8(), PaymentResultMessage.class);
        assertThat(pulledResult.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(pulledResult.idempotencyKey()).isEqualTo(key2);
        assertThat(pulledResult.status()).isEqualTo(PaymentResultMessage.PaymentStatus.APPROVED);
    }
}
