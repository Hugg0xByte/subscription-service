# Design Document

## Overview

Este design detalha a abordagem para criar a documentação arquitetural completa do Sistema de Gestão de Assinaturas Globo Streaming. O foco é exclusivamente em documentação e diagramas — sem implementação de código. A solução utiliza o Draw.io Power do Kiro para gerar diagramas profissionais e Markdown para documentação textual.

A arquitetura é projetada como **cloud-agnostic**: GCP é o provider V1, mas todo o design garante que Domain_Layer e Application_Layer não contenham referências a cloud providers específicos. A Cloud_Infrastructure_Layer é tratada como uma camada de adaptadores intercambiáveis atrás de Outbound_Port interfaces.

### Design Principles

1. **Documentation-as-Code**: Toda documentação versionada no repositório
2. **Cloud Agnosticism**: Infraestrutura cloud como adaptadores intercambiáveis
3. **Hexagonal Purity**: Domínio e aplicação livres de dependências de infraestrutura
4. **Visual Consistency**: Padrão de cores e notações consistente entre todos os diagramas
5. **Bilingual Convention**: Termos de negócio em PT-BR, termos técnicos em EN

## Architecture

### Documentation-as-Code Approach

A documentação será criada diretamente no repositório, versionada junto ao código futuro:

```
docs/
├── diagrams/
│   ├── architecture-subscription-system.drawio
│   ├── class-diagram-subscription-system.drawio
│   └── database-subscription-system.drawio
├── business-rules.md
└── architecture-decisions.md
```

### Diagram Creation Strategy

Todos os diagramas Draw.io serão criados usando o `aws-drawio` Power disponível no Kiro. Cada diagrama será construído com:

1. **Nodes** — componentes, classes, tabelas, serviços
2. **Edges** — relacionamentos, dependências, fluxos de dados
3. **Groups** — para representar camadas, boundaries e regiões de deploy
4. **Styles** — cores diferenciadas por camada/tipo seguindo o padrão definido

### Color Coding Standard (Requirement 6)

Aplicado consistentemente em TODOS os diagramas:

| Layer | Color | Hex | Usage |
|-------|-------|-----|-------|
| Domain_Layer | Orange | `#FFE0B2` | Entities, Value Objects, Domain Events |
| Application_Layer | Yellow | `#FFF9C4` | Use Cases, Ports, Commands, Queries |
| Adapter_Layer | Green | `#C8E6C9` | Controllers, Repositories, Adapters |
| External/Infrastructure | Purple/Gray | `#E1BEE7` / `#F5F5F5` | Databases, Caches, Providers, Cloud |

### Architecture Diagram Design

O diagrama de arquitetura será organizado em zonas visuais com bounded regions:

```
┌─────────────────────────────────────────────────────────────────┐
│  CURRENT ARCHITECTURE (Single Modular Microservice)             │
│                                                                 │
│  ┌──────────┐                                                   │
│  │  Client  │──→ API Gateway ──→┌──────────────────────────┐   │
│  └──────────┘                   │  Subscription Service     │   │
│                                 │  ┌────────────────────┐   │   │
│                                 │  │ Inbound Adapters   │   │   │
│                                 │  │ (REST, Scheduler)  │   │   │
│                                 │  ├────────────────────┤   │   │
│                                 │  │ Application Layer  │   │   │
│                                 │  │ (Use Cases, Ports) │   │   │
│                                 │  ├────────────────────┤   │   │
│                                 │  │ Domain Model       │   │   │
│                                 │  │ (Entities, Events) │   │   │
│                                 │  ├────────────────────┤   │   │
│                                 │  │ Outbound Ports     │   │   │
│                                 │  │ (Interfaces)       │──┼───→ Cloud Infrastructure
│                                 │  └────────────────────┘   │   │
│                                 └──────────────────────────┘   │
│                                                                 │
│  Observability: Logs ←→ Metrics ←→ Traces                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  CLOUD INFRASTRUCTURE (Generic Boundary)                        │
│                                                                 │
│  ┌──────────────────┐  V1: GCP            Alternatives:        │
│  │ Managed Database  │  Cloud SQL          AWS RDS / Azure SQL  │
│  │ Managed Cache     │  Memorystore        ElastiCache / Azure  │
│  │ Messaging Service │  Pub/Sub            SQS+SNS / Service Bus│
│  │ Observability     │  Cloud Logging      CloudWatch / Monitor │
│  │ Secrets Mgmt      │  Secret Manager     Secrets Mgr / Vault  │
│  │ Container Runtime │  Cloud Run          ECS Fargate / ACA    │
│  └──────────────────┘                                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  FUTURE EVOLUTION (Multi-Service Vision)                        │
│                                                                 │
│  subscription-service │ payment-service │ billing-scheduler     │
│  notification-service                                           │
└─────────────────────────────────────────────────────────────────┘
```

