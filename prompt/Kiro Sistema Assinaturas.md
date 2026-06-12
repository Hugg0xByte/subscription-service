# Prompt para Kiro AWS — Sistema de Assinaturas Globo

Você é um **Arquiteto de Software Sênior / Staff Engineer**, especialista em **Java 25**, **Spring Boot**, **arquitetura hexagonal**, **microserviços**, **sistemas de assinatura**, **pagamentos recorrentes**, **concorrência**, **observabilidade**, **resiliência**, **GCP** e **documentação técnica com Draw.io**.

## Contexto do desafio

Vamos construir um **Sistema de Gestão de Assinaturas para serviço de streaming**, com base no desafio técnico da Globo.

Requisitos principais:

- Criar API para cadastrar usuários e criar assinaturas.
- Um usuário pode ter apenas **uma assinatura ativa por vez**.
- Planos disponíveis:
  - `BASICO` — R$ 19,90/mês
  - `PREMIUM` — R$ 39,90/mês
  - `FAMILIA` — R$ 59,90/mês
- A assinatura deve conter: `id`, `usuarioId`, `plano`, `dataInicio`, `dataExpiracao`, `status`.
- Implementar renovação automática no dia do vencimento.
- Caso o pagamento falhe, tentar novamente até **3 tentativas**.
- Após 3 falhas, suspender a assinatura.
- Criar endpoint para cancelamento.
- Quando cancelada antes da expiração, o usuário continua com acesso até o fim do ciclo.
- Usar boas práticas de estruturação, SOLID, testes, concorrência, desempenho e criatividade técnica.

Use os desenhos de referência da arquitetura e do banco da Petz apenas como **inspiração conceitual**, pois o contexto Petz é mais complexo. O objetivo aqui é entregar uma solução bem desenhada para o desafio técnico da Globo, sem copiar exatamente o modelo Petz.

---

## Objetivo inicial no Kiro

Antes de gerar código, crie a documentação e os diagramas do projeto usando o poder do **Draw.io no Kiro**.

A ordem de trabalho deve ser:

1. Criar desenho de arquitetura da solução.
2. Avaliar se o projeto deve ser um monólito modular, um microserviço único ou vários microserviços.
3. Criar diagrama de classes.
4. Criar desenho do banco de dados.
5. Criar documentação das regras de negócio em Markdown.
6. Depois disso, propor a estrutura inicial do projeto Java 25 com arquitetura hexagonal.

---

## Decisão arquitetural esperada

Avalie tecnicamente a necessidade de usar **um microserviço ou vários microserviços**.

Para este desafio, considere como primeira opção:

> **Um microserviço único bem modularizado com arquitetura hexagonal.**

Justificativa esperada:

- O domínio é relativamente pequeno para o desafio.
- Separar em vários microserviços agora pode gerar complexidade desnecessária.
- A arquitetura hexagonal permite separar domínio, aplicação e infraestrutura.
- O sistema pode evoluir futuramente para múltiplos serviços, como `subscription-service`, `payment-service` e `notification-service`, sem reescrever o domínio.

Mesmo assim, documente uma visão futura com possível separação em:

- `subscription-service`
- `payment-service`
- `billing-scheduler-service`
- `notification-service`

Mas deixe claro que a entrega inicial será um **microserviço de assinaturas modularizado**.

---

## Tecnologias desejadas

Use como base:

- Java 25
- Spring Boot 3.x ou versão compatível mais recente
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway ou Liquibase
- Redis ou cache local com Spring Cache/Caffeine
- Resilience4j para circuit breaker, retry e timeout
- Scheduler com Spring Scheduler ou alternativa robusta
- Kafka ou RabbitMQ como diferencial para eventos assíncronos
- Docker e Docker Compose
- Testcontainers para testes de integração
- JUnit 5
- Mockito
- AssertJ
- ArchUnit para validar arquitetura hexagonal
- OpenAPI/Swagger
- Micrometer + Actuator
- Logs estruturados
- GCP como destino de deploy

---

## Uso de recursos modernos do Java 25

Aplique boas práticas modernas do Java:

- `record` para DTOs e objetos imutáveis de entrada/saída.
- `sealed interface` e `sealed class` quando fizer sentido para erros de domínio, eventos ou resultados de pagamento.
- Pattern matching em `switch` quando aplicável.
- Virtual Threads para workloads bloqueantes, como chamadas HTTP para pagamento.
- `var` apenas quando melhorar legibilidade.
- Imutabilidade no domínio sempre que possível.
- Evitar anemic domain model quando houver regra de negócio clara.
- Usar `Clock` injetável para lidar com datas e testes.
- Usar `BigDecimal` para valores monetários.

