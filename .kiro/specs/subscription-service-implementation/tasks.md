# Implementation Plan: Subscription Service Implementation

## Overview

Implementação faseada do Sistema de Gestão de Assinaturas em Java 25 com Spring Boot, seguindo arquitetura hexa`gonal. Cada fase produz código compilável e testável independentemente. A implementação é local-first: PostgreSQL local via Docker, Caffeine cache in-memory, mock payment gateway.

## Tasks

- [ ] 1. Project Scaffolding e Estrutura Base
  - [x] 1.1 Create Maven project structure with Spring Boot parent and dependency management
    - Initialize Spring Boot project with Java 25 source/target
    - Configure `pom.xml` with Spring Boot parent (latest stable), dependency management for: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, postgresql driver, liquibase-core, caffeine, resilience4j-spring-boot3, spring-boot-starter-test, jqwik, testcontainers-postgresql, archunit
    - Include Maven profiles: `default` (unit+property tests), `integration-tests`, `all-tests`
    - Configure JaCoCo plugin for coverage reporting
    - _Requirements: 1.1, 1.2, 1.6_

  - [x] 1.2 Create package structure and application entry point
    - Create top-level packages: `com.globo.subscription.domain`, `com.globo.subscription.application`, `com.globo.subscription.adapter`
    - Create `SubscriptionServiceApplication.java` main class with `@SpringBootApplication`
    - Create `application.yml` with profiles `local` (default) and `test`
    - Verify the application context loads successfully with `./mvnw spring-boot:run`
    - _Requirements: 1.3, 1.7, 1.8_

  - [x] 1.3 Create docker-compose.yml for local PostgreSQL
    - Configure PostgreSQL 16 container with credentials: user `huggooliveira`, password `Mv123456`, database `subscription_db`
    - Configure port mapping (localhost:5432)
    - Update `application-local.yml` with PostgreSQL connection settings matching the Docker credentials
    - _Requirements: 4.10, 4.12_

- [x] 2. Domain Layer — Entities, Value Objects, Enums e Events
  - [x] 2.1 Implement Money value object and Plan entity
    - Create `Money.java` as Java record with non-negative validation in compact constructor
    - Create `Plan.java` as domain entity with fields: id (UUID), name (String), displayName (String), monthlyPrice (Money), active (boolean), createdAt (Instant)
    - Plan is persisted in the database and cached (not an enum)
    - _Requirements: 2.5, 2.6_

  - [x] 2.2 Write property test for Money non-negativity invariant
    - **Property 1: Money value object non-negativity invariant**
    - **Validates: Requirements 2.5**
    - Create `MoneyPropertyTest.java` using jqwik with @ForAll BigDecimal generation
    - Verify negative amounts throw IllegalArgumentException, non-negative amounts succeed and preserve value

  - [x] 2.3 Implement SubscriptionStatus, PaymentAttemptStatus enums
    - Create `SubscriptionStatus.java` enum with values: ATIVA, PENDENTE_PAGAMENTO, SUSPENSA, EXPIRADA, CANCELADA
    - Create `PaymentAttemptStatus.java` enum with values: PROCESSING, APPROVED, FAILED, TIMEOUT
    - _Requirements: 2.7, 2.8_

  - [x] 2.4 Implement domain events as sealed interface hierarchy
    - Create `DomainEvent.java` sealed interface with subscriptionId(), occurredAt(), eventType() methods
    - Create record implementations: SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended, PaymentFailed, PaymentApproved
    - _Requirements: 2.13_

  - [x] 2.5 Implement User entity
    - Create `User.java` with fields: id (UUID), name (String), email (String), active (boolean), createdAt (Instant), updatedAt (Instant)
    - Implement constructor validation for required fields
    - _Requirements: 2.1_

  - [x] 2.6 Implement Subscription entity with business methods
    - Create `Subscription.java` with all fields as specified in design (planId UUID, priceAtPurchase Money instead of Plan enum)
    - Implement `processSuccessfulPayment()`: advance expirationDate +1 month, reset failedAttempts to 0, set status ATIVA, register PaymentApproved/SubscriptionRenewed event
    - Implement `processFailedPayment()`: increment failedAttempts, if reaches 3 set SUSPENSA + suspendedAt, else set PENDENTE_PAGAMENTO, register corresponding event
    - Implement `requestCancellation()`: set cancelRequestedAt without changing status, register SubscriptionCanceled event
    - Implement `isEligibleForRenewal()`: validate status is ATIVA or PENDENTE_PAGAMENTO
    - Implement domain events list management (getDomainEvents, clearDomainEvents)
    - Enforce invariant: status CANCELADA rejects all state-changing operations
    - _Requirements: 2.2, 2.9, 2.10, 2.11, 2.12, 2.14, 2.15_

  - [x] 2.7 Write property tests for Subscription state transitions
    - **Property 2: Subscription state transition enforcement**
    - **Validates: Requirements 2.9, 2.15**
    - Create `SubscriptionStatePropertyTest.java` using jqwik
    - Test that CANCELADA rejects all operations, non-eligible states reject renewal, valid states allow operations

  - [x] 2.8 Write property tests for successful payment postconditions
    - **Property 3: Successful payment postconditions**
    - **Validates: Requirements 2.10, 2.14**
    - Verify expirationDate advances exactly 1 month, failedAttempts resets to 0, status becomes ATIVA, domain event registered

  - [x] 2.9 Write property tests for failed payment with suspension threshold
    - **Property 4: Failed payment postconditions with suspension threshold**
    - **Validates: Requirements 2.11, 2.14**
    - Verify failedAttempts increments, suspension at 3 failures, correct events registered

  - [x] 2.10 Write property test for cancellation request preserving status
    - **Property 5: Cancellation request preserves status**
    - **Validates: Requirements 2.12**
    - Verify cancelRequestedAt is set but status remains unchanged

  - [x] 2.11 Implement PaymentMethod and PaymentAttempt entities
    - Create `PaymentMethod.java` with fields: id, userId, provider, token, active, createdAt, updatedAt
    - Create `PaymentAttempt.java` with fields: id, subscriptionId, amount (Money), status, attemptNumber, idempotencyKey, providerTransactionId, errorCode, errorMessage, createdAt, processedAt
    - _Requirements: 2.3, 2.4_

- [x] 3. Checkpoint — Domain Layer
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Application Layer — Use Cases e Ports
  - [x] 4.1 Define port interfaces
    - Create `SubscriptionRepositoryPort.java` with methods: save, findById, findActiveByUserId, findSubscriptionsDueForRenewal, existsActiveForUser
    - Create `UserRepositoryPort.java` with methods: save, findById, findByEmail
    - Create `PaymentGatewayPort.java` with processPayment returning PaymentResult (sealed interface)
    - Create `SubscriptionCachePort.java` with methods: getActiveSubscription, putActiveSubscription, evictActiveSubscription
    - Create `PlanRepositoryPort.java` with methods: findById, findByName, findAllActive
    - Create `PlanCachePort.java` with methods: getAllActivePlans, putAllActivePlans, evictAllPlans
    - Create `EventPublisherPort.java` with publish(DomainEvent) method
    - Create `LockManagerPort.java` with methods: acquireLock, releaseLock
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [x] 4.2 Implement domain-specific exceptions
    - Create `DomainException.java` abstract base class with errorCode field
    - Create `ActiveSubscriptionExistsException.java`
    - Create `EmailAlreadyExistsException.java`
    - Create `SubscriptionNotFoundException.java`
    - Create `InvalidStateTransitionException.java`
    - Create `UserNotFoundException.java`
    - _Requirements: 3.12, 3.13_

  - [x] 4.3 Implement CreateUserUseCase
    - Validate email uniqueness via UserRepositoryPort.findByEmail
    - If email exists, throw EmailAlreadyExistsException
    - Create User entity and persist via UserRepositoryPort.save
    - Annotate with @Service and @Transactional
    - _Requirements: 3.11_

  - [x] 4.4 Implement CreateSubscriptionUseCase
    - Check existsActiveForUser via SubscriptionRepositoryPort
    - If active subscription exists, throw ActiveSubscriptionExistsException
    - Retrieve Plan via PlanCachePort (on cache miss, query PlanRepositoryPort and populate cache)
    - Create new Subscription with status ATIVA, planId, and priceAtPurchase snapshot from Plan's monthlyPrice
    - Persist, evict subscription cache, publish SubscriptionCreated event
    - _Requirements: 3.9, 3.14_

  - [ ] 4.5 Implement RenewExpiredSubscriptionsUseCase
    - Acquire lock via LockManagerPort, abort if not acquired
    - Query due subscriptions via findSubscriptionsDueForRenewal
    - For each subscription: process payment, update entity state, persist, evict cache, publish event
    - Handle individual failures without aborting batch
    - _Requirements: 3.8, 3.13, 3.14_

  - [ ] 4.6 Implement CancelSubscriptionUseCase
    - Find subscription by ID, throw SubscriptionNotFoundException if not found
    - Call requestCancellation() on domain entity
    - Persist, evict cache, publish domain event
    - _Requirements: 3.9_

  - [ ] 4.7 Implement GetActiveSubscriptionUseCase
    - Check cache via SubscriptionCachePort.getActiveSubscription
    - On cache miss, query SubscriptionRepositoryPort.findActiveByUserId
    - Populate cache on miss, return result
    - _Requirements: 3.10_

  - [ ]* 4.8 Write unit tests for all use cases with mocked ports
    - Create `CreateUserUseCaseTest.java` — happy path, email already exists
    - Create `CreateSubscriptionUseCaseTest.java` — happy path, active subscription exists
    - Create `RenewExpiredSubscriptionsUseCaseTest.java` — lock acquired + mixed results, lock not acquired
    - Create `CancelSubscriptionUseCaseTest.java` — happy path, not found, already cancelled
    - Create `GetActiveSubscriptionUseCaseTest.java` — cache hit, cache miss, not found
    - _Requirements: 9.7_

- [ ] 5. Checkpoint — Application Layer
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Persistence Adapter — JPA e PostgreSQL
  - [ ] 6.1 Create Liquibase changelog scripts
    - Create `db.changelog-master.yaml` master changelog in `src/main/resources/db/changelog/`
    - Create `001-create-users-table.sql` in SQL format with `--liquibase formatted sql` header
    - Create `002-create-plans-and-subscriptions-tables.sql` with plans table, seed data (BASICO R$19.90, PREMIUM R$39.90, FAMILIA R$59.90), subscriptions table with plan_id FK, price_at_purchase, partial unique index and performance index
    - Create `003-create-subscription-status-history-table.sql`
    - Create `004-create-payment-tables.sql` with idempotency_key unique constraint
    - Create `005-create-subscription-events-table.sql` with unpublished index
    - Place SQL changelogs in `src/main/resources/db/changelog/changes/`
    - _Requirements: 4.5, 4.6, 4.7, 4.8, 4.9_

  - [ ] 6.2 Create JPA entity classes with mappers
    - Create `SubscriptionJpaEntity.java` with @Entity, @Table, @Version annotations (planId UUID, priceAtPurchase, currencyAtPurchase)
    - Create `UserJpaEntity.java`, `PlanJpaEntity.java`, `PaymentMethodJpaEntity.java`, `PaymentAttemptJpaEntity.java`, `SubscriptionEventJpaEntity.java`
    - Create `SubscriptionPersistenceMapper.java` (domain ↔ JPA entity conversion)
    - Create `UserPersistenceMapper.java`, `PlanPersistenceMapper.java`
    - Ensure JPA annotations do NOT leak to domain layer
    - _Requirements: 4.3, 4.4, 4.9_

  - [ ] 6.3 Implement JPA repository adapters
    - Create `SubscriptionJpaRepository.java` (Spring Data interface)
    - Create `UserJpaRepository.java` (Spring Data interface)
    - Create `PlanJpaRepository.java` (Spring Data interface)
    - Create `JpaSubscriptionRepositoryAdapter.java` implementing SubscriptionRepositoryPort
    - Create `JpaUserRepositoryAdapter.java` implementing UserRepositoryPort
    - Create `JpaPlanRepositoryAdapter.java` implementing PlanRepositoryPort
    - Implement `findSubscriptionsDueForRenewal` with `FOR UPDATE SKIP LOCKED` query
    - _Requirements: 4.1, 4.2, 4.11_

  - [ ]* 6.4 Write property test for Subscription serialization round-trip
    - **Property 6: Subscription serialization round-trip**
    - **Validates: Requirements 2.16**
    - Create `SubscriptionSerializationPropertyTest.java` using jqwik
    - Generate arbitrary Subscription states, verify domain→JPA→domain preserves all fields

  - [ ]* 6.5 Write property test for persistence adapter round-trip
    - **Property 7: Persistence adapter round-trip**
    - **Validates: Requirements 4.13**
    - Create `PersistenceRoundTripPropertyTest.java` using jqwik + Testcontainers
    - Persist entities, retrieve by ID, verify field equality

- [ ] 7. REST API Adapter — Controllers e DTOs
  - [ ] 7.1 Create request/response DTOs with validation
    - Create `CreateSubscriptionRequest.java` record with @NotNull annotations (userId UUID, planId UUID)
    - Create `CreateUserRequest.java` record with @NotBlank, @Email annotations
    - Create `SubscriptionResponse.java` record (no version or internal IDs of related entities exposed, includes plan name and price)
    - Create `UserResponse.java` record
    - Create `ErrorResponse.java` record
    - _Requirements: 5.3, 5.4_

  - [ ] 7.2 Implement REST mappers
    - Create `SubscriptionRestMapper.java` (domain entity → response DTO, request → use case input)
    - Create `UserRestMapper.java`
    - _Requirements: 5.10_

  - [ ] 7.3 Implement controllers and global exception handler
    - Create `SubscriptionController.java` with endpoints: POST /api/v1/subscriptions, GET /api/v1/subscriptions/active, DELETE /api/v1/subscriptions/{id}/cancel
    - Create `UserController.java` with endpoint: POST /api/v1/users
    - Create `GlobalExceptionHandler.java` @RestControllerAdvice mapping all exceptions to correct HTTP status codes
    - _Requirements: 5.1, 5.2, 5.5, 5.6, 5.7, 5.8, 5.9, 5.11_

  - [ ]* 7.4 Write property test for DTO validation
    - **Property 9: DTO validation rejects invalid input**
    - **Validates: Requirements 5.3**
    - Create `DtoValidationPropertyTest.java` using jqwik
    - Generate DTOs with invalid fields, verify constraint violations produced

  - [ ]* 7.5 Write property test for REST mapper field preservation
    - **Property 10: REST mapper field preservation**
    - **Validates: Requirements 5.10**
    - Create `RestMapperPropertyTest.java` using jqwik
    - Generate valid Subscription entities, verify mapped response preserves all exposed fields

- [ ] 8. Payment Adapter — Mock Gateway e Resiliência
  - [ ] 8.1 Implement MockPaymentGatewayAdapter with configurable outcomes
    - Create `MockPaymentGatewayAdapter.java` implementing PaymentGatewayPort
    - Configure success/failure/timeout ratios via application properties (default: 80%/15%/5%)
    - Generate idempotency key in format `subscription:{subscriptionId}:billing-cycle:{expirationDate}`
    - Simulate processing delay for realistic behavior
    - _Requirements: 6.1, 6.2, 6.8, 6.10_

  - [ ] 8.2 Configure Resilience4j circuit breaker, retry, and timeout
    - Configure CircuitBreaker: open after 5 consecutive failures
    - Configure Retry: max 3 attempts, exponential backoff (1s, 2s, 4s)
    - Configure Timeout: 10 seconds per request
    - Ensure exhausted retries or open circuit return PaymentResult.Failed (no unhandled exceptions)
    - Expose Resilience4j metrics via Actuator
    - _Requirements: 6.3, 6.4, 6.5, 6.6, 6.7, 6.9_

  - [ ]* 8.3 Write property test for idempotency key deterministic generation
    - **Property 11: Idempotency key deterministic generation**
    - **Validates: Requirements 6.8**
    - Create `IdempotencyKeyPropertyTest.java` using jqwik
    - Verify format is `subscription:{id}:billing-cycle:{date}` and same inputs always produce same output

- [ ] 9. Checkpoint — Adapters
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Scheduler, Cache e Event Publisher
  - [ ] 10.1 Implement CaffeineSubscriptionCacheAdapter and CaffeinePlanCacheAdapter
    - Create `CaffeineSubscriptionCacheAdapter.java` implementing SubscriptionCachePort
    - Configure Caffeine with TTL 5 minutes (configurable) and max 10,000 entries for subscriptions
    - Implement getActiveSubscription (cache hit returns without DB query), putActiveSubscription, evictActiveSubscription
    - Create `CaffeinePlanCacheAdapter.java` implementing PlanCachePort
    - Configure dedicated Caffeine cache with TTL 1 hour (configurable) and max 100 entries for plans
    - Implement getAllActivePlans, putAllActivePlans, evictAllPlans
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [ ]* 10.2 Write property test for cache put-get-evict round-trip
    - **Property 12: Cache put-get-evict round-trip**
    - **Validates: Requirements 8.3, 8.4, 8.5, 8.6**
    - Create `CacheRoundTripPropertyTest.java` using jqwik
    - Verify subscription cache: put→get returns subscription, evict→get returns empty
    - Verify plan cache: putAll→getAll returns plans, evict→getAll returns empty

  - [ ] 10.3 Implement LocalEventPublisherAdapter (outbox pattern)
    - Create `LocalEventPublisherAdapter.java` implementing EventPublisherPort
    - Serialize domain event payload to JSON
    - Insert row in subscription_events table with published_at=NULL
    - Ensure participation in same transaction as use case (atomicity guarantee)
    - _Requirements: 8.5, 8.6, 8.7_

  - [ ]* 10.4 Write property test for event publication round-trip
    - **Property 8: Event publication round-trip**
    - **Validates: Requirements 8.8**
    - Create `EventPublicationPropertyTest.java` using jqwik + Testcontainers
    - Publish event, query subscription_events, verify matching subscription_id, event_type, payload

  - [ ] 10.5 Implement InMemoryLockManagerAdapter
    - Create `InMemoryLockManagerAdapter.java` implementing LockManagerPort
    - Use ReentrantLock or AtomicBoolean for single-instance local locking
    - _Requirements: 7.8_

  - [ ] 10.6 Implement RenewalScheduler
    - Create `RenewalScheduler.java` with @Scheduled and configurable cron (default: every hour)
    - Acquire lock, delegate to RenewExpiredSubscriptionsUseCase, release lock in finally block
    - Process in configurable batch sizes (default: 100)
    - Log individual failures, continue batch processing
    - Log INFO: batch start, subscriptions found, successes, failures, duration
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.9_

- [ ] 11. Checkpoint — Scheduler, Cache, Events
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Observability, Integration Tests e Documentation
  - [ ] 12.1 Configure structured logging and Micrometer metrics
    - Configure structured JSON logging with fields: timestamp, level, logger, message, traceId (MDC), subscriptionId, userId
    - Configure log levels: DEBUG for cache, INFO for use cases, WARN for payment/lock failures, ERROR for unhandled exceptions
    - Expose Actuator endpoints: /actuator/health, /actuator/metrics, /actuator/info
    - Add Micrometer metrics: use case execution count/duration, payment outcomes, cache hit/miss ratio, renewal batch metrics
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [ ] 12.2 Implement integration tests with Testcontainers
    - Create test configuration with @SpringBootTest, test profile, Testcontainers PostgreSQL
    - Test full lifecycle: create user → create subscription → renew (success) → cancel
    - Test payment failure lifecycle: create → renew (fail x3) → suspension
    - Test persistence round-trip for all entities
    - Test Liquibase changelog execution
    - Test partial unique index enforcement (duplicate active subscription)
    - Test optimistic locking conflict detection
    - Configure integration tests to run on-demand (not every build) via Maven profile
    - _Requirements: 9.5, 9.8_

  - [ ]* 12.3 Write unit tests for domain entities (specific examples)
    - Create `SubscriptionTest.java` — specific state transitions with known inputs
    - Create `UserTest.java` — creation, validation
    - Create `MoneyTest.java` — edge cases (zero, boundary)
    - Create `PlanTest.java` — entity creation, validation, monthlyPrice consistency
    - _Requirements: 9.6_

  - [ ] 12.4 Write ArchUnit architecture tests
    - Create `ArchitectureTest.java` verifying: domain has no Spring/JPA imports, application has no adapter imports, outbound adapters implement port interfaces
    - _Requirements: 1.4, 1.5_

  - [ ] 12.5 Create README.md with project documentation
    - Include build instructions (`./mvnw clean install`)
    - Include PostgreSQL setup (`docker compose up`)
    - Include application run instructions (`./mvnw spring-boot:run`)
    - Include test execution commands (unit, integration, all-with-coverage)
    - Include API endpoint documentation with curl/httpie examples
    - _Requirements: 9.10_

- [ ] 13. Final Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation between phases
- Property tests (jqwik) validate universal correctness properties from the design
- Unit tests validate specific examples and edge cases
- Integration tests with Testcontainers verify cross-layer correctness
- The phased approach ensures each phase is independently compilable and testable
- Tech stack: Java 25, Spring Boot (latest stable), Maven, PostgreSQL 16, Liquibase, Caffeine, Resilience4j, jqwik, Testcontainers, ArchUnit

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.3", "2.4", "2.5"] },
    { "id": 3, "tasks": ["2.2", "2.6", "2.11"] },
    { "id": 4, "tasks": ["2.7", "2.8", "2.9", "2.10"] },
    { "id": 5, "tasks": ["4.1", "4.2"] },
    { "id": 6, "tasks": ["4.3", "4.4", "4.5", "4.6", "4.7"] },
    { "id": 7, "tasks": ["4.8"] },
    { "id": 8, "tasks": ["6.1", "7.1"] },
    { "id": 9, "tasks": ["6.2", "7.2", "8.1"] },
    { "id": 10, "tasks": ["6.3", "7.3", "8.2"] },
    { "id": 11, "tasks": ["6.4", "6.5", "7.4", "7.5", "8.3"] },
    { "id": 12, "tasks": ["10.1", "10.3", "10.5"] },
    { "id": 13, "tasks": ["10.2", "10.4", "10.6"] },
    { "id": 14, "tasks": ["12.1", "12.4", "12.5"] },
    { "id": 15, "tasks": ["12.2", "12.3"] }
  ]
}
```