**Cloud-Agnostic Representation (Requirement 7):**
- A bounded region "Cloud Infrastructure" é genérica — mostra categorias abstratas de serviço
- GCP é rotulado como "V1 Implementation" dentro desta região
- AWS e Azure aparecem como anotações "Future Alternatives" ao lado de cada serviço
- Todas as conexões do Subscription Service para Cloud Infrastructure passam por Outbound_Port interfaces explicitamente rotuladas
- Nenhum SDK ou conceito cloud-specific aparece dentro do serviço

**Main Request Flow:**
```
Client → REST API → Use Case → Domain → Payment Port → Payment Provider
                       │          │
                       │          → Repository Port → Managed Database
                       → Cache Port → Managed Cache
                       → Event Publisher → Messaging Service
```

**Renewal Flow:**
```
Scheduler → RenewExpiredSubscriptionsUseCase → SubscriptionRepository
    → PaymentGateway → Update Subscription → Publish Event
```

### Class Diagram Design — Port/Adapter Pairs for Cloud Portability

O diagrama de classes demonstra visualmente o padrão port/adapter que permite troca de provider:

```
┌─────────────────────────────────────────────────────────────┐
│ APPLICATION LAYER (Yellow)                                   │
│                                                             │
│  <<interface>>              <<interface>>                    │
│  SubscriptionRepositoryPort PaymentGatewayPort              │
│  <<interface>>              <<interface>>                    │
│  SubscriptionCachePort      EventPublisherPort              │
│  <<interface>>              <<interface>>                    │
│  LockManagerPort            ObservabilityPort               │
│  <<interface>>                                              │
│  SecretsPort                                                │
└────────────────────────┬────────────────────────────────────┘
                         │ implements (realization arrow)
┌────────────────────────▼────────────────────────────────────┐
│ ADAPTER LAYER (Green)                                        │
│                                                             │
│  <<adapter>> GcpCloudSqlRepositoryAdapter                   │
│  <<adapter>> GcpMemorystoreCacheAdapter                     │
│  <<adapter>> GcpPubSubEventPublisherAdapter                 │
│  <<adapter>> GcpSecretManagerAdapter                        │
│  <<adapter>> GcpCloudLoggingObservabilityAdapter            │
│  <<adapter>> PaymentProviderAdapter                         │
│                                                             │
│  [Future: AwsRdsRepositoryAdapter, AzureSqlRepositoryAdapter│
│   AwsElastiCacheAdapter, AwsSqsEventPublisherAdapter, etc.] │
└─────────────────────────────────────────────────────────────┘
```

**Key Rules for Class Diagram:**
- NO dependency arrows from Domain_Layer → Adapter_Layer
- NO cloud SDK references in Domain_Layer or Application_Layer
- Each Outbound_Port has at least one GCP-specific adapter
- Realization arrows (dashed + hollow triangle) from adapters to ports
- Port/adapter pairs explicitly demonstrate provider swapping capability

### Database Diagram Design

O diagrama de banco será organizado com:
- Tabelas representadas como retângulos com header (nome) e body (colunas com tipo)
- Colunas com tipo de dado, constraints (PK, FK, NOT NULL, UNIQUE)
- Linhas de relacionamento com cardinalidade (1:N)
- Seção dedicada para indexes e constraints especiais
- Color coding: Domain-related tables in orange tint, infrastructure tables in gray

