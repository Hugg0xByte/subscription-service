# Implementation Plan: Payment Queue Decoupling

## Overview

Refatoração do fluxo de renovação de assinaturas para usar Google Cloud Pub/Sub como camada de mensageria assíncrona. O `RenewExpiredSubscriptionsUseCase` deixará de chamar o `PaymentGatewayPort` diretamente e passará a publicar mensagens no tópico "pendente-de-pagamento". Um consumer mock processará o pagamento e publicará o resultado em "pagamento-processado", e o `PaymentResultListener` consumirá o resultado para atualizar a Subscription.

## Tasks

- [x] 1. Set up dependencies and infrastructure configuration
  - [x] 1.1 Add Spring Cloud GCP Pub/Sub, Spring Retry, and Testcontainers GCloud dependencies to pom.xml
    - Add `spring-cloud-gcp-starter-pubsub` 6.5.0
    - Add `spring-retry` dependency
    - Add `org.testcontainers:gcloud` with scope test
    - Add `@EnableRetry` annotation to the main application class
    - _Requirements: 4.4, 4.5, 5.4_

  - [x] 1.2 Add Pub/Sub emulator service to docker-compose.yml
    - Add `pubsub-emulator` service using `gcr.io/google.com/cloudsdktool/google-cloud-cli:latest`
    - Configure command: `gcloud beta emulators pubsub start --host-port=0.0.0.0:8085`
    - Expose port 8085 and add healthcheck
    - _Requirements: 5.1_

  - [x] 1.3 Add Pub/Sub configuration properties to application.yml
    - Add `pubsub.topic.pendente-pagamento`, `pubsub.topic.pagamento-processado`, DLQ topics
    - Add `pubsub.subscription.pendente-pagamento`, `pubsub.subscription.pagamento-processado`
    - Configure `spring.cloud.gcp.pubsub.emulator-host` and `project-id`
    - _Requirements: 5.4, 5.5, 9.5_

- [x] 2. Create message DTOs and domain changes
  - [x] 2.1 Create PaymentRequestMessage record in application/dto package
    - Implement as Java record with messageId, subscriptionId, userId, planId, amount, currency, attemptNumber, idempotencyKey, timestamp
    - Add compact constructor with null/blank validation for all required fields
    - _Requirements: 1.2, 1.3, 8.1_

  - [x] 2.2 Create PaymentResultMessage record in application/dto package
    - Implement as Java record with subscriptionId, userId, status (enum APPROVED/FAILED), providerTransactionId, errorCode, errorMessage, idempotencyKey, processedAt
    - Add compact constructor with null/blank validation for required fields
    - _Requirements: 2.3, 8.2_

  - [x] 2.3 Add PENDENTE_PAGAMENTO to SubscriptionStatus enum and markAsPendingPayment() to Subscription entity
    - Add new enum value `PENDENTE_PAGAMENTO` to `SubscriptionStatus`
    - Add `markAsPendingPayment()` method to `Subscription` entity with state validation (valid from ATIVA or PENDENTE_PAGAMENTO)
    - _Requirements: 6.3_

  - [x] 2.4 Write property test for PaymentRequestMessage construction correctness
    - **Property 1: PaymentRequestMessage construction correctness**
    - Use jqwik to generate valid Subscription-like inputs and verify message fields match source
    - Verify attemptNumber, idempotencyKey format, non-null messageId and timestamp
    - **Validates: Requirements 1.1, 1.2, 1.3**

  - [x] 2.5 Write property test for PaymentRequestMessage serialization round-trip
    - **Property 2: PaymentRequestMessage serialization round-trip**
    - Use jqwik to generate arbitrary valid PaymentRequestMessage objects
    - Serialize to JSON with ObjectMapper, deserialize back, assert equivalence
    - **Validates: Requirements 8.4**

  - [x] 2.6 Write property test for PaymentResultMessage serialization round-trip
    - **Property 3: PaymentResultMessage serialization round-trip**
    - Use jqwik to generate both APPROVED and FAILED variants
    - Serialize to JSON with ObjectMapper, deserialize back, assert equivalence
    - **Validates: Requirements 8.5**

