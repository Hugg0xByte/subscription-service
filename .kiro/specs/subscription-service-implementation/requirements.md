# Requirements Document

## Introduction

Este documento especifica os requisitos para a implementação em código do Sistema de Gestão de Assinaturas, conforme a arquitetura definida em #[[file:.kiro/specs/subscription-architecture-design/design.md]]. A implementação segue uma abordagem faseada e incremental, onde cada fase é testável independentemente antes de avançar para a próxima. O stack tecnológico é Java 25, Spring Boot (última versão estável) e Hexagonal Architecture. O ambiente inicial é 100% local (PostgreSQL local, sem cloud), com evolução futura para cloud.

## Glossary

- **Subscription_Service**: Microserviço Spring Boot que implementa toda a lógica de gestão de assinaturas
- **Domain_Layer**: Camada de domínio puro em Java (entities, value objects, enums, domain events) sem dependências de framework
- **Application_Layer**: Camada de aplicação contendo use cases e definições de port interfaces, sem dependência de infraestrutura
- **Persistence_Adapter**: Adaptador JPA/Spring Data que implementa os repository ports com PostgreSQL local
- **REST_Adapter**: Adaptador REST (Spring MVC controllers) que expõe a API HTTP do sistema
- **Payment_Adapter**: Adaptador que implementa o PaymentGatewayPort com mock local, circuit breaker e retry
- **Renewal_Scheduler**: Componente Spring @Scheduled que executa a renovação automática em batch
- **Cache_Adapter**: Adaptador Caffeine que implementa o SubscriptionCachePort para cache in-memory local
- **Event_Publisher_Adapter**: Adaptador que implementa o EventPublisherPort para publicação de domain events
- **Flyway**: Ferramenta de migração de banco de dados para versionamento de schema
- **Testcontainers**: Biblioteca para testes de integração com containers Docker (PostgreSQL)
- **Hexagonal_Architecture**: Padrão onde Domain e Application não dependem de infraestrutura — dependências apontam para dentro
- **Port**: Interface definida na Application_Layer que abstrai uma dependência externa
- **Adapter**: Implementação concreta de um Port, localizada na camada de infraestrutura

## Requirements

### Requirement 1: Project Scaffolding e Estrutura de Módulos

**User Story:** As a developer, I want a well-structured Spring Boot project with clear module boundaries following hexagonal architecture, so that I can develop each layer independently and enforce dependency rules.

#### Acceptance Criteria

1. THE Subscription_Service SHALL use Java 25 as the source and target compilation version
2. THE Subscription_Service SHALL use the latest stable version of Spring Boot as the parent dependency
3. WHEN the project is scaffolded, THE Subscription_Service SHALL organize source code into the following top-level packages: `domain`, `application`, `adapter`
4. THE Subscription_Service SHALL configure the `domain` package to contain zero imports from Spring framework, JPA, or any infrastructure library
5. THE Subscription_Service SHALL configure the `application` package to contain zero imports from Spring framework (except Spring stereotype annotations for dependency injection), JPA, or adapter-specific libraries
6. THE Subscription_Service SHALL include a build configuration (Maven or Gradle) with dependency management for: Spring Boot Starter Web, Spring Boot Starter Data JPA, Spring Boot Starter Validation, PostgreSQL Driver, Flyway Core, Caffeine, Resilience4j, and Spring Boot Starter Test
7. THE Subscription_Service SHALL include an `application.yml` configuration file with profiles for `local` (default) and `test`
8. WHEN the project is scaffolded, THE Subscription_Service SHALL compile successfully and the Spring Boot application context SHALL load without errors

### Requirement 2: Domain Layer — Entities e Value Objects

**User Story:** As a developer, I want pure Java domain entities and value objects implementing all business rules, so that the core logic is framework-independent and fully unit-testable.

#### Acceptance Criteria

