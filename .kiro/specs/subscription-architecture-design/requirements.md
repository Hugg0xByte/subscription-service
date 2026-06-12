# Requirements Document

## Introduction

Este documento especifica os requisitos para a criação da documentação arquitetural e diagramas do Sistema de Gestão de Assinaturas para streaming (desafio técnico Globo). O escopo desta fase é exclusivamente documentação e design — sem implementação de código. Os entregáveis incluem diagramas Draw.io (arquitetura, classes, banco de dados) e documentação Markdown (regras de negócio, decisões arquiteturais).

## Glossary

- **Documentation_System**: Sistema responsável por gerar e manter a documentação arquitetural do projeto
- **Architecture_Diagram**: Diagrama Draw.io que representa os componentes do sistema, suas camadas e integrações
- **Class_Diagram**: Diagrama Draw.io que representa classes de domínio, casos de uso, portas e adaptadores
- **Database_Diagram**: Diagrama Draw.io que representa o modelo de dados com tabelas, relacionamentos e constraints
- **Business_Rules_Document**: Documento Markdown que detalha todas as regras de negócio do sistema de assinaturas
- **ADR_Document**: Documento Markdown de Architecture Decision Records que registra decisões arquiteturais chave
- **Hexagonal_Architecture**: Padrão arquitetural que separa domínio, aplicação e infraestrutura através de portas e adaptadores
- **Subscription_Service**: Microserviço único modularizado que gerencia assinaturas de streaming
- **GCP**: Google Cloud Platform, plataforma de deploy alvo para a visão futura do sistema
- **Domain_Layer**: Camada interna da arquitetura hexagonal contendo modelos, value objects, eventos e exceções de domínio
- **Application_Layer**: Camada intermediária contendo casos de uso, commands, queries e definições de portas
- **Adapter_Layer**: Camada externa contendo implementações de portas (REST, persistence, payment, cache, messaging)
- **Draw_io_Power**: Ferramenta do Kiro para criação e edição de diagramas no formato .drawio
- **Cloud_Infrastructure_Layer**: Camada de abstração que encapsula todos os serviços de infraestrutura cloud (banco gerenciado, cache, mensageria, observabilidade, secrets) como adaptadores intercambiáveis
- **Outbound_Port**: Interface definida na Application_Layer que abstrai uma dependência de infraestrutura externa, permitindo múltiplas implementações (adapters) por cloud provider
- **Cloud_Provider**: Provedor de serviços de nuvem (GCP, AWS ou Azure) cuja implementação concreta é isolada na Adapter_Layer
- **Subscription_Status_History**: Tabela de auditoria que registra todas as transições de status de uma assinatura, incluindo status anterior, novo status, motivo da transição e quem/o que disparou a mudança
- **Payment_Attempt_Lifecycle**: Ciclo de vida imutável de uma tentativa de pagamento: PROCESSING (enviado ao provider) → APPROVED (confirmado) | FAILED (rejeitado) | TIMEOUT (sem resposta)

## Requirements

### Requirement 1: Architecture Diagram Creation

**User Story:** As a software architect, I want an architecture diagram in Draw.io format, so that I can visualize all system components, layers, integrations and deployment targets before implementation.

#### Acceptance Criteria

1. THE Documentation_System SHALL produce a Draw.io file at `docs/diagrams/architecture-subscription-system.drawio`
2. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL include the following external components: Client/API Consumer, API Gateway/Load Balancer, PostgreSQL database, Redis/Caffeine cache, external Payment Provider, optional Message Broker
3. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL represent the Hexagonal_Architecture layers within the Subscription_Service: Inbound Adapters, Application Use Cases, Domain Model, Outbound Ports, Infrastructure Adapters
4. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL include the main request flow: Client → REST API → Use Case → Domain → Payment Port → Payment Provider, with branches to Repository Port → PostgreSQL, Cache Port → Redis/Caffeine, and Event Publisher → Broker
5. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL include the renewal flow: Scheduler → RenewExpiredSubscriptionsUseCase → SubscriptionRepository → PaymentGateway → Update Subscription → Publish Event
6. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL include the observability components (Logs, Metrics, and Traces) connected to the Subscription_Service indicating that all internal layers emit telemetry data
7. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL represent cloud infrastructure as a generic "Cloud Infrastructure" bounded region containing abstract service categories (Managed Database, Managed Cache, Messaging Service, Observability Platform, Secrets Management, Container Runtime), with GCP labeled as the V1 implementation (Cloud Run, Cloud SQL, Memorystore, Pub/Sub, Cloud Logging/Monitoring, Secret Manager) and annotations indicating AWS and Azure as future alternative implementations
8. THE Architecture_Diagram SHALL use separate grouping boxes (bounded regions) to visually distinguish the current single-microservice architecture from the future multi-service evolution vision containing: subscription-service, payment-service, billing-scheduler-service, notification-service
9. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL represent all outbound connections from the Subscription_Service to cloud infrastructure services as passing through explicitly labeled Outbound_Port interfaces, visually demonstrating that the application depends on abstractions rather than concrete cloud provider services