- [x] 3. Implement MessagePublisherPort and PubSubMessagePublisherAdapter
  - [x] 3.1 Create MessagePublisherPort interface in application/port package
    - Define `void publish(String topic, Object payload)` method
    - Add Javadoc documenting the contract
    - _Requirements: 4.1, 4.3_

  - [x] 3.2 Create PubSubMessagePublisherAdapter in adapter/outbound/messaging package
    - Implement `MessagePublisherPort` using `PubSubTemplate`
    - Serialize payload to JSON with `ObjectMapper` before publishing
    - Add `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))` for retry with exponential backoff
    - Record counter metrics for success/failure with Micrometer
    - Log structured messages with topic name
    - _Requirements: 1.4, 4.4, 8.1, 9.1_

  - [x] 3.3 Write unit tests for PubSubMessagePublisherAdapter
    - Test successful publish records success counter
    - Test failed publish records failure counter and throws exception
    - Test retry behavior on transient failures
    - Mock PubSubTemplate and ObjectMapper
    - _Requirements: 1.4, 1.5, 9.1_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create PubSubInitializer for topic/subscription auto-creation
  - [x] 5.1 Create PubSubInitializer in adapter/outbound/messaging package
    - Use `PubSubAdmin` to create topics and subscriptions in `@PostConstruct`
    - Create pendente-de-pagamento, pagamento-processado, and DLQ topics
    - Create subscriptions linking to their respective topics
    - Handle idempotent creation (skip if already exists)
    - _Requirements: 5.2, 5.3, 7.1, 7.2_

  - [x] 5.2 Write unit tests for PubSubInitializer
    - Test topics and subscriptions are created when they don't exist
    - Test no error when topics already exist
    - Mock PubSubAdmin
    - _Requirements: 5.2, 5.3_

