# Subscription Service — Globo Streaming

Sistema de Gestão de Assinaturas para a plataforma Globo Streaming. Implementado em **Java 25** com **Spring Boot 3.5**, seguindo **Arquitetura Hexagonal** (Ports & Adapters).

## 🌐 Ambiente Cloud (GCP)

A aplicação está deployada no Google Cloud Platform e pode ser acessada publicamente:

| Recurso | URL |
|---------|-----|
| **API Base** | https://subscription-service-j4odpghlpq-rj.a.run.app |
| **Health Check** | https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/health |
| **Métricas (Prometheus)** | https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/prometheus |
| **Métricas (JSON)** | https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics |

> **Nota:** O Cloud Run escala para zero quando ocioso. A primeira requisição pode levar ~30s (cold start).

---

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 25 |
| Framework | Spring Boot 3.5.7 |
| Banco de dados | PostgreSQL 16 (Cloud SQL) |
| Messaging | Google Cloud Pub/Sub |
| Migrações | Liquibase (SQL changelogs) |
| Cache | Caffeine (in-memory) |
| Resiliência | Resilience4j (CircuitBreaker, Retry, Timeout) |
| Observabilidade | Micrometer + Prometheus |
| Testes unitários | JUnit 5 + Mockito |
| Testes de propriedade | jqwik (Property-Based Testing) |
| Testes de integração | Testcontainers + PostgreSQL |
| Testes de arquitetura | ArchUnit |
| Cobertura | JaCoCo |
| Deploy | Docker + GCP Cloud Run |
| Build | Maven (wrapper incluído) |

---

## Endpoints da API (Cloud)

### Health Check

```bash
curl -s https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/health | jq .
```

---

### Criar Usuário

**POST** `/api/v1/users`

```bash
curl -s -X POST https://subscription-service-j4odpghlpq-rj.a.run.app/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "João Silva",
    "email": "joao@email.com"
  }' | jq .
```

Resposta (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "João Silva",
  "email": "joao@email.com",
  "active": true,
  "createdAt": "2026-06-16T10:30:00Z"
}
```

---

### Criar Assinatura

**POST** `/api/v1/subscriptions`

| Plano | Preço Mensal |
|-------|-------------|
| BASICO | R$ 19,90 |
| PREMIUM | R$ 39,90 |
| FAMILIA | R$ 59,90 |

```bash
curl -s -X POST https://subscription-service-j4odpghlpq-rj.a.run.app/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER_ID",
    "planId": "PLAN_ID"
  }' | jq .
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
  "startDate": "2026-06-16",
  "expirationDate": "2026-07-16"
}
```

---

### Consultar Assinatura Ativa

**GET** `/api/v1/subscriptions/active?userId={userId}`

```bash
curl -s "https://subscription-service-j4odpghlpq-rj.a.run.app/api/v1/subscriptions/active?userId=USER_ID" | jq .
```

---

### Cancelar Assinatura

**DELETE** `/api/v1/subscriptions/{id}/cancel`

```bash
curl -s -X DELETE "https://subscription-service-j4odpghlpq-rj.a.run.app/api/v1/subscriptions/SUBSCRIPTION_ID/cancel"
```

Resposta: 204 No Content

> O usuário mantém acesso até a data de expiração. O status permanece ATIVA até o fim do ciclo.

---

### Disparar Renovação Manual (fluxo com filas)

**POST** `/api/v1/subscriptions/renewals/trigger`

```bash
curl -s -X POST "https://subscription-service-j4odpghlpq-rj.a.run.app/api/v1/subscriptions/renewals/trigger?batchSize=100" | jq .
```

Este endpoint aciona o fluxo completo com mensageria:
1. Busca assinaturas vencidas
2. Publica na fila `pendente-de-pagamento` (Pub/Sub)
3. PaymentConsumerMock processa e publica resultado na fila `pagamento-processado`
4. PaymentResultListener atualiza a assinatura (ATIVA ou incrementa falhas)

---

## Observabilidade

| Endpoint | Descrição |
|----------|-----------|
| [/actuator/health](https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/health) | Status da aplicação |
| [/actuator/prometheus](https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/prometheus) | Métricas em formato Prometheus |
| [/actuator/metrics](https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics) | Lista de métricas disponíveis |

### Métricas Custom

```bash
# Mensagens publicadas no Pub/Sub
curl -s https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics/pubsub.message.published | jq .