1. THE Domain_Layer SHALL implement the `User` entity with fields: id (UUID), name (String), email (String), active (boolean), createdAt (Instant), updatedAt (Instant)
2. THE Domain_Layer SHALL implement the `Subscription` entity with fields: id (UUID), userId (UUID), plan (Plan), status (SubscriptionStatus), startDate (LocalDate), expirationDate (LocalDate), cancelRequestedAt (Instant nullable), suspendedAt (Instant nullable), failedAttempts (int), version (long), createdAt (Instant), updatedAt (Instant)
3. THE Domain_Layer SHALL implement the `PaymentMethod` entity with fields: id (UUID), userId (UUID), provider (String), token (String), active (boolean), createdAt (Instant), updatedAt (Instant)
4. THE Domain_Layer SHALL implement the `PaymentAttempt` entity with fields: id (UUID), subscriptionId (UUID), amount (Money), status (PaymentAttemptStatus), attemptNumber (int), idempotencyKey (String), providerTransactionId (String nullable), errorCode (String nullable), errorMessage (String nullable), createdAt (Instant), processedAt (Instant nullable)
5. THE Domain_Layer SHALL implement the `Money` value object as a Java record with fields: amount (BigDecimal), currency (String), enforcing that amount is non-negative
6. THE Domain_Layer SHALL implement the `Plan` enum with values: BASICO, PREMIUM, FAMILIA, each providing its monthly price as a Money value object (R$19.90, R$39.90, R$59.90 respectively)
7. THE Domain_Layer SHALL implement the `SubscriptionStatus` enum with values: ATIVA, PENDENTE_PAGAMENTO, SUSPENSA, EXPIRADA, CANCELADA
8. THE Domain_Layer SHALL implement the `PaymentAttemptStatus` enum with values: PROCESSING, APPROVED, FAILED, TIMEOUT
9. WHEN the `Subscription` entity receives a renewal request, THE Subscription entity SHALL validate that status is ATIVA or PENDENTE_PAGAMENTO before allowing the operation
10. WHEN the `Subscription` entity processes a successful payment, THE Subscription entity SHALL advance expirationDate by 1 month, reset failedAttempts to 0, and set status to ATIVA
11. WHEN the `Subscription` entity processes a failed payment, THE Subscription entity SHALL increment failedAttempts by 1, and IF failedAttempts reaches 3 THEN transition status to SUSPENSA and record suspendedAt
12. WHEN the `Subscription` entity receives a cancellation request, THE Subscription entity SHALL record cancelRequestedAt without changing the current status
13. THE Domain_Layer SHALL implement domain events as sealed interfaces or sealed classes: SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended, PaymentFailed, PaymentApproved
14. WHEN a state-changing operation occurs on the Subscription entity, THE Subscription entity SHALL register the corresponding domain event internally for later publication
15. THE Domain_Layer SHALL enforce the invariant that a Subscription with status CANCELADA rejects all state-changing operations
16. FOR ALL valid Subscription state transitions, applying the transition and then checking the status SHALL produce a status consistent with the state machine defined in the business rules (round-trip property: serialize entity state → deserialize → verify equality)

### Requirement 3: Application Layer — Use Cases e Ports

**User Story:** As a developer, I want use case classes and port interfaces that orchestrate domain logic without infrastructure coupling, so that business workflows are testable with mocks and the system remains adaptable.

#### Acceptance Criteria