### Requirement 2: Database Diagram Creation

**User Story:** As a software architect, I want a database diagram in Draw.io format, so that I can visualize the data model with all tables, relationships, constraints and indexes.

#### Acceptance Criteria

1. THE Documentation_System SHALL produce a Draw.io file at `docs/diagrams/database-subscription-system.drawio`
2. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the following tables: users, subscriptions, subscription_status_history, payment_methods, payment_attempts, subscription_events
3. WHEN the Database_Diagram is created, THE Documentation_System SHALL represent for each table: column name, data type, nullability (NOT NULL or nullable), and default value (if any), specifically: users (id UUID PK, name VARCHAR NOT NULL, email VARCHAR NOT NULL, active BOOLEAN NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP), subscriptions (id UUID PK, user_id UUID FK NOT NULL, plan VARCHAR NOT NULL, status VARCHAR NOT NULL, start_date DATE NOT NULL, expiration_date DATE NOT NULL, cancel_requested_at TIMESTAMP, suspended_at TIMESTAMP, failed_attempts INT DEFAULT 0, version BIGINT NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP), subscription_status_history (id UUID PK, subscription_id UUID FK NOT NULL, from_status VARCHAR, to_status VARCHAR NOT NULL, reason VARCHAR NOT NULL, changed_by VARCHAR NOT NULL, changed_at TIMESTAMP NOT NULL), payment_methods (id UUID PK, user_id UUID FK NOT NULL, provider VARCHAR NOT NULL, token VARCHAR NOT NULL, active BOOLEAN NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP), payment_attempts (id UUID PK, subscription_id UUID FK NOT NULL, amount NUMERIC(10,2) NOT NULL, status VARCHAR NOT NULL, attempt_number INT NOT NULL, idempotency_key VARCHAR NOT NULL, provider_transaction_id VARCHAR, error_code VARCHAR, error_message VARCHAR, created_at TIMESTAMP, processed_at TIMESTAMP), subscription_events (id UUID PK, subscription_id UUID FK NOT NULL, event_type VARCHAR NOT NULL, payload JSONB NOT NULL, created_at TIMESTAMP, published_at TIMESTAMP)
4. WHEN the Database_Diagram is created, THE Documentation_System SHALL represent foreign key relationships with cardinality notation: subscriptions.user_id → users.id (many-to-one), subscription_status_history.subscription_id → subscriptions.id (many-to-one), payment_methods.user_id → users.id (many-to-one), payment_attempts.subscription_id → subscriptions.id (many-to-one), subscription_events.subscription_id → subscriptions.id (many-to-one)
5. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the unique constraint on users.email
6. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the partial unique index `uq_active_subscription_per_user` on subscriptions(user_id) WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO')
7. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the performance index `idx_subscriptions_due_renewal` on subscriptions(status, expiration_date)
8. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the unique constraint on payment_attempts.idempotency_key
9. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the version column of type BIGINT NOT NULL in subscriptions table for optimistic locking
10. WHEN the Database_Diagram is created, THE Documentation_System SHALL include the subscription_status_history table to track all status transitions, where each row records: the previous status (from_status, nullable for initial creation), the new status (to_status), the reason for transition (e.g., 'payment_approved', 'payment_failed_3x', 'user_canceled', 'expiration_reached', 'manual_reactivation'), who or what triggered it (changed_by: 'scheduler', 'user', 'admin', 'payment_callback'), and when it happened (changed_at)
11. WHEN the Database_Diagram is created, THE Documentation_System SHALL represent the payment_attempts.status column as having the following lifecycle values: PROCESSING (payment sent to provider, awaiting response), APPROVED (provider confirmed successful charge), FAILED (provider rejected the charge), TIMEOUT (provider did not respond within configured timeout), specifying that each payment_attempt is immutable after reaching a terminal status (APPROVED, FAILED, TIMEOUT)
12. WHEN the Database_Diagram is created, THE Documentation_System SHALL include a performance index `idx_status_history_subscription` on subscription_status_history(subscription_id, changed_at) for efficient timeline queries

