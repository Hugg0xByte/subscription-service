# Implementation Plan

## Overview

Create the complete architectural documentation for the Subscription Management System (Globo Streaming). This includes Draw.io diagrams (architecture, class, database) and Markdown documents (business rules, architecture decision records). All documentation follows hexagonal architecture principles with cloud-agnostic design.

## Tasks

- [x] 1. Create Architecture Diagram
  - **Requirements:** Requirement 1 (all acceptance criteria), Requirement 6 (criteria 1, 2, 5, 6), Requirement 7 (criteria 1-5, 7)
  - **Subtasks:**
    - [x] 1.1 Create the base Draw.io file at docs/diagrams/architecture-subscription-system.drawio
    - [x] 1.2 Add external components: Client/API Consumer, API Gateway, Payment Provider, Message Broker
    - [x] 1.3 Add the Subscription Service container with hexagonal architecture layers (Inbound Adapters, Application/Use Cases, Domain Model, Outbound Ports, Infrastructure Adapters)
    - [x] 1.4 Add infrastructure components: PostgreSQL, Redis/Caffeine, Scheduler
    - [x] 1.5 Add the main request flow connections: Client → REST API → Use Case → Domain → Payment Port → Payment Provider, with branches to Repository, Cache, and Event Publisher
    - [x] 1.6 Add the renewal flow: Scheduler → RenewUseCase → Repository → PaymentGateway → Update → Publish Event
    - [x] 1.7 Add observability components: Logs, Metrics, Traces
    - [x] 1.8 Add Cloud Infrastructure boundary with abstract service categories (Managed Database, Managed Cache, Messaging Service, Observability Platform, Secrets Management, Container Runtime) and GCP as V1 implementation with AWS/Azure as future alternatives
    - [x] 1.9 Add visual separation between current architecture and future multi-service evolution (subscription-service, payment-service, billing-scheduler-service, notification-service)
    - [x] 1.10 Apply consistent color coding and label all connections, ensure all outbound connections pass through labeled Outbound_Port interfaces

- [x] 2. Create Database Diagram
  - **Requirements:** Requirement 2 (all acceptance criteria), Requirement 6 (criteria 1, 2, 4, 6)
  - **Subtasks:**
    - [x] 2.1 Create the base Draw.io file at docs/diagrams/database-subscription-system.drawio
    - [x] 2.2 Add the users table with columns: id (UUID PK), name (VARCHAR NOT NULL), email (VARCHAR NOT NULL UNIQUE), active (BOOLEAN NOT NULL), created_at, updated_at
    - [x] 2.3 Add the subscriptions table with columns: id (UUID PK), user_id (UUID FK), plan (VARCHAR), status (VARCHAR), start_date (DATE), expiration_date (DATE), cancel_requested_at, suspended_at, failed_attempts (INT DEFAULT 0), version (BIGINT), created_at, updated_at
    - [ ] 2.3b Add the subscription_status_history table with columns: id (UUID PK), subscription_id (UUID FK NOT NULL), from_status (VARCHAR nullable), to_status (VARCHAR NOT NULL), reason (VARCHAR NOT NULL), changed_by (VARCHAR NOT NULL), changed_at (TIMESTAMP NOT NULL)
    - [x] 2.4 Add the payment_methods table with columns: id (UUID PK), user_id (UUID FK), provider (VARCHAR), token (VARCHAR), active (BOOLEAN), created_at, updated_at
    - [x] 2.5 Add the payment_attempts table with columns: id (UUID PK), subscription_id (UUID FK), amount (NUMERIC(10,2)), status (VARCHAR NOT NULL — lifecycle: PROCESSING → APPROVED/FAILED/TIMEOUT), attempt_number (INT), idempotency_key (VARCHAR UNIQUE), provider_transaction_id, error_code, error_message, created_at, processed_at
    - [x] 2.6 Add the subscription_events table with columns: id (UUID PK), subscription_id (UUID FK), event_type (VARCHAR), payload (JSONB), created_at, published_at
    - [x] 2.7 Add foreign key relationship lines between all tables with cardinality notation (including subscription_status_history → subscriptions)
    - [x] 2.8 Add constraints section: partial unique index uq_active_subscription_per_user, performance index idx_subscriptions_due_renewal, unique idempotency_key, index idx_status_history_subscription on subscription_status_history(subscription_id, changed_at)
    - [x] 2.9 Apply database diagram notation standards (PK, FK markers, cardinality on relationships) and consistent color coding

- [ ] 3. Create Class Diagram
  - **Requirements:** Requirement 3 (all acceptance criteria), Requirement 6 (criteria 1, 2, 3, 6), Requirement 7 (criteria 5, 6)
  - **Subtasks:**
    - [ ] 3.1 Create the base Draw.io file at docs/diagrams/class-diagram-subscription-system.drawio
    - [ ] 3.2 Add Domain layer classes: User, Subscription, Plan (enum), SubscriptionStatus (enum), PaymentMethod, PaymentAttempt, Money (value object), SubscriptionId (value object), UserId (value object), PaymentResult (sealed), DomainEvent (sealed), SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended
    - [ ] 3.3 Add Application layer use cases: CreateUserUseCase, CreateSubscriptionUseCase, RenewExpiredSubscriptionsUseCase, CancelSubscriptionUseCase, GetActiveSubscriptionUseCase
    - [ ] 3.4 Add Port interfaces with <<interface>> stereotype: UserRepositoryPort, SubscriptionRepositoryPort, PaymentGatewayPort, SubscriptionCachePort, EventPublisherPort, LockManagerPort, ObservabilityPort, SecretsPort
    - [ ] 3.5 Add Adapter layer classes with <<adapter>> stereotype: SubscriptionController, RenewSubscriptionScheduler, GcpCloudSqlSubscriptionRepositoryAdapter, GcpCloudSqlUserRepositoryAdapter, GcpMemorystoreCacheAdapter, GcpPubSubEventPublisherAdapter, GcpSecretManagerAdapter, GcpCloudLoggingObservabilityAdapter, PaymentProviderAdapter
    - [ ] 3.6 Add dependency arrows: Controllers → Use Cases, Use Cases → Ports, Adapters → Ports (implements), Use Cases → Domain, Domain → DomainEvent
    - [ ] 3.7 Verify and enforce: no arrows from Domain to Adapters/Spring/infrastructure, no cloud-specific concepts in Domain or Application layers
    - [ ] 3.8 Apply UML notation standards, color coding per layer, and layer package groupings