1. THE Application_Layer SHALL define the `SubscriptionRepositoryPort` interface with methods: save(Subscription), findById(UUID), findActiveByUserId(UUID), findSubscriptionsDueForRenewal(LocalDate, int batchSize), existsActiveForUser(UUID)
2. THE Application_Layer SHALL define the `UserRepositoryPort` interface with methods: save(User), findById(UUID), findByEmail(String)
3. THE Application_Layer SHALL define the `PaymentGatewayPort` interface with methods: processPayment(PaymentAttempt) returning PaymentResult
4. THE Application_Layer SHALL define the `SubscriptionCachePort` interface with methods: getActiveSubscription(UUID userId), putActiveSubscription(UUID userId, Subscription), evictActiveSubscription(UUID userId)
5. THE Application_Layer SHALL define the `EventPublisherPort` interface with methods: publish(DomainEvent)
6. THE Application_Layer SHALL define the `LockManagerPort` interface with methods: acquireLock(String lockName, Duration ttl) returning boolean, releaseLock(String lockName)
7. THE Application_Layer SHALL implement `CreateSubscriptionUseCase` that: validates no active subscription exists for the user via SubscriptionRepositoryPort, creates a new Subscription with status ATIVA, persists via SubscriptionRepositoryPort, invalidates cache via SubscriptionCachePort, and publishes SubscriptionCreated event
8. THE Application_Layer SHALL implement `RenewExpiredSubscriptionsUseCase` that: acquires distributed lock via LockManagerPort, queries due subscriptions via SubscriptionRepositoryPort, for each subscription processes payment via PaymentGatewayPort, updates subscription state based on PaymentResult, persists changes, invalidates cache, and publishes the corresponding domain event
9. THE Application_Layer SHALL implement `CancelSubscriptionUseCase` that: finds the subscription, calls the cancellation method on the domain entity, persists the updated subscription, invalidates cache, and publishes the appropriate domain event
10. THE Application_Layer SHALL implement `GetActiveSubscriptionUseCase` that: first checks SubscriptionCachePort, on cache miss queries SubscriptionRepositoryPort, populates cache on miss, and returns the active subscription or empty
11. THE Application_Layer SHALL implement `CreateUserUseCase` that: validates email uniqueness via UserRepositoryPort, and IF email is not unique THEN aborts user creation by throwing a domain-specific exception without modifying any state, otherwise creates a new User entity and persists via UserRepositoryPort
12. WHEN `CreateSubscriptionUseCase` detects an existing active subscription for the user, THE use case SHALL throw a domain-specific exception without modifying any state
13. WHEN `RenewExpiredSubscriptionsUseCase` fails to acquire the distributed lock, THE use case SHALL abort execution without processing any subscriptions
14. FOR ALL use case executions that modify a Subscription, the resulting Subscription state SHALL be consistent with the domain invariants (status transitions, failedAttempts bounds, version increment)

### Requirement 4: Persistence Adapter — JPA e PostgreSQL Local

**User Story:** As a developer, I want JPA repository adapters backed by a local PostgreSQL database with Flyway migrations, so that I can persist and query domain entities with proper schema versioning.

#### Acceptance Criteria

1. THE Persistence_Adapter SHALL implement `JpaSubscriptionRepositoryAdapter` that implements `SubscriptionRepositoryPort` using Spring Data JPA
2. THE Persistence_Adapter SHALL implement `JpaUserRepositoryAdapter` that implements `UserRepositoryPort` using Spring Data JPA
3. THE Persistence_Adapter SHALL include JPA entity classes (`SubscriptionJpaEntity`, `UserJpaEntity`, `PaymentMethodJpaEntity`, `PaymentAttemptJpaEntity`) mapped to database tables, separate from domain entities
4. THE Persistence_Adapter SHALL include mapper classes that convert between domain entities and JPA entities without exposing JPA annotations to the domain layer
5. THE Persistence_Adapter SHALL configure Flyway migrations in `src/main/resources/db/migration` with numbered scripts creating all tables: users, subscriptions, subscription_status_history, payment_methods, payment_attempts, subscription_events
6. WHEN Flyway migrations execute, THE Persistence_Adapter SHALL create the partial unique index `uq_active_subscription_per_user` on subscriptions(user_id) WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO')
7. WHEN Flyway migrations execute, THE Persistence_Adapter SHALL create the performance index `idx_subscriptions_due_renewal` on subscriptions(status, expiration_date)
8. WHEN Flyway migrations execute, THE Persistence_Adapter SHALL create the unique constraint on payment_attempts.idempotency_key
9. THE Persistence_Adapter SHALL configure the `subscriptions.version` column for JPA @Version optimistic locking
10. THE Persistence_Adapter SHALL configure a local PostgreSQL connection in `application-local.yml` with sensible defaults (localhost:5432, database name, credentials)
11. WHEN `findSubscriptionsDueForRenewal` is called, THE JpaSubscriptionRepositoryAdapter SHALL use a JPQL/native query with `FOR UPDATE SKIP LOCKED` to prevent row contention during batch processing
12. THE Persistence_Adapter SHALL include a `docker-compose.yml` at project root to run PostgreSQL locally with a single `docker compose up` command
13. FOR ALL domain entities persisted and then retrieved, the retrieved entity SHALL be equal to the original entity (round-trip property: save → findById → assertEquals on all fields)

### Requirement 5: REST API Adapter — Controllers e DTOs

**User Story:** As a developer, I want REST API endpoints exposing subscription management operations with proper validation and error handling, so that clients can interact with the system over HTTP.

#### Acceptance Criteria