---

## Arquitetura hexagonal esperada

Crie o projeto com esta estrutura conceitual:

```text
src/main/java/com/globo/subscription
  ├── domain
  │   ├── model
  │   ├── valueobject
  │   ├── event
  │   ├── exception
  │   └── service
  │
  ├── application
  │   ├── usecase
  │   ├── command
  │   ├── query
  │   └── port
  │       ├── in
  │       └── out
  │
  ├── adapter
  │   ├── inbound
  │   │   ├── rest
  │   │   └── scheduler
  │   └── outbound
  │       ├── persistence
  │       ├── payment
  │       ├── cache
  │       ├── messaging
  │       └── notification
  │
  └── config
```

Regras:

- O domínio não pode depender de Spring.
- Use cases dependem apenas de portas.
- Adapters implementam portas.
- Controllers REST não devem conter regra de negócio.
- Entidades JPA não devem contaminar o domínio, salvo decisão bem justificada.
- Use mappers entre DTO, domínio e entidade de persistência.
- Use ArchUnit para garantir dependências corretas.

---

## Casos de uso esperados

Crie e documente os seguintes casos de uso:

### 1. Criar usuário

Entrada:

```json
{
  "nome": "Hugo Santos",
  "email": "hugo@email.com"
}
```

Regras:

- E-mail deve ser único.
- Usuário deve iniciar ativo.

---

### 2. Criar assinatura

Entrada:

```json
{
  "usuarioId": "uuid",
  "plano": "PREMIUM",
  "paymentToken": "token-cartao"
}
```

Regras:

- Usuário pode ter apenas uma assinatura ativa por vez.
- Calcular `dataInicio` como data atual.
- Calcular `dataExpiracao` adicionando 1 mês.
- Status inicial deve ser `ATIVA` quando pagamento aprovado.
- Se pagamento falhar na criação, não criar assinatura ativa.
- Usar lock, constraint ou transação para evitar duas assinaturas ativas simultâneas para o mesmo usuário.

---

### 3. Renovar assinatura automaticamente

Regras:

- Rodar no dia exato do vencimento.
- Buscar assinaturas `ATIVA` com `dataExpiracao <= hoje`.
- Processar cobrança.
- Se pagamento aprovado:
  - Renovar por mais 1 mês.
  - Zerar tentativas de falha.
  - Publicar evento `SubscriptionRenewed`.
- Se pagamento falhar:
  - Incrementar tentativas.
  - Publicar evento `PaymentFailed`.
  - Se atingir 3 tentativas, alterar status para `SUSPENSA`.
- Garantir idempotência para evitar cobrança duplicada.
- Garantir controle de concorrência para múltiplas instâncias do scheduler.

---

### 4. Cancelar assinatura

Regras:

- Cancelamento não remove acesso imediatamente.
- Se cancelada antes da expiração, manter acesso até `dataExpiracao`.
- Status pode ser `CANCELADA`, mas o campo de acesso deve considerar validade até o fim do ciclo.
- Publicar evento `SubscriptionCanceled`.

---

### 5. Consultar assinatura ativa do usuário

Regras:

- Usar cache para otimizar consulta.
- Cache pode ser Redis ou Caffeine.
- Invalidar cache em criação, renovação, cancelamento e suspensão.

---

## Estados da assinatura

Modele os status:

```text
ATIVA
SUSPENSA
CANCELADA
EXPIRADA
PENDENTE_PAGAMENTO
```

Regras importantes:

- `ATIVA`: assinatura com acesso liberado.
- `SUSPENSA`: após 3 falhas de pagamento.
- `CANCELADA`: usuário cancelou, mas pode manter acesso até a expiração.
- `EXPIRADA`: ciclo terminou sem renovação válida.
- `PENDENTE_PAGAMENTO`: usado se houver fluxo assíncrono de pagamento.

---

## Prompt para criar o desenho de arquitetura no Draw.io

Use o Draw.io no Kiro para criar um diagrama chamado:

```text
docs/diagrams/architecture-subscription-system.drawio
```

O diagrama deve conter:

1. Cliente/API Consumer.
2. API Gateway ou Load Balancer.
3. Microserviço `subscription-service`.
4. Camadas internas do serviço:
   - Inbound adapters
   - Application use cases
   - Domain model
   - Outbound ports
   - Infrastructure adapters
5. PostgreSQL.
6. Redis ou cache local.
7. Payment Provider externo.
8. Message Broker opcional.
9. Scheduler de renovação.
10. Observabilidade:
    - Logs
    - Metrics
    - Traces