### Requirement 3: Class Diagram Creation

**User Story:** As a software architect, I want a class diagram in Draw.io format, so that I can visualize domain classes, use cases, ports, and adapters with correct dependency directions following hexagonal architecture.

#### Acceptance Criteria

1. THE Documentation_System SHALL produce a Draw.io file at `docs/diagrams/class-diagram-subscription-system.drawio`
2. WHEN the Class_Diagram is created, THE Documentation_System SHALL include Domain_Layer classes with UML stereotypes: User (entity), Subscription (entity), Plan (<<enum>>), SubscriptionStatus (<<enum>>), PaymentMethod (entity), PaymentAttempt (entity), Money (<<value object>>), SubscriptionId (<<value object>>), UserId (<<value object>>), PaymentResult (<<sealed>>), DomainEvent (<<sealed>>), SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended
3. WHEN the Class_Diagram is created, THE Documentation_System SHALL include Application_Layer use cases: CreateUserUseCase, CreateSubscriptionUseCase, RenewExpiredSubscriptionsUseCase, CancelSubscriptionUseCase, GetActiveSubscriptionUseCase
4. WHEN the Class_Diagram is created, THE Documentation_System SHALL include port interfaces with <<interface>> stereotype: UserRepositoryPort, SubscriptionRepositoryPort, PaymentGatewayPort, SubscriptionCachePort, EventPublisherPort, LockManagerPort
5. WHEN the Class_Diagram is created, THE Documentation_System SHALL include Adapter_Layer classes with <<adapter>> stereotype: SubscriptionController, RenewSubscriptionScheduler, JpaSubscriptionRepositoryAdapter, JpaUserRepositoryAdapter, PaymentProviderAdapter, RedisSubscriptionCacheAdapter, KafkaEventPublisherAdapter
6. WHEN the Class_Diagram is created, THE Documentation_System SHALL represent relationships using standard UML arrows: dashed dependency arrows from Controllers to Use Cases, dashed dependency arrows from Use Cases to Ports, dashed dependency arrows from Use Cases to Domain classes, realization arrows (dashed with hollow triangle) from Adapters to Ports they implement, and association arrows from Domain entities to their DomainEvent subtypes
7. THE Class_Diagram SHALL enforce that no dependency or association arrows originate from any Domain_Layer class toward any Adapter_Layer class, Spring framework class, database class, or messaging infrastructure class
8. WHEN the Class_Diagram is created, THE Documentation_System SHALL visually group classes into three distinct layer packages (Domain_Layer, Application_Layer, Adapter_Layer), each using the color coding defined in Requirement 6

### Requirement 4: Business Rules Documentation

**User Story:** As a software architect, I want a comprehensive business rules document in Markdown, so that I can have a single source of truth for all subscription system rules before implementation.

#### Acceptance Criteria