# Mensagens consumidas
curl -s https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics/pubsub.message.consumed | jq .

# Duração do batch de renovação
curl -s https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics/subscription.renewal.batch.duration | jq .

# Cache hit/miss
curl -s https://subscription-service-j4odpghlpq-rj.a.run.app/actuator/metrics/subscription.cache | jq .
```

---

## Arquitetura

```
src/main/java/com/globo/subscription/
├── domain/              # Entidades, Value Objects, Enums, Domain Events
├── application/         # Use Cases, Port Interfaces, DTOs, Exceções
└── adapter/
    ├── inbound/
    │   ├── rest/        # Controllers, DTOs, Mappers, Exception Handler
    │   ├── messaging/   # PaymentResultListener (Pub/Sub consumer)
    │   └── scheduler/   # Renewal Scheduler (cron)
    └── outbound/
        ├── persistence/ # JPA Entities, Repositories, Mappers
        ├── cache/       # Caffeine Cache Adapters
        ├── messaging/   # PubSub Publisher, PaymentConsumerMock
        ├── payment/     # Mock Payment Gateway + Resilience4j
        ├── event/       # Local Event Publisher
        └── lock/        # In-Memory Lock Manager
```

---

## Regras de Negócio

- **Uma assinatura ativa por usuário** — enforced por Partial Unique Index + validação no use case
- **Renovação automática** — Scheduler com cron no dia de expiração
- **3 falhas → Suspensão** — Após 3 tentativas de pagamento falhadas no mesmo billing cycle
- **Cancelamento gracioso** — Usuário mantém acesso até o fim do período pago
- **Idempotência** — Chave única por billing cycle previne cobranças duplicadas
- **Concorrência** — Optimistic locking + `FOR UPDATE SKIP LOCKED` + Distributed Lock

---

## Infraestrutura GCP

| Serviço | Configuração |
|---------|-------------|
| **Cloud Run** | 1 instância max, scale-to-zero, 1Gi RAM, 1 vCPU |
| **Cloud SQL** | PostgreSQL 16, db-f1-micro, 10GB SSD |
| **Pub/Sub** | 4 tópicos + 2 subscriptions + DLQ |
| **Artifact Registry** | Docker images |

### Deploy

```bash
./deploy.sh globo-test-499418
```

### Teardown (remover todos os recursos)

```bash
./teardown.sh globo-test-499418
```

---

## Setup Local

### Pré-requisitos

- **Java 25** (`sdk use java 25.0.3-amzn`)
- **Docker** (para Pub/Sub emulator)

### Executar

```bash
# Subir emulador Pub/Sub
docker compose up -d pubsub-emulator

# Rodar aplicação (perfil local)
PUBSUB_EMULATOR_HOST=localhost:8085 ./mvnw spring-boot:run
```

A aplicação conecta ao PostgreSQL local (`localhost:5432`) e ao emulador Pub/Sub (`localhost:8085`).

### Testes

```bash
# Testes unitários + property-based
./mvnw test

# Testes de integração (requer Docker)
./mvnw verify -P integration-tests

# Todos os testes + cobertura
./mvnw verify -P all-tests
```

Relatório de cobertura: `target/site/jacoco/index.html`

---

## Respostas de Erro

| HTTP Status | Cenário |
|------------|---------|
| 400 | Validação de campos falhou |
| 404 | Entidade não encontrada |
| 409 | Assinatura ativa já existe / email duplicado |
| 500 | Erro interno |

```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2026-06-16T10:40:00Z"
}
```

---

## Licença

Projeto para avaliação técnica — Globo Streaming.