1. THE REST_Adapter SHALL implement `SubscriptionController` with the following endpoints: POST /api/v1/subscriptions (create subscription), GET /api/v1/subscriptions/active?userId={userId} (get active subscription), DELETE /api/v1/subscriptions/{id}/cancel (cancel subscription)
2. THE REST_Adapter SHALL implement `UserController` with the following endpoints: POST /api/v1/users (create user)
3. THE REST_Adapter SHALL define request DTOs with Jakarta Bean Validation annotations (@NotNull, @NotBlank, @Email, @Valid) for all input fields
4. THE REST_Adapter SHALL define response DTOs that expose only necessary fields without leaking internal domain state (no version field, no internal IDs of related entities)
5. WHEN a request DTO fails validation, THE REST_Adapter SHALL return HTTP 400 regardless of any other error conditions that may also be present
6. WHEN a domain-specific business exception is thrown (e.g., active subscription already exists), THE REST_Adapter SHALL return HTTP 409 (Conflict) with an error message describing the business rule violation
7. WHEN an entity is not found, THE REST_Adapter SHALL return HTTP 404 with an error message
8. WHEN an OptimisticLockException occurs, THE REST_Adapter SHALL return HTTP 409 with an error message indicating concurrent modification
9. THE REST_Adapter SHALL implement a global `@RestControllerAdvice` exception handler that maps all known exceptions to appropriate HTTP status codes and structured error response bodies
10. THE REST_Adapter SHALL include mapper classes that convert between request/response DTOs and domain entities or use case input/output objects
11. WHEN the application starts, THE REST_Adapter SHALL expose a health check endpoint at GET /actuator/health returning HTTP 200

### Requirement 6: Payment Adapter — Mock Gateway e Resiliência

**User Story:** As a developer, I want a mock payment gateway adapter with circuit breaker and retry patterns, so that I can develop and test the payment flow locally without external dependencies.

#### Acceptance Criteria

1. THE Payment_Adapter SHALL implement `MockPaymentGatewayAdapter` that implements `PaymentGatewayPort` and simulates payment processing locally
2. WHEN the MockPaymentGatewayAdapter processes a payment, THE adapter SHALL simulate configurable outcomes: approve (default 80% of requests), reject (15%), timeout (5%)
3. THE Payment_Adapter SHALL wrap the PaymentGatewayPort invocation with a Resilience4j CircuitBreaker configured to open after 5 consecutive failures
4. WHEN the CircuitBreaker is in OPEN state, THE Payment_Adapter SHALL fail immediately without invoking the gateway and return a failure PaymentResult
5. THE Payment_Adapter SHALL wrap the PaymentGatewayPort invocation with a Resilience4j Retry configured for maximum 3 attempts with exponential backoff (1s, 2s, 4s)
6. THE Payment_Adapter SHALL configure a timeout of 10 seconds per payment request
7. WHEN all retry attempts are exhausted or the circuit breaker is open, THE Payment_Adapter SHALL return a PaymentResult with status FAILED and an appropriate error message without throwing unhandled exceptions
8. THE Payment_Adapter SHALL generate the idempotency key in format `subscription:{subscriptionId}:billing-cycle:{expirationDate}` and include it in the PaymentAttempt before processing
9. THE Payment_Adapter SHALL expose Resilience4j metrics (circuit breaker state, retry count, timeout count) via Spring Boot Actuator
10. THE Payment_Adapter SHALL allow configuration of success/failure/timeout ratios via application properties for testing different scenarios

### Requirement 7: Renewal Scheduler e Batch Processing

**User Story:** As a developer, I want an automated scheduler that processes subscription renewals in batches with distributed locking, so that expired subscriptions are renewed without manual intervention and without duplicate processing.

#### Acceptance Criteria