**Nova tabela: subscription_status_history**

Tabela dedicada para rastrear todas as transições de status da assinatura, eliminando a necessidade de parsear eventos JSONB para entender o ciclo de vida:

```
subscription_status_history (
  id UUID PK,
  subscription_id UUID FK NOT NULL → subscriptions.id,
  from_status VARCHAR,         -- nullable para criação inicial
  to_status VARCHAR NOT NULL,
  reason VARCHAR NOT NULL,     -- 'payment_approved', 'payment_failed_3x', 'user_canceled', etc.
  changed_by VARCHAR NOT NULL, -- 'scheduler', 'user', 'admin', 'payment_callback'
  changed_at TIMESTAMP NOT NULL
)
IDX: idx_status_history_subscription(subscription_id, changed_at)
```

**Ciclo de vida do payment_attempts.status:**

```
PROCESSING → APPROVED (pagamento confirmado)
PROCESSING → FAILED (pagamento rejeitado)
PROCESSING → TIMEOUT (sem resposta do provider)
```

Cada payment_attempt é imutável após atingir status terminal.

**Fluxo explícito de pagamento (database state transitions):**

```
1. Scheduler detecta vencimento
   → UPDATE subscriptions SET status='PENDENTE_PAGAMENTO'
   → INSERT subscription_status_history (from='ATIVA', to='PENDENTE_PAGAMENTO', reason='expiration_reached', changed_by='scheduler')

2. Envia para pagamento
   → INSERT payment_attempts (status='PROCESSING', attempt_number=N)

3a. Pagamento APROVADO
   → UPDATE payment_attempts SET status='APPROVED', processed_at=now()
   → UPDATE subscriptions SET status='ATIVA', expiration_date=+1mês, failed_attempts=0
   → INSERT subscription_status_history (from='PENDENTE_PAGAMENTO', to='ATIVA', reason='payment_approved', changed_by='payment_callback')

3b. Pagamento REJEITADO
   → UPDATE payment_attempts SET status='FAILED', processed_at=now()
   → UPDATE subscriptions SET failed_attempts=failed_attempts+1
   → INSERT subscription_status_history (from='PENDENTE_PAGAMENTO', to='PENDENTE_PAGAMENTO', reason='payment_failed', changed_by='payment_callback')

3c. 3ª falha consecutiva
   → UPDATE subscriptions SET status='SUSPENSA', suspended_at=now()
   → INSERT subscription_status_history (from='PENDENTE_PAGAMENTO', to='SUSPENSA', reason='payment_failed_3x', changed_by='scheduler')
```

**Decisão: PostgreSQL vs MongoDB para histórico**

Mantemos PostgreSQL para o histórico de status porque:
- Schema fixo e previsível (não precisa de flexibilidade de documento)
- Queries SQL com JOIN para relatórios são mais eficientes
- Evita complexidade operacional de manter dois bancos
- subscription_events com JSONB já cobre payloads variáveis quando necessário
- Se volume crescer muito, evolução natural é event store dedicado (não Mongo)

### ADR Document Structure — 8 ADRs

O documento de ADRs conterá exatamente 8 registros:

| ADR | Title | Scope |
|-----|-------|-------|
| ADR-001 | Single Modularized Microservice | Architecture style decision |
| ADR-002 | Future Multi-Service Evolution | Evolution criteria |
| ADR-003 | Technology Stack | Java 25, Spring Boot, etc. |
| ADR-004 | GCP Deployment Strategy | Cloud Run, Cloud SQL, etc. |
| ADR-005 | Cache Strategy | Caffeine → Redis evolution |
| ADR-006 | Concurrency Strategy | Locking, idempotency |
| ADR-007 | Event Strategy | Outbox pattern, domain events |
| ADR-008 | Cloud-Agnostic Strategy | Provider portability |

**ADR-008 (new) specifics:**
- Context: Why cloud-agnostic matters for a streaming service
- Decision: All cloud services behind Outbound_Port interfaces, GCP as V1
- Consequences: Slightly more abstraction upfront, but migration requires only new adapters
- Includes criteria for evaluating alternative providers
- Documents expected migration effort: new adapters only, zero Domain/Application changes