1. THE Documentation_System SHALL produce a Markdown file at `docs/business-rules.md`
2. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document all available plans with prices: BASICO (R$19.90/month), PREMIUM (R$39.90/month), FAMILIA (R$59.90/month)
3. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document all subscription states with definitions and a complete state transition matrix specifying: ATIVA may transition to CANCELADA, EXPIRADA, or SUSPENSA; PENDENTE_PAGAMENTO may transition to ATIVA or SUSPENSA; SUSPENSA may transition to ATIVA or CANCELADA; EXPIRADA may transition to ATIVA or CANCELADA; CANCELADA is a terminal state with no outgoing transitions
4. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document the constraint of one active subscription per user at a time, specifying that "active" includes subscriptions with status ATIVA or PENDENTE_PAGAMENTO
5. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document the automatic renewal rule: triggered on expiration day, process payment via payment gateway, if approved then renew subscription for 1 month and reset failed_attempts to 0, if rejected then increment failed_attempts by 1 and transition status to PENDENTE_PAGAMENTO
6. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document the payment retry rule: up to 3 attempts per billing cycle with idempotency key format `subscription:{subscriptionId}:billing-cycle:{expirationDate}`, transition subscription to SUSPENSA after the 3rd consecutive failed attempt in the same billing cycle
7. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document the explicit payment flow lifecycle with database state transitions at each step: (a) scheduler detects expiration → subscription status changes from ATIVA to PENDENTE_PAGAMENTO, insert into subscription_status_history; (b) payment request sent → insert payment_attempts with status PROCESSING; (c) payment approved → update payment_attempts.status to APPROVED, update subscription status to ATIVA, advance expiration_date +1 month, reset failed_attempts to 0, insert into subscription_status_history with reason 'payment_approved'; (d) payment rejected → update payment_attempts.status to FAILED, increment failed_attempts, keep subscription as PENDENTE_PAGAMENTO, insert into subscription_status_history; (e) 3rd consecutive failure → update subscription status to SUSPENSA, insert into subscription_status_history with reason 'payment_failed_3x'
7. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document the cancellation rule: upon user-initiated cancellation, record cancel_requested_at timestamp, retain access and status ATIVA until expiration_date, then transition to CANCELADA instead of triggering renewal
8. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document idempotency rules: one idempotency key per subscription per billing cycle, no duplicate charges for the same cycle, duplicate payment requests with the same idempotency key shall return the original result without reprocessing
9. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document concurrency rules: optimistic locking via version column on subscriptions table, partial unique index on (user_id) WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO'), distributed lock for the renewal scheduler to prevent parallel execution, FOR UPDATE SKIP LOCKED for batch renewal queries to avoid row-level contention
10. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document cache rules: cache active subscription lookups by user_id with a TTL between 30 seconds and 5 minutes, invalidate cache entry on subscription creation, renewal, cancellation, and suspension events
11. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document domain events: SubscriptionCreated, SubscriptionRenewed, SubscriptionCanceled, SubscriptionSuspended, PaymentFailed, PaymentApproved, specifying that each event includes at minimum subscription_id, event_type, and timestamp
12. WHEN the Business_Rules_Document is created, THE Documentation_System SHALL document resilience rules for payment integration: circuit breaker that opens after 5 to 10 consecutive failures, retry with exponential backoff for a maximum of 3 attempts, timeout between 5 and 30 seconds per payment request, fallback behavior that marks the attempt as failed and schedules for retry without blocking the renewal batch

### Requirement 5: Architecture Decision Records

**User Story:** As a software architect, I want architecture decision records documented in Markdown, so that I can track the rationale behind key architectural choices for future reference.

#### Acceptance Criteria

1. THE Documentation_System SHALL produce a Markdown file at `docs/architecture-decisions.md`
2. WHEN the ADR_Document is created, THE Documentation_System SHALL document the decision to use a single modularized microservice with Hexagonal_Architecture for initial delivery, including at least 2 rationale points and at least 2 trade-offs (pros and cons)
3. WHEN the ADR_Document is created, THE Documentation_System SHALL document the future evolution vision with possible separation into: subscription-service, payment-service, billing-scheduler-service, notification-service, including criteria that would trigger each separation
4. WHEN the ADR_Document is created, THE Documentation_System SHALL document the technology choices: Java 25, Spring Boot, PostgreSQL, Redis/Caffeine, Resilience4j, Kafka/RabbitMQ (optional), Docker
5. WHEN the ADR_Document is created, THE Documentation_System SHALL document the GCP deployment strategy: Cloud Run, Cloud SQL, Memorystore, Pub/Sub, Secret Manager, Cloud Build, Artifact Registry, Cloud Logging/Monitoring
6. WHEN the ADR_Document is created, THE Documentation_System SHALL document the cache strategy decision: start with Caffeine for simplicity, evolve to Redis/Memorystore for multi-instance deployment
7. WHEN the ADR_Document is created, THE Documentation_System SHALL document the concurrency strategy: combination of transactions, optimistic locking, partial unique index, idempotency key, and optional distributed lock
8. WHEN the ADR_Document is created, THE Documentation_System SHALL document the event strategy: domain events, outbox pattern for reliability, subscription_events table for persistence before publishing
9. WHEN the ADR_Document is created, THE Documentation_System SHALL document the cloud-agnostic strategy decision: treat cloud infrastructure as an interchangeable adapter layer, isolate all cloud-specific SDKs and configurations in the Adapter_Layer, define Outbound_Port interfaces for each infrastructure concern (database, cache, messaging, observability, secrets), select GCP as the V1 implementation with AWS and Azure documented as viable future alternatives, and specify that migration to another provider requires only new adapter implementations without changes to Domain_Layer or Application_Layer
10. WHEN the ADR_Document is created, THE Documentation_System SHALL format each ADR with a sequential numeric identifier (ADR-001, ADR-002, etc.) and the following mandatory sections each containing at least one paragraph of content: Title, Status (one of: Proposed, Accepted, Deprecated, Superseded), Context, Decision, Consequences
11. THE ADR_Document SHALL contain exactly 8 ADRs corresponding to criteria 2 through 9, each following the format defined in criterion 10