- [x] 6. Refactor RenewExpiredSubscriptionsUseCase to publish to queue
  - [x] 6.1 Refactor RenewExpiredSubscriptionsUseCase to use MessagePublisherPort instead of PaymentGatewayPort
    - Replace `PaymentGatewayPort` dependency with `MessagePublisherPort`
    - Inject `pendingPaymentTopic` via `@Value`
    - Build `PaymentRequestMessage` from subscription data in `processSubscriptionRenewal`
    - Publish message, then call `subscription.markAsPendingPayment()`, save, and evict cache
    - Catch `RuntimeException` from publish per subscription and continue batch
    - Remove `paymentApprovedCounter` and `paymentFailedCounter` (moved to listener)
    - Retain distributed lock and batch processing logic
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 6.2 Write property test for refactored use case publishes to queue and updates status
    - **Property 8: Refactored use case publishes to queue and updates status**
    - Use jqwik to generate lists of eligible subscriptions
    - Verify exactly one publish per subscription, status becomes PENDENTE_PAGAMENTO, no PaymentGatewayPort call
    - **Validates: Requirements 6.1, 6.2, 6.3**

  - [x] 6.3 Write unit tests for refactored RenewExpiredSubscriptionsUseCase
    - Test successful publish updates subscription to PENDENTE_PAGAMENTO
    - Test publish failure logs error and continues batch
    - Test no invocation of PaymentGatewayPort
    - Test lock acquisition and release
    - Mock MessagePublisherPort, SubscriptionRepositoryPort, LockManagerPort
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement PaymentConsumerMock
  - [x] 8.1 Create PaymentConsumerMock in adapter/outbound/messaging package
    - Subscribe to "pendente-de-pagamento" subscription via `PubSubTemplate.subscribe()` in `@PostConstruct`
    - Deserialize `PaymentRequestMessage` from JSON
    - Build `PaymentAttempt` and call `MockPaymentGatewayAdapter.processPayment()`
    - Build `PaymentResultMessage` from payment result
    - Publish result to "pagamento-processado" topic
    - Ack message on success, nack on failure
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 8.2 Write property test for consumer mock produces valid PaymentResultMessage
    - **Property 4: Consumer mock produces valid PaymentResultMessage**
    - Use jqwik to generate valid PaymentRequestMessage objects
    - Verify output contains same subscriptionId, userId, idempotencyKey
    - Verify status is non-null, processedAt non-null, conditional fields consistent with status
    - **Validates: Requirements 2.2, 2.3**

  - [x] 8.3 Write unit tests for PaymentConsumerMock
    - Test successful processing acks message and publishes result
    - Test deserialization failure nacks message
    - Test approved result has providerTransactionId
    - Test failed result has errorCode
    - Mock PubSubTemplate, ObjectMapper, PaymentGatewayPort
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 9. Implement PaymentResultListener
  - [x] 9.1 Create PaymentResultListener in adapter/inbound/messaging package
    - Subscribe to "pagamento-processado" subscription via `PubSubTemplate.subscribe()` in `@PostConstruct`
    - Deserialize `PaymentResultMessage` from JSON
    - Implement idempotency check using `ConcurrentHashMap.newKeySet()` of processed idempotencyKeys
    - Route APPROVED → `subscription.processSuccessfulPayment()`, FAILED → `subscription.processFailedPayment()`
    - Persist subscription, evict cache, publish domain events
    - Ack malformed messages without retry, nack on transient errors
    - Add MDC context (subscriptionId, idempotencyKey) for structured logging
    - Record counter metrics (approved, failed, error) and timer for processing duration
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.2, 8.6, 9.2, 9.3, 9.4_

  - [x] 9.2 Write property test for payment result listener routes correctly based on status
    - **Property 5: Payment result listener routes correctly based on status**
    - Use jqwik to generate valid PaymentResultMessage with APPROVED/FAILED status
    - Verify correct domain method is invoked based on status
    - **Validates: Requirements 3.1, 3.2**

  - [x] 9.3 Write property test for payment result listener performs all side effects
    - **Property 6: Payment result listener performs all side effects**
    - Use jqwik to generate valid PaymentResultMessage with existing subscriptions
    - Verify persist, cache eviction, and domain event publication all occur
    - **Validates: Requirements 3.3**

  - [x] 9.4 Write property test for idempotent message processing
    - **Property 7: Idempotent message processing**
    - Use jqwik to generate valid PaymentResultMessage objects
    - Process same message twice, verify second processing does not modify subscription or persist
    - **Validates: Requirements 3.5**

  - [x] 9.5 Write unit tests for PaymentResultListener
    - Test APPROVED message invokes processSuccessfulPayment
    - Test FAILED message invokes processFailedPayment
    - Test subscription not found logs warning and acks
    - Test duplicate message is skipped
    - Test deserialization failure acks message without retry
    - Test metrics are recorded (approved, failed, error counters and timer)
    - Mock SubscriptionRepositoryPort, SubscriptionCachePort, EventPublisherPort, PubSubTemplate
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 9.2, 9.3_

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Integration test with Pub/Sub emulator
  - [x] 11.1 Write integration test for full Pub/Sub round-trip flow
    - Use Testcontainers with `gcloud` module to start Pub/Sub emulator
    - Test end-to-end: publish PaymentRequestMessage → PaymentConsumerMock processes → PaymentResultListener updates Subscription
    - Verify subscription status transitions correctly through PENDENTE_PAGAMENTO → ATIVA (or failed state)
    - Verify topics and subscriptions are auto-created by PubSubInitializer
    - _Requirements: 1.1, 2.1, 2.2, 3.1, 3.2, 3.3, 5.2, 5.3_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1-8)
- Unit tests validate specific examples and edge cases
- The project already uses jqwik 1.9.2 for property-based testing
- Java 25 with Spring Boot 3.5.7 and hexagonal architecture patterns are maintained
- Integration tests require the Testcontainers GCloud module for the Pub/Sub emulator

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "3.1"] },
    { "id": 2, "tasks": ["2.4", "2.5", "2.6", "3.2", "5.1"] },
    { "id": 3, "tasks": ["3.3", "5.2", "6.1"] },
    { "id": 4, "tasks": ["6.2", "6.3", "8.1"] },
    { "id": 5, "tasks": ["8.2", "8.3", "9.1"] },
    { "id": 6, "tasks": ["9.2", "9.3", "9.4", "9.5"] },
    { "id": 7, "tasks": ["11.1"] }
  ]
}
```