Each ADR follows the template:
```markdown
## ADR-XXX: Title

**Status:** Accepted

### Context
[Problem or need - at least one paragraph]

### Decision
[What was decided - at least one paragraph]

### Consequences
[Trade-offs and implications - at least one paragraph]
```

### Business Rules Document Structure

Organized sections:
1. Visão geral do sistema
2. Planos e preços (BASICO, PREMIUM, FAMILIA)
3. Ciclo de vida da assinatura (state machine diagram in text)
4. Regras de pagamento e renovação (3 attempts, idempotency)
5. Regras de concorrência (optimistic locking, unique index, distributed lock)
6. Regras de cache (TTL, invalidation triggers)
7. Eventos de domínio (SubscriptionCreated, Renewed, Canceled, Suspended, PaymentFailed, PaymentApproved)
8. Regras de resiliência (circuit breaker, retry, timeout, fallback)

## Components and Interfaces

### Deliverable Components

| # | Component | Type | Path |
|---|-----------|------|------|
| 1 | Architecture Diagram | Draw.io | `docs/diagrams/architecture-subscription-system.drawio` |
| 2 | Class Diagram | Draw.io | `docs/diagrams/class-diagram-subscription-system.drawio` |
| 3 | Database Diagram | Draw.io | `docs/diagrams/database-subscription-system.drawio` |
| 4 | Business Rules | Markdown | `docs/business-rules.md` |
| 5 | ADR Document | Markdown | `docs/architecture-decisions.md` |

### Architecture Diagram Interfaces (Visual)

**Inbound Adapters (entry points):**
- `SubscriptionController` — REST API endpoints
- `RenewSubscriptionScheduler` — Cron-triggered batch renewal

**Outbound Port Interfaces (Application_Layer → Cloud Infrastructure):**

| Port Interface | Responsibility | V1 GCP Adapter | Future Alternatives |
|----------------|---------------|----------------|---------------------|
| `SubscriptionRepositoryPort` | Persist/query subscriptions | Cloud SQL (PostgreSQL) | AWS RDS, Azure SQL |
| `UserRepositoryPort` | Persist/query users | Cloud SQL (PostgreSQL) | AWS RDS, Azure SQL |
| `PaymentGatewayPort` | Process payments | External Provider | External Provider |
| `SubscriptionCachePort` | Cache active subscriptions | Memorystore (Redis) | ElastiCache, Azure Cache |
| `EventPublisherPort` | Publish domain events | Pub/Sub | SQS+SNS, Service Bus |
| `LockManagerPort` | Distributed locking | Cloud Memorystore | DynamoDB Lock, Azure Blob Lease |
| `ObservabilityPort` | Metrics, logs, traces | Cloud Logging/Monitoring | CloudWatch, Azure Monitor |
| `SecretsPort` | Secret retrieval | Secret Manager | AWS Secrets Manager, Azure Key Vault |

### Class Diagram Component Groups

**Domain_Layer (Orange):**
- Entities: `User`, `Subscription`, `PaymentMethod`, `PaymentAttempt`
- Value Objects: `Money`, `SubscriptionId`, `UserId`
- Enums: `Plan`, `SubscriptionStatus`
- Sealed Types: `PaymentResult`, `DomainEvent`
- Events: `SubscriptionCreated`, `SubscriptionRenewed`, `SubscriptionCanceled`, `SubscriptionSuspended`

**Application_Layer (Yellow):**
- Use Cases: `CreateUserUseCase`, `CreateSubscriptionUseCase`, `RenewExpiredSubscriptionsUseCase`, `CancelSubscriptionUseCase`, `GetActiveSubscriptionUseCase`
- Ports (<<interface>>): `UserRepositoryPort`, `SubscriptionRepositoryPort`, `PaymentGatewayPort`, `SubscriptionCachePort`, `EventPublisherPort`, `LockManagerPort`, `ObservabilityPort`, `SecretsPort`