11. Deploy futuro em GCP:
    - Cloud Run ou GKE
    - Cloud SQL PostgreSQL
    - Memorystore Redis
    - Pub/Sub ou Kafka gerenciado
    - Cloud Logging/Monitoring

Inclua no diagrama o fluxo principal:

```text
Cliente -> REST API -> Use Case -> Domain -> Payment Port -> Payment Provider
                         |          |
                         |          -> Repository Port -> PostgreSQL
                         -> Cache Port -> Redis/Caffeine
                         -> Event Publisher -> Broker
```

Também inclua o fluxo de renovação:

```text
Scheduler -> RenewExpiredSubscriptionsUseCase -> SubscriptionRepository -> PaymentGateway -> Update Subscription -> Publish Event
```

Não copie o desenho Petz exatamente. Use-o apenas como referência de organização visual.

---

## Prompt para criar o diagrama de classes no Draw.io

Crie um diagrama chamado:

```text
docs/diagrams/class-diagram-subscription-system.drawio
```

Inclua as classes principais:

### Domain

- `User`
- `Subscription`
- `Plan`
- `SubscriptionStatus`
- `PaymentMethod`
- `PaymentAttempt`
- `Money`
- `SubscriptionId`
- `UserId`
- `PaymentResult`
- `DomainEvent`
- `SubscriptionCreated`
- `SubscriptionRenewed`
- `SubscriptionCanceled`
- `SubscriptionSuspended`

### Use cases

- `CreateUserUseCase`
- `CreateSubscriptionUseCase`
- `RenewExpiredSubscriptionsUseCase`
- `CancelSubscriptionUseCase`
- `GetActiveSubscriptionUseCase`

### Ports

- `UserRepositoryPort`
- `SubscriptionRepositoryPort`
- `PaymentGatewayPort`
- `SubscriptionCachePort`
- `EventPublisherPort`
- `LockManagerPort`

### Adapters

- `SubscriptionController`
- `RenewSubscriptionScheduler`
- `JpaSubscriptionRepositoryAdapter`
- `JpaUserRepositoryAdapter`
- `PaymentProviderAdapter`
- `RedisSubscriptionCacheAdapter`
- `KafkaEventPublisherAdapter`

Mostre as dependências corretas:

```text
Controller -> UseCase -> Port -> Adapter
UseCase -> Domain
Domain -> DomainEvent
```

Não deixe o domínio depender de adapters, Spring, banco ou mensageria.

---

## Prompt para criar o desenho do banco de dados no Draw.io

Crie um diagrama chamado:

```text
docs/diagrams/database-subscription-system.drawio
```

Modele as tabelas:

### users

```text
id UUID PK
name VARCHAR NOT NULL
email VARCHAR NOT NULL UNIQUE
active BOOLEAN NOT NULL
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

### subscriptions

```text
id UUID PK
user_id UUID FK -> users.id
plan VARCHAR NOT NULL
status VARCHAR NOT NULL
start_date DATE NOT NULL
expiration_date DATE NOT NULL
cancel_requested_at TIMESTAMP NULL
suspended_at TIMESTAMP NULL
failed_attempts INT NOT NULL DEFAULT 0
version BIGINT NOT NULL
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

### payment_methods

