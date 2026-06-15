# Subscription Service — Globo Streaming

Sistema de Gestão de Assinaturas para a plataforma Globo Streaming. Implementado em **Java 25** com **Spring Boot 3.5**, seguindo **Arquitetura Hexagonal** (Ports & Adapters).

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 25 |
| Framework | Spring Boot 3.5 |
| Banco de dados | PostgreSQL 16 |
| Migrações | Liquibase (SQL changelogs) |
| Cache | Caffeine (in-memory) |
| Resiliência | Resilience4j (CircuitBreaker, Retry, Timeout) |
| Testes unitários | JUnit 5 |
| Testes de propriedade | jqwik |
| Testes de integração | Testcontainers + PostgreSQL |
| Testes de arquitetura | ArchUnit |
| Cobertura | JaCoCo |
| Build | Maven (wrapper incluído) |

## Pré-requisitos

- **Java 25** (ou superior)
- **Docker** e **Docker Compose** (para PostgreSQL local)

## Setup do Banco de Dados

Suba o PostgreSQL local via Docker Compose:

```bash
docker compose up -d
```

Isso inicia um container PostgreSQL 16 com:
- Host: `localhost:5432`
- Database: `subscription_db`
- User: `huggooliveira`
- Password: `Mv123456`

Para parar o banco:

```bash
docker compose down
```

Para parar e remover os dados persistidos:

```bash
docker compose down -v
```

## Build

```bash
./mvnw clean install
```

Isso compila o projeto, executa testes unitários e de propriedade, e gera o artefato JAR.

## Executar a Aplicação

```bash
./mvnw spring-boot:run
```

A aplicação inicia no perfil `local` por padrão, conectando ao PostgreSQL configurado no Docker Compose. O Liquibase executa as migrações automaticamente na inicialização.

A aplicação estará disponível em: `http://localhost:8080`

## Executar Testes

### Testes unitários e de propriedade (padrão)

```bash
./mvnw test
```

### Testes de integração (Testcontainers — requer Docker)

```bash
./mvnw verify -P integration-tests
```

### Todos os testes com relatório de cobertura

```bash
./mvnw verify -P all-tests
```

O relatório de cobertura JaCoCo é gerado em: `target/site/jacoco/index.html`

## Endpoints da API

### Health Check

```bash
# curl
curl http://localhost:8080/actuator/health

# httpie
http GET :8080/actuator/health
```

Resposta:
```json
{"status": "UP"}
```

---

### Criar Usuário

**POST** `/api/v1/users`

```bash
# curl
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name": "João Silva", "email": "joao@email.com"}'

# httpie
http POST :8080/api/v1/users name="João Silva" email="joao@email.com"
```

Resposta (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "João Silva",
  "email": "joao@email.com",
  "active": true,
  "createdAt": "2025-01-15T10:30:00Z"
}
```

---

### Criar Assinatura

**POST** `/api/v1/subscriptions`

Planos disponíveis (seeded no banco):
| Plano | Preço Mensal |
|-------|-------------|
| BASICO | R$ 19,90 |
| PREMIUM | R$ 39,90 |
| FAMILIA | R$ 59,90 |

```bash
# curl
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"userId": "550e8400-e29b-41d4-a716-446655440000", "planId": "PLAN_UUID_HERE"}'

# httpie
http POST :8080/api/v1/subscriptions \
  userId="550e8400-e29b-41d4-a716-446655440000" \
  planId="PLAN_UUID_HERE"
```

Resposta (201 Created):
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "planName": "BASICO",
  "price": 19.90,
  "currency": "BRL",
  "status": "ATIVA",
  "startDate": "2025-01-15",
  "expirationDate": "2025-02-15",
  "createdAt": "2025-01-15T10:35:00Z"
}
```

---

### Consultar Assinatura Ativa

**GET** `/api/v1/subscriptions/active?userId={userId}`

```bash
# curl
curl "http://localhost:8080/api/v1/subscriptions/active?userId=550e8400-e29b-41d4-a716-446655440000"

# httpie
http GET :8080/api/v1/subscriptions/active userId==550e8400-e29b-41d4-a716-446655440000
```

Resposta (200 OK):
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "planName": "BASICO",
  "price": 19.90,
  "currency": "BRL",
  "status": "ATIVA",
  "startDate": "2025-01-15",
  "expirationDate": "2025-02-15",
  "createdAt": "2025-01-15T10:35:00Z"
}
```

---

### Cancelar Assinatura

**DELETE** `/api/v1/subscriptions/{id}/cancel`

```bash
# curl
curl -X DELETE http://localhost:8080/api/v1/subscriptions/660e8400-e29b-41d4-a716-446655440001/cancel

# httpie
http DELETE :8080/api/v1/subscriptions/660e8400-e29b-41d4-a716-446655440001/cancel
```

Resposta (200 OK):
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "planName": "BASICO",
  "price": 19.90,
  "currency": "BRL",
  "status": "ATIVA",
  "startDate": "2025-01-15",
  "expirationDate": "2025-02-15",
  "cancelRequestedAt": "2025-01-20T14:00:00Z",
  "createdAt": "2025-01-15T10:35:00Z"
}
```

> **Nota:** O status permanece inalterado após o cancelamento. O usuário mantém acesso até a data de expiração.

## Respostas de Erro

| HTTP Status | Cenário |
|------------|---------|
| 400 | Validação de campos falhou (campos obrigatórios, email inválido) |
| 404 | Entidade não encontrada |
| 409 | Conflito de regra de negócio (assinatura ativa já existe, email duplicado) |
| 409 | Conflito de concorrência (optimistic locking) |
| 500 | Erro interno não tratado |

Formato de erro:
```json
{
  "error": "ACTIVE_SUBSCRIPTION_EXISTS",
  "message": "User already has an active subscription",
  "timestamp": "2025-01-15T10:40:00Z"
}
```

## Arquitetura

```
src/main/java/com/globo/subscription/
├── domain/              # Entidades, Value Objects, Enums, Domain Events (zero deps externas)
├── application/         # Use Cases, Port Interfaces, Exceções de domínio
└── adapter/
    ├── inbound/
    │   ├── rest/        # Controllers, DTOs, Mappers, Exception Handler
    │   └── scheduler/   # Renewal Scheduler
    └── outbound/
        ├── persistence/ # JPA Entities, Repositories, Mappers
        ├── cache/       # Caffeine Cache Adapters
        ├── payment/     # Mock Payment Gateway + Resilience4j
        ├── event/       # Local Event Publisher (outbox pattern)
        └── lock/        # In-Memory Lock Manager
```

## Perfis de Configuração

| Perfil | Uso | Ativação |
|--------|-----|----------|
| `local` | Desenvolvimento local (padrão) | Automático |
| `test` | Testes de integração com Testcontainers | Via `@ActiveProfiles("test")` |

## Observabilidade

- **Actuator endpoints:** `/actuator/health`, `/actuator/metrics`, `/actuator/info`
- **Logging:** JSON estruturado com traceId, subscriptionId e userId via MDC
- **Métricas Micrometer:** execução de use cases, outcomes de pagamento, cache hit/miss, batch de renovação

## Licença

Projeto interno — Globo Streaming.