1. THE Renewal_Scheduler SHALL execute periodically via Spring @Scheduled with a configurable cron expression (default: every hour)
2. WHEN the scheduler triggers, THE Renewal_Scheduler SHALL attempt to acquire a distributed lock via LockManagerPort before processing any subscriptions
3. IF the distributed lock cannot be acquired, THEN THE Renewal_Scheduler SHALL skip the execution and log a warning without throwing an exception
4. WHEN the lock is acquired, THE Renewal_Scheduler SHALL delegate all business logic to `RenewExpiredSubscriptionsUseCase`, passing the current date and batch size
5. THE Renewal_Scheduler SHALL process subscriptions in configurable batch sizes (default: 100) to avoid memory pressure and long-running transactions
6. WHEN a single subscription renewal fails (exception), THE Renewal_Scheduler SHALL log the error (accepting that logging itself may silently fail), continue processing the remaining subscriptions in the batch, and not abort the entire batch
7. THE Renewal_Scheduler SHALL release the distributed lock after processing completes (success or failure) using a finally block or equivalent guarantee
8. THE Renewal_Scheduler SHALL implement a simple in-memory `LockManagerPort` adapter for local development that uses `ReentrantLock` or `AtomicBoolean`
9. THE Renewal_Scheduler SHALL log at INFO level: batch start, number of subscriptions found, number of successes, number of failures, batch end with total duration

### Requirement 8: Cache e Domain Events

**User Story:** As a developer, I want an in-memory cache for active subscription lookups and a local domain event publishing mechanism, so that read performance is optimized and domain events are captured for future integration.

#### Acceptance Criteria

1. THE Cache_Adapter SHALL implement `CaffeineSubscriptionCacheAdapter` that implements `SubscriptionCachePort` using Caffeine cache library
2. THE Cache_Adapter SHALL configure Caffeine with a TTL of 5 minutes (configurable via application properties) and a maximum size of 10,000 entries
3. WHEN `getActiveSubscription` is called and a cache hit occurs, THE Cache_Adapter SHALL return the cached subscription without querying the database
4. WHEN `evictActiveSubscription` is called, THE Cache_Adapter SHALL remove the entry for the given userId immediately
5. THE Event_Publisher_Adapter SHALL implement `LocalEventPublisherAdapter` that implements `EventPublisherPort` by persisting domain events to the `subscription_events` table
6. WHEN a domain event is published, THE Event_Publisher_Adapter SHALL serialize the event payload to JSON and insert a row in `subscription_events` with `published_at` set to NULL (outbox pattern — ready for future async publisher)
7. THE Event_Publisher_Adapter SHALL participate in the same transaction as the use case operation, ensuring atomicity between state change and event persistence
8. FOR ALL events published through EventPublisherPort, the event SHALL be retrievable from the subscription_events table with matching subscription_id, event_type, and payload content (round-trip property: publish → query → verify content)

### Requirement 9: Observability e Integration Testing

**User Story:** As a developer, I want structured logging, health metrics, and comprehensive integration tests with Testcontainers, so that I can monitor system behavior and verify correctness across all layers.

#### Acceptance Criteria

1. THE Subscription_Service SHALL configure structured JSON logging with fields: timestamp, level, logger, message, traceId (MDC), subscriptionId (MDC when available), userId (MDC when available)
2. THE Subscription_Service SHALL log at appropriate levels: DEBUG for cache hits/misses, INFO for use case execution start/end, WARN for payment failures and lock acquisition failures, ERROR for unhandled exceptions
3. THE Subscription_Service SHALL expose Spring Boot Actuator endpoints: /actuator/health, /actuator/metrics, /actuator/info
4. THE Subscription_Service SHALL include Micrometer metrics for: use case execution count and duration, payment attempt outcomes (approved/failed/timeout), cache hit/miss ratio, renewal batch execution count and duration
5. THE Subscription_Service SHALL include integration tests using Testcontainers with PostgreSQL that verify: full lifecycle (create user → create subscription → renew → cancel), persistence round-trip for all entities, Flyway migration execution, partial unique index enforcement, optimistic locking conflict detection
6. THE Subscription_Service SHALL include unit tests for all domain entities verifying: state transitions, invariant enforcement, domain event generation, Money value object constraints
7. THE Subscription_Service SHALL include unit tests for all use cases using mocked ports verifying: happy path, error paths, cache invalidation, event publication
8. WHEN integration tests execute, THE Subscription_Service SHALL use `@SpringBootTest` with the `test` profile and Testcontainers PostgreSQL, requiring no external infrastructure to run; integration tests MAY be configured to not execute during every build but SHALL be runnable on demand
9. THE Subscription_Service SHALL achieve a minimum of 80% line coverage on Domain_Layer and Application_Layer code
10. THE Subscription_Service SHALL include a README.md with instructions to: build the project, run PostgreSQL locally (docker compose), run the application, run tests, and test the API endpoints with curl/httpie examples