```text
id UUID PK
user_id UUID FK -> users.id
provider VARCHAR NOT NULL
token VARCHAR NOT NULL
active BOOLEAN NOT NULL
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

### payment_attempts

```text
id UUID PK
subscription_id UUID FK -> subscriptions.id
amount NUMERIC(10,2) NOT NULL
status VARCHAR NOT NULL
attempt_number INT NOT NULL
idempotency_key VARCHAR NOT NULL UNIQUE
provider_transaction_id VARCHAR NULL
error_code VARCHAR NULL
error_message VARCHAR NULL
created_at TIMESTAMP NOT NULL
```

### subscription_events

```text
id UUID PK
subscription_id UUID FK -> subscriptions.id
event_type VARCHAR NOT NULL
payload JSONB NOT NULL
created_at TIMESTAMP NOT NULL
published_at TIMESTAMP NULL
```

Inclua constraints importantes:

- `users.email` único.
- Índice para busca de assinaturas vencidas:

```sql
CREATE INDEX idx_subscriptions_due_renewal
ON subscriptions(status, expiration_date);
```

- Constraint ou índice parcial para impedir mais de uma assinatura ativa por usuário:

```sql
CREATE UNIQUE INDEX uq_active_subscription_per_user
ON subscriptions(user_id)
WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO');
```

- `version` para optimistic locking.
- `idempotency_key` único para evitar cobrança duplicada.

---

## Documentação de regras de negócio em Markdown

Crie o arquivo:

```text
docs/business-rules.md
```

Documente:

1. Planos disponíveis e preços.
2. Estados da assinatura.
3. Regra de uma assinatura ativa por usuário.
4. Regra de renovação automática.
5. Regra de 3 tentativas de pagamento.
6. Regra de suspensão.
7. Regra de cancelamento com acesso até fim do ciclo.
8. Regra de idempotência de pagamento.
9. Regra de concorrência no scheduler.
10. Regra de cache e invalidação.
11. Eventos de domínio.
12. Decisões arquiteturais.

---

## Resiliência e circuit breaker

Implemente integração de pagamento com:

- Circuit breaker
- Retry com backoff
- Timeout
- Fallback controlado

Regras:

- Não repetir cobrança se já existir tentativa aprovada para a mesma assinatura e ciclo.
- Retry técnico não deve gerar múltiplas cobranças financeiras.
- Usar idempotency key por assinatura + ciclo de cobrança.

Exemplo de chave:

```text
subscription:{subscriptionId}:billing-cycle:{expirationDate}
```

---

## Cache

Avalie duas opções:

### Opção 1 — Redis

Boa para múltiplas instâncias e deploy em GCP.

### Opção 2 — Caffeine/Spring Cache local

Boa para simplificar o desafio técnico.

Decisão esperada:

- Para teste técnico, pode iniciar com Caffeine.
- Documentar evolução para Redis/Memorystore no GCP.

---

## Concorrência

O sistema deve evitar:

- Duas assinaturas ativas para o mesmo usuário.
- Duas renovações simultâneas da mesma assinatura.
- Duas cobranças no mesmo ciclo.

Use uma combinação de:

- Transação.
- Optimistic locking com `@Version`.
- Índice único parcial.
- Idempotency key.
- Lock distribuído opcional para múltiplas instâncias.
- Query com `FOR UPDATE SKIP LOCKED`, se necessário.

---

## Eventos assíncronos

Modele eventos como diferencial:

```text
SubscriptionCreated
SubscriptionRenewed
SubscriptionCanceled
SubscriptionSuspended
PaymentFailed
PaymentApproved
```

Pode usar:

- Kafka/RabbitMQ na visão evolutiva.
- Outbox pattern para confiabilidade.
- Tabela `subscription_events` para persistir eventos antes de publicar.

---

## Deploy futuro no GCP

Documente uma estratégia de deploy com:

- Cloud Run para o microserviço.
- Cloud SQL PostgreSQL.
- Memorystore Redis.
- Pub/Sub ou Kafka gerenciado, caso use eventos.
- Secret Manager para credenciais.
- Cloud Build para CI/CD.
- Artifact Registry para imagem Docker.
- Cloud Logging e Cloud Monitoring.

---

## Testes esperados

Crie plano de testes com:

### Unitários

- Criar assinatura com sucesso.
- Impedir segunda assinatura ativa.
- Renovar assinatura com pagamento aprovado.
- Incrementar falha de pagamento.
- Suspender após 3 falhas.
- Cancelar mantendo acesso até expiração.

### Integração

- Repository com PostgreSQL usando Testcontainers.
- Constraint de assinatura ativa única.
- Scheduler processando assinaturas vencidas.
- Cache invalidado após alteração.

### Arquitetura

- ArchUnit garantindo que `domain` não depende de `adapter` ou Spring.

---

## Entregáveis esperados

Gere no projeto:

```text
docs/
  business-rules.md
  architecture-decisions.md
  diagrams/
    architecture-subscription-system.drawio
    class-diagram-subscription-system.drawio
    database-subscription-system.drawio
```

Depois dos diagramas e documentos, gere a estrutura inicial do projeto:

```text
build.gradle ou pom.xml
Dockerfile
docker-compose.yml
src/main/java/...
src/test/java/...
```

---

## Importante

Não implemente uma arquitetura exagerada apenas para mostrar complexidade.

A solução deve demonstrar:

- Clareza arquitetural.
- Domínio bem modelado.
- Código limpo.
- SOLID.
- Testabilidade.
- Controle de concorrência.
- Idempotência.
- Resiliência em pagamento.
- Documentação profissional.
- Capacidade de evolução para GCP e múltiplos serviços.

Comece criando os diagramas no Draw.io e a documentação Markdown. Só depois proponha ou gere o código.