**Adapter_Layer (Green):**
- Inbound: `SubscriptionController`, `RenewSubscriptionScheduler`
- Outbound (GCP V1): `GcpCloudSqlSubscriptionRepositoryAdapter`, `GcpCloudSqlUserRepositoryAdapter`, `GcpMemorystoreCacheAdapter`, `GcpPubSubEventPublisherAdapter`, `GcpSecretManagerAdapter`, `GcpCloudLoggingObservabilityAdapter`, `PaymentProviderAdapter`

### Database Diagram Component Groups

**Tables:**
- `users` — 6 columns, PK on id, UNIQUE on email
- `subscriptions` — 12 columns, PK on id, FK to users, partial unique index, performance index
- `subscription_status_history` — 7 columns, PK on id, FK to subscriptions, performance index on (subscription_id, changed_at)
- `payment_methods` — 7 columns, PK on id, FK to users
- `payment_attempts` — 11 columns, PK on id, FK to subscriptions, UNIQUE on idempotency_key, status lifecycle (PROCESSING → APPROVED/FAILED/TIMEOUT)
- `subscription_events` — 6 columns, PK on id, FK to subscriptions

**Relationships (all many-to-one):**
- `subscriptions.user_id` → `users.id`
- `subscription_status_history.subscription_id` → `subscriptions.id`
- `payment_methods.user_id` → `users.id`
- `payment_attempts.subscription_id` → `subscriptions.id`
- `subscription_events.subscription_id` → `subscriptions.id`

## Data Models

### Architecture Diagram Data Model

Each visual element in the architecture diagram maps to:

```
Node {
  id: string
  label: string
  layer: "domain" | "application" | "adapter" | "external" | "infrastructure"
  style: { fillColor: string, strokeColor: string, shape: string }
  group?: string  // parent bounded region
}

Edge {
  source: Node.id
  target: Node.id
  label: string  // REQUIRED - nature of interaction
  style: { dashed?: boolean, endArrow: string }
}

Group {
  id: string
  label: string
  type: "current-architecture" | "cloud-infrastructure" | "future-evolution"
  children: Node[]
}
```

### Class Diagram Data Model

Each element follows UML semantics:

```
ClassNode {
  name: string
  stereotype: "<<interface>>" | "<<adapter>>" | "<<enum>>" | "<<value object>>" | "<<sealed>>" | none
  layer: "Domain_Layer" | "Application_Layer" | "Adapter_Layer"
  attributes?: string[]
  methods?: string[]
}

Relationship {
  source: ClassNode.name
  target: ClassNode.name
  type: "dependency" | "realization" | "association" | "composition"
  label: string  // REQUIRED
}
```

### Database Diagram Data Model

```
Table {
  name: string
  columns: Column[]
  indexes: Index[]
  constraints: Constraint[]
}

Column {
  name: string
  type: string  // e.g., "UUID", "VARCHAR", "NUMERIC(10,2)"
  nullable: boolean
  default?: string
  isPK: boolean
  isFK: boolean
  fkTarget?: string  // e.g., "users.id"
}

Index {
  name: string
  columns: string[]
  unique: boolean
  partial?: string  // WHERE clause for partial indexes
}

// Payment Attempts Status Lifecycle
PaymentAttemptStatus = "PROCESSING" | "APPROVED" | "FAILED" | "TIMEOUT"
// Terminal states: APPROVED, FAILED, TIMEOUT (immutable after reaching)
// Transitions: PROCESSING → APPROVED | FAILED | TIMEOUT

// Subscription Status History tracks all transitions
StatusHistoryEntry {
  subscription_id: UUID
  from_status: SubscriptionStatus | null  // null for initial creation
  to_status: SubscriptionStatus
  reason: string  // 'payment_approved', 'payment_failed_3x', 'user_canceled', 'expiration_reached', 'manual_reactivation'
  changed_by: string  // 'scheduler', 'user', 'admin', 'payment_callback'
  changed_at: Timestamp
}
```

### ADR Data Model

```
ADR {
  id: string      // "ADR-001" through "ADR-008"
  title: string
  status: "Proposed" | "Accepted" | "Deprecated" | "Superseded"
  context: string   // at least one paragraph
  decision: string  // at least one paragraph
  consequences: string  // at least one paragraph
}
```