### Requirement 6: Diagram Quality and Consistency

**User Story:** As a software architect, I want all diagrams to follow consistent visual standards, so that the documentation is professional and easy to understand.

#### Acceptance Criteria

1. THE Documentation_System SHALL use exactly 4 distinct colors consistently across all diagrams to distinguish layers: one color for Domain_Layer elements, a different color for Application_Layer elements, a different color for Adapter_Layer elements, and a different color for External systems, where the same color mapping is applied identically in the Architecture_Diagram, Class_Diagram, and Database_Diagram
2. THE Documentation_System SHALL label all connections and relationships in the diagrams with a description that identifies the nature of the interaction (e.g., "implements", "depends on", "persists to", "publishes event", "queries")
3. THE Documentation_System SHALL use standard UML notation in the Class_Diagram for classes, interfaces, inheritance, and composition, including the <<interface>> and <<adapter>> stereotypes where applicable
4. THE Documentation_System SHALL use standard database diagram notation in the Database_Diagram for primary keys, foreign keys, and indexes, including cardinality indicators (1:1, 1:N) on all relationship lines
5. THE Architecture_Diagram SHALL group related components into visually bounded regions by layer (one region per hexagonal layer) and by deployment target (one region for current architecture, one region for GCP deployment vision, one region for future multi-service evolution)
6. THE Documentation_System SHALL use Portuguese (Brazilian) for business domain terms (plan names, subscription states, business rule labels) and English for technical architecture terms (layer names, pattern names, technology names, UML stereotypes) in all diagrams and documents
7. IF a connection or relationship in any diagram has no label, THEN THE Documentation_System SHALL be considered non-compliant with the labeling standard

### Requirement 7: Cloud Provider Portability

**User Story:** As a software architect, I want the architecture to treat cloud infrastructure as an interchangeable adapter layer, so that the system can migrate from GCP to AWS or Azure in the future without rewriting domain or application logic.

#### Acceptance Criteria

1. THE Documentation_System SHALL design the architecture treating all cloud infrastructure services as interchangeable adapters behind Outbound_Port interfaces defined in the Application_Layer
2. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL represent a generic "Cloud_Infrastructure_Layer" boundary containing abstract service categories (Managed Database, Managed Cache, Messaging Service, Observability Platform, Secrets Management, Container Runtime) with GCP services labeled as the V1 concrete implementation and annotations listing AWS and Azure as documented future alternatives
3. WHEN the Architecture_Diagram is created, THE Documentation_System SHALL represent each cloud-specific integration (managed database, cache, messaging, observability, secrets) as an outbound port implementation in the Adapter_Layer that can be swapped independently without affecting Domain_Layer or Application_Layer components
4. WHEN the ADR_Document is created, THE Documentation_System SHALL include a dedicated ADR documenting the decision to remain cloud-agnostic, specifying: GCP as the V1 choice with rationale, the principle that all cloud-specific code is isolated in adapters, the criteria for evaluating alternative providers, and the expected migration effort (new adapters only, no domain or application changes)
5. THE Architecture_Diagram and Class_Diagram SHALL enforce that no cloud-provider-specific concepts (GCP SDK classes, AWS SDK classes, Azure SDK classes, provider-specific annotations, or provider-specific configuration types) appear in the Domain_Layer or Application_Layer visual representation
6. WHEN the Class_Diagram is created, THE Documentation_System SHALL include for each infrastructure concern (persistence, cache, messaging, observability, secrets) an Outbound_Port interface in the Application_Layer and at least one GCP-specific adapter implementation in the Adapter_Layer, visually demonstrating the port/adapter separation that enables provider swapping
7. THE Documentation_System SHALL document in both the Architecture_Diagram and ADR_Document that migrating to a different Cloud_Provider requires implementing new adapters for the existing Outbound_Port interfaces without modifications to Domain_Layer or Application_Layer code