- [ ] 4. Create Business Rules Documentation
  - **Requirements:** Requirement 4 (all acceptance criteria), Requirement 6 (criterion 6)
  - **Subtasks:**
    - [x] 4.1 Create docs/business-rules.md with document structure and introduction
    - [ ] 4.2 Document available plans and prices: BASICO (R$19.90/mês), PREMIUM (R$39.90/mês), FAMILIA (R$59.90/mês)
    - [ ] 4.3 Document subscription states (ATIVA, SUSPENSA, CANCELADA, EXPIRADA, PENDENTE_PAGAMENTO) with definitions and valid state transitions
    - [ ] 4.4 Document the one-active-subscription-per-user constraint with enforcement mechanisms
    - [ ] 4.5 Document automatic renewal rules: trigger on expiration day, payment processing, success/failure flows
    - [ ] 4.6 Document payment retry rules: up to 3 attempts, suspension after 3 failures, idempotency key format (subscription:{id}:billing-cycle:{expirationDate})
    - [ ] 4.6b Document the explicit payment flow lifecycle with database state transitions at each step: (a) scheduler detects expiration → status ATIVA→PENDENTE_PAGAMENTO + insert status_history; (b) payment sent → insert payment_attempts status=PROCESSING; (c) approved → payment_attempts APPROVED + subscription ATIVA + advance expiration + reset failed_attempts + insert status_history; (d) rejected → payment_attempts FAILED + increment failed_attempts + insert status_history; (e) 3rd failure → subscription SUSPENSA + insert status_history
    - [ ] 4.7 Document cancellation rules: access retained until end of billing cycle, status transitions
    - [ ] 4.8 Document concurrency control mechanisms: optimistic locking (version), partial unique index, distributed lock, FOR UPDATE SKIP LOCKED
    - [ ] 4.9 Document cache strategy: what is cached, invalidation triggers (creation, renewal, cancellation, suspension)
    - [ ] 4.10 Document domain events: SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended, PaymentFailed, PaymentApproved with their payloads
    - [ ] 4.11 Document resilience patterns for payment: circuit breaker, retry with backoff, timeout, fallback behavior

- [ ] 5. Create Architecture Decision Records
  - **Requirements:** Requirement 5 (all acceptance criteria), Requirement 6 (criterion 6), Requirement 7 (criteria 4, 7)
  - **Subtasks:**
    - [ ] 5.1 Create docs/architecture-decisions.md with ADR format template (Title, Status, Context, Decision, Consequences)
    - [ ] 5.2 Write ADR-001: Single Modularized Microservice with Hexagonal Architecture (rationale: small domain, avoid premature distribution, clean separation, future evolution path)
    - [ ] 5.3 Write ADR-002: Future Evolution Vision (subscription-service, payment-service, billing-scheduler-service, notification-service with migration criteria)
    - [ ] 5.4 Write ADR-003: Technology Stack (Java 25, Spring Boot, PostgreSQL, Redis/Caffeine, Resilience4j, Kafka/RabbitMQ optional, Docker, Testcontainers)
    - [ ] 5.5 Write ADR-004: GCP Deployment Strategy (Cloud Run, Cloud SQL, Memorystore, Pub/Sub, Secret Manager, Cloud Build, Artifact Registry, Cloud Logging/Monitoring)
    - [ ] 5.6 Write ADR-005: Cache Strategy (start with Caffeine for simplicity, evolve to Redis/Memorystore for multi-instance)
    - [ ] 5.7 Write ADR-006: Concurrency and Idempotency Strategy (transactions, optimistic locking, partial unique index, idempotency key, optional distributed lock)
    - [ ] 5.8 Write ADR-007: Event Strategy (domain events, outbox pattern, subscription_events table, future Kafka/Pub/Sub publishing)
    - [ ] 5.9 Write ADR-008: Cloud-Agnostic Strategy (provider portability, GCP as V1, all cloud services behind Outbound_Port interfaces, migration requires only new adapters)

## Task Dependency Graph

```
Task 1 (Architecture Diagram)
Task 2 (Database Diagram)
Task 3 (Class Diagram) --> Task 1
Task 4 (Business Rules) 
Task 5 (ADR Document) --> Task 1, Task 3
```

## Notes

- All diagrams use the aws-drawio Kiro Power for Draw.io file creation
- Color coding standard: Domain (Orange #FFE0B2), Application (Yellow #FFF9C4), Adapter (Green #C8E6C9), External (Purple #E1BEE7)
- Bilingual convention: PT-BR for business terms, English for technical terms
- Cloud-agnostic design: GCP as V1, AWS/Azure as documented alternatives