### Business Rules Document Data Model

```
BusinessRule {
  section: string
  rules: Rule[]
}

Rule {
  id: string
  description: string
  conditions?: string[]
  actions?: string[]
}

StateTransition {
  from: SubscriptionStatus
  to: SubscriptionStatus[]
  trigger: string
}
```

## Error Handling

Since this spec produces documentation artifacts (not runtime code), "errors" map to documentation quality issues:

### Diagram Creation Errors

| Error Condition | Handling Strategy |
|----------------|-------------------|
| Draw.io Power unavailable | Document diagram structure in Markdown as fallback, retry when Power is available |
| Missing node/edge in diagram | Validate against requirements checklist before finalizing |
| Incorrect dependency direction | Review all arrows against hexagonal rules before saving |
| Color inconsistency between diagrams | Use centralized color reference table during creation |
| Unlabeled connections | Final review pass to ensure all edges have labels |

### Document Content Errors

| Error Condition | Handling Strategy |
|----------------|-------------------|
| Missing ADR section | Validate each ADR against template (Title, Status, Context, Decision, Consequences) |
| Incomplete state transitions | Cross-reference with acceptance criteria 4.3 |
| Portuguese/English mix violation | Review with bilingual glossary |
| Cloud-specific term in Domain/Application | Grep-equivalent visual check: no GCP/AWS/Azure terms in non-adapter areas |

### Validation Checklist

Before marking any deliverable complete:
1. ✅ File exists at specified path
2. ✅ All required elements present (per acceptance criteria)
3. ✅ Color coding consistent across diagrams
4. ✅ All edges/connections labeled
5. ✅ No cloud-specific concepts in Domain_Layer or Application_Layer
6. ✅ Port/adapter pairs complete for all infrastructure concerns
7. ✅ ADR count equals 8

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Note: Although this spec produces documentation artifacts rather than runtime code, the properties below verify structural invariants that must hold across all diagram elements. These are verifiable by inspection of the generated Draw.io XML and Markdown content.

### Property 1: No cloud-specific concepts in Domain or Application layers

*For any* diagram element (node or class) located within the Domain_Layer or Application_Layer boundary, the element's label and attributes SHALL NOT contain any cloud-provider-specific term (GCP, AWS, Azure, Cloud Run, Cloud SQL, Memorystore, Pub/Sub, S3, Lambda, etc.).

**Validates: Requirements 7.5**

### Property 2: All outbound connections pass through port interfaces

*For any* edge connecting from a component inside the Subscription_Service boundary to a component inside the Cloud_Infrastructure_Layer boundary, there SHALL exist an intermediate Outbound_Port node that the edge passes through.

**Validates: Requirements 1.9, 7.3**

### Property 3: Every port has at least one adapter implementation

*For any* Outbound_Port interface defined in the Application_Layer, there SHALL exist at least one adapter class in the Adapter_Layer with a realization arrow pointing to that port.

**Validates: Requirements 7.6**

### Property 4: All diagram edges are labeled

*For any* edge (connection/relationship) in any of the three diagrams, the edge SHALL have a non-empty label describing the nature of the interaction.

**Validates: Requirements 6.2, 6.7**

### Property 5: Color consistency across diagrams

*For any* element belonging to a given layer (Domain, Application, Adapter, External), the fill color used in the Architecture_Diagram, Class_Diagram, and Database_Diagram SHALL be identical.

**Validates: Requirements 6.1**

### Property 6: ADR structural completeness

*For any* ADR entry in the ADR_Document, the entry SHALL contain all five mandatory sections (Title, Status, Context, Decision, Consequences) each with at least one paragraph of content.

**Validates: Requirements 5.10**

## Testing Strategy

### Approach: Example-Based Verification

Property-based testing is **not applicable** for this feature because:
- This is a documentation-only deliverable (no runtime code with inputs/outputs)
- Verification is about file existence and content completeness
- There are no pure functions, data transformations, or algorithms to test with varied inputs
- Checks are deterministic: either the artifact contains the required element or it doesn't

### Verification Strategy

All verification is **example-based** — checking specific, concrete conditions against each acceptance criterion:

#### Architecture Diagram Verification (Requirement 1 + 7)

| Check | What to Verify |
|-------|---------------|
| File exists | `docs/diagrams/architecture-subscription-system.drawio` exists |
| External components | Client, API Gateway, Payment Provider, Message Broker nodes present |
| Hexagonal layers | Inbound Adapters, Application, Domain, Outbound Ports groups present |
| Main flow | Complete path from Client through all layers visible |
| Renewal flow | Scheduler → UseCase → Repository → PaymentGateway path visible |
| Observability | Logs, Metrics, Traces connected to service |
| Cloud Infrastructure boundary | Generic boundary with abstract service categories |
| GCP V1 label | GCP services listed as V1 implementation |
| AWS/Azure annotations | Listed as future alternatives |
| Outbound Port connections | All connections to cloud pass through labeled ports |
| No cloud SDK in domain | Domain/Application groups contain no cloud-specific nodes |
| Bounded regions | Current Architecture, Cloud Infrastructure, Future Evolution regions |

#### Database Diagram Verification (Requirement 2)

| Check | What to Verify |
|-------|---------------|
| File exists | `docs/diagrams/database-subscription-system.drawio` exists |
| Tables present | All 5 tables with correct columns and types |
| Relationships | 4 FK relationships with cardinality |
| Indexes | Partial unique index, performance index, email unique |
| Version column | BIGINT NOT NULL in subscriptions |

#### Class Diagram Verification (Requirement 3 + 7)

| Check | What to Verify |
|-------|---------------|
| File exists | `docs/diagrams/class-diagram-subscription-system.drawio` exists |
| Domain classes | All entities, VOs, enums, sealed types present |
| Application classes | All use cases and port interfaces present |
| Adapter classes | All adapter implementations present |
| Dependency direction | No arrows from Domain → Adapter |
| Stereotypes | <<interface>>, <<adapter>>, <<enum>>, <<value object>> correctly applied |
| Port/adapter pairs | Each infrastructure concern has port + GCP adapter |
| Layer coloring | 3 distinct packages with correct colors |
| No cloud in domain | No GCP/AWS/Azure classes in Domain or Application packages |

#### Business Rules Verification (Requirement 4)

| Check | What to Verify |
|-------|---------------|
| File exists | `docs/business-rules.md` exists |
| Plans documented | BASICO R$19.90, PREMIUM R$39.90, FAMILIA R$59.90 |
| State machine | All 5 states with complete transition matrix |
| Single subscription rule | Constraint documented |
| Renewal rules | Trigger, success path, failure path documented |
| Payment retry | 3 attempts, idempotency key format, suspension trigger |
| Cancellation | Access until expiration documented |
| Concurrency | All 4 mechanisms documented |
| Cache | TTL, invalidation triggers documented |
| Events | All 6 events with minimum fields documented |
| Resilience | Circuit breaker, retry, timeout, fallback documented |

#### ADR Document Verification (Requirement 5 + 7)

| Check | What to Verify |
|-------|---------------|
| File exists | `docs/architecture-decisions.md` exists |
| ADR count | Exactly 8 ADRs present |
| ADR format | Each has Title, Status, Context, Decision, Consequences |
| ADR-008 | Cloud-agnostic strategy documented with GCP rationale, isolation principle, evaluation criteria, migration effort |
| Content depth | Each section has at least one paragraph |

#### Cross-Diagram Consistency (Requirement 6)

| Check | What to Verify |
|-------|---------------|
| Color mapping | Same 4 colors across all 3 diagrams |
| Labels | All connections have descriptive labels |
| UML notation | Standard notation in class diagram |
| DB notation | Standard notation in database diagram |
| Bilingual | PT-BR for business, EN for technical |

### Execution Order

1. Create Architecture Diagram (largest, establishes cloud-agnostic pattern)
2. Create Class Diagram (depends on architecture decisions)
3. Create Database Diagram (independent of other diagrams)
4. Write Business Rules Document (references domain model)
5. Write ADR Document (references all decisions made in diagrams)
6. Final consistency review across all artifacts

