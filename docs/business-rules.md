# Regras de Negócio — Sistema de Gestão de Assinaturas

## 1. Visão Geral do Sistema

O Sistema de Gestão de Assinaturas é responsável por gerenciar o ciclo de vida completo de assinaturas de streaming para a plataforma Globo. O sistema controla a criação, renovação automática, cancelamento e suspensão de assinaturas, garantindo consistência de dados, idempotência em pagamentos e resiliência em integrações externas.

O sistema segue os princípios de Hexagonal Architecture, separando regras de domínio da infraestrutura técnica, e adota uma estratégia cloud-agnostic com GCP como provider V1.

### Princípios Fundamentais

- **Uma assinatura ativa por usuário**: O sistema garante que cada usuário possua no máximo uma assinatura com status ATIVA ou PENDENTE_PAGAMENTO
- **Idempotência**: Todas as operações de pagamento são idempotentes por billing cycle
- **Resiliência**: Falhas em integrações externas não bloqueiam o sistema
- **Consistência eventual**: Eventos de domínio são publicados de forma confiável via outbox pattern

---

## 2. Planos e Preços

O sistema oferece três planos de assinatura:

| Plano | Preço Mensal | Descrição |
|-------|-------------|-----------|
| BASICO | R$ 19,90/mês | Plano básico de streaming |
| PREMIUM | R$ 39,90/mês | Plano premium com recursos adicionais |
| FAMILIA | R$ 59,90/mês | Plano família com múltiplos perfis |

### Regras de Plano

- Cada assinatura está vinculada a exatamente um plano
- O preço é fixado no momento da criação/renovação da assinatura
- Os planos disponíveis são representados pelo enum `Plan` no domínio

---

## 3. Ciclo de Vida da Assinatura (State Machine)

### 3.1 Estados Possíveis

| Estado | Definição |
|--------|-----------|
| **ATIVA** | Assinatura em vigor. O usuário tem acesso completo ao conteúdo até a `expiration_date`. |
| **PENDENTE_PAGAMENTO** | Pagamento da renovação falhou, mas ainda há tentativas restantes no billing cycle. O acesso é mantido enquanto houver retentativas. |
| **SUSPENSA** | Todas as tentativas de pagamento falharam (3 falhas consecutivas no mesmo billing cycle). O acesso ao conteúdo é bloqueado. |
| **EXPIRADA** | A assinatura atingiu sua `expiration_date` sem renovação. Pode ocorrer por falha no scheduler ou ausência de método de pagamento válido. |
| **CANCELADA** | Estado terminal. O usuário solicitou cancelamento ou a assinatura foi encerrada após suspensão/expiração. Não há transições de saída. |

### 3.2 Diagrama de Transição de Estados

```
                    ┌─────────────────────────────────┐
                    │                                 │
                    ▼                                 │
┌──────────┐   pagamento    ┌───────────────────┐    │ renovação
│  ATIVA   │───falhou──────▶│ PENDENTE_PAGAMENTO│    │ com sucesso
│          │                │                   │────┘
│          │◀───────────────│                   │
│          │  pagamento OK  └───────────────────┘
│          │                        │
│          │                        │ 3ª falha consecutiva
│          │                        ▼
│          │                ┌───────────────────┐
│          │                │    SUSPENSA       │
│          │◀───────────────│                   │
│          │  reativação    │                   │───────┐
│          │                └───────────────────┘       │
│          │                                            │ cancelamento
│          │                                            ▼
│          │───cancelamento/expiração──▶┌──────────────────┐
│          │                           │   CANCELADA      │
└──────────┘                           │  (terminal)      │
      │                                └──────────────────┘
      │                                        ▲
      │ expirou sem renovação                  │
      ▼                                        │ cancelamento
┌──────────────┐                               │
│  EXPIRADA    │───────────────────────────────┘
│              │
│              │──reativação──▶ ATIVA
└──────────────┘
```

### 3.3 Matriz de Transição de Estados

| Estado Atual | Transições Válidas | Trigger |
|-------------|-------------------|---------|
| **ATIVA** | → CANCELADA | Usuário solicita cancelamento e `expiration_date` é atingida |
| **ATIVA** | → EXPIRADA | `expiration_date` atingida sem renovação bem-sucedida |
| **ATIVA** | → SUSPENSA | Após 3 falhas consecutivas de pagamento no billing cycle |
| **PENDENTE_PAGAMENTO** | → ATIVA | Pagamento de retry aprovado com sucesso |
| **PENDENTE_PAGAMENTO** | → SUSPENSA | 3ª tentativa de pagamento falha no mesmo billing cycle |
| **SUSPENSA** | → ATIVA | Reativação com novo pagamento aprovado |
| **SUSPENSA** | → CANCELADA | Cancelamento administrativo ou por solicitação do usuário |
| **EXPIRADA** | → ATIVA | Reativação com novo pagamento aprovado |
| **EXPIRADA** | → CANCELADA | Cancelamento definitivo (automático ou por solicitação) |
| **CANCELADA** | _(nenhuma)_ | Estado terminal — sem transições de saída |

---

## 4. Restrição de Assinatura Única por Usuário

### Regra

Cada usuário pode possuir **no máximo uma assinatura ativa** a qualquer momento. O conceito de "ativa" abrange assinaturas com status:
- `ATIVA`
- `PENDENTE_PAGAMENTO`

### Mecanismos de Enforcement

1. **Partial Unique Index (Database level)**
   ```sql
   CREATE UNIQUE INDEX uq_active_subscription_per_user
   ON subscriptions(user_id)
   WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO');
   ```
   Garante a unicidade no nível do banco de dados, prevenindo race conditions.

2. **Validação no Use Case (Application level)**
   O `CreateSubscriptionUseCase` verifica a existência de assinatura ativa antes de criar uma nova. Se uma assinatura ativa já existe, a operação é rejeitada com erro de negócio.

3. **Consequências**
   - Um usuário com assinatura SUSPENSA, EXPIRADA ou CANCELADA pode criar uma nova assinatura
   - Tentativas de criar uma segunda assinatura enquanto uma ATIVA ou PENDENTE_PAGAMENTO existe resultam em violação de constraint

---

## 5. Regras de Renovação Automática

### Trigger

A renovação automática é disparada no **dia de expiração** da assinatura (`expiration_date`). Um scheduler (cron job) executa periodicamente a busca por assinaturas elegíveis para renovação.

### Critérios de Elegibilidade

- Status da assinatura: `ATIVA`
- `expiration_date` <= data atual
- `cancel_requested_at` IS NULL (não há cancelamento pendente)

### Fluxo de Sucesso

1. Scheduler identifica assinaturas com `expiration_date` atingida
2. Para cada assinatura elegível, processa pagamento via Payment Gateway
3. Payment Gateway retorna **APROVADO**
4. Sistema atualiza a assinatura:
   - `expiration_date` = `expiration_date` + 1 mês
   - `failed_attempts` = 0
   - `status` permanece `ATIVA`
   - `version` incrementada (optimistic locking)
5. Evento `SubscriptionRenewed` é publicado
6. Cache é invalidado

### Fluxo de Falha

1. Scheduler identifica assinaturas com `expiration_date` atingida
2. Para cada assinatura elegível, processa pagamento via Payment Gateway
3. Payment Gateway retorna **REJEITADO**
4. Sistema atualiza a assinatura:
   - `failed_attempts` = `failed_attempts` + 1
   - `status` → `PENDENTE_PAGAMENTO` (se era ATIVA)
   - `version` incrementada (optimistic locking)
5. Evento `PaymentFailed` é publicado
6. Assinatura fica elegível para retry conforme regras da Seção 6

### Cancelamento Pendente

Se `cancel_requested_at` IS NOT NULL quando a `expiration_date` é atingida:
- A renovação **não** é processada
- Status transiciona para `CANCELADA`
- Evento `SubscriptionCanceled` é publicado

---

## 6. Regras de Retry de Pagamento

### Limites de Tentativas

- **Máximo de 3 tentativas** por billing cycle
- Cada billing cycle é identificado pela `expiration_date` da assinatura
- O contador `failed_attempts` rastreia o número de falhas no ciclo atual

### Idempotency Key

Formato: `subscription:{subscriptionId}:billing-cycle:{expirationDate}`

Exemplo: `subscription:550e8400-e29b-41d4-a716-446655440000:billing-cycle:2025-02-15`

**Regras de Idempotência:**
- Uma única idempotency key por assinatura por billing cycle
- Requisições duplicadas com a mesma idempotency key retornam o resultado original sem reprocessamento
- Garante que não haverá cobranças duplicadas para o mesmo ciclo
- A idempotency key é armazenada na tabela `payment_attempts` com constraint UNIQUE

### Fluxo de Retry

| Tentativa | Ação em Caso de Falha | Status Resultante |
|-----------|----------------------|-------------------|
| 1ª falha | `failed_attempts` = 1, status → PENDENTE_PAGAMENTO | PENDENTE_PAGAMENTO |
| 2ª falha | `failed_attempts` = 2, permanece PENDENTE_PAGAMENTO | PENDENTE_PAGAMENTO |
| 3ª falha | `failed_attempts` = 3, status → SUSPENSA | SUSPENSA |

### Suspensão Após 3 Falhas

Após a 3ª tentativa falha consecutiva no mesmo billing cycle:
- Status transiciona para `SUSPENSA`
- `suspended_at` é registrado
- Evento `SubscriptionSuspended` é publicado
- O acesso ao conteúdo é bloqueado
- Cache é invalidado

---

## 7. Regras de Cancelamento

### Cancelamento Iniciado pelo Usuário

1. Usuário solicita cancelamento
2. Sistema registra `cancel_requested_at` = timestamp atual
3. Status permanece `ATIVA` — **acesso é mantido até `expiration_date`**
4. Quando `expiration_date` é atingida:
   - Renovação **não** é processada (verificação de `cancel_requested_at`)
   - Status transiciona para `CANCELADA`
   - Evento `SubscriptionCanceled` é publicado
   - Cache é invalidado

### Princípio

O usuário **retém acesso** ao conteúdo até o final do período já pago (billing cycle atual). O cancelamento é efetivado apenas na data de expiração.

### Transições de Status no Cancelamento

```
ATIVA (com cancel_requested_at preenchido)
  └──▶ expiration_date atingida
        └──▶ CANCELADA (terminal)

SUSPENSA
  └──▶ cancelamento solicitado
        └──▶ CANCELADA (imediato, pois não há acesso ativo)
```

---

## 8. Controle de Concorrência

### 8.1 Optimistic Locking (Version Column)

- A tabela `subscriptions` possui a coluna `version` (BIGINT NOT NULL)
- Cada atualização incrementa o `version`
- Se dois processos tentam atualizar a mesma assinatura simultaneamente, o segundo recebe `OptimisticLockException`
- O processo que falha deve fazer retry da operação com o state atualizado

```sql
UPDATE subscriptions 
SET status = 'ATIVA', version = version + 1, ...
WHERE id = :id AND version = :expectedVersion;
-- Se affected_rows = 0, lançar OptimisticLockException
```

### 8.2 Partial Unique Index

```sql
CREATE UNIQUE INDEX uq_active_subscription_per_user
ON subscriptions(user_id)
WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO');
```

- Garante no nível do banco que apenas uma assinatura ativa/pendente existe por usuário
- Tentativas concorrentes de criar assinaturas duplicadas resultam em violação de constraint
- Não afeta assinaturas com status SUSPENSA, EXPIRADA ou CANCELADA

### 8.3 Distributed Lock (Scheduler)

- O scheduler de renovação adquire um distributed lock antes de iniciar o batch
- Previne que múltiplas instâncias do scheduler executem renovações em paralelo
- Lock é implementado via cache distribuído (Redis/Memorystore)
- TTL do lock garante liberação em caso de falha do processo holder

### 8.4 FOR UPDATE SKIP LOCKED (Batch Processing)

```sql
SELECT * FROM subscriptions
WHERE status = 'ATIVA' 
  AND expiration_date <= CURRENT_DATE
  AND cancel_requested_at IS NULL
FOR UPDATE SKIP LOCKED
LIMIT :batchSize;
```

- Utilizado nas queries de batch renewal
- Evita contention em nível de row entre workers paralelos
- Rows já travadas por outro worker são puladas (não bloqueiam)
- Permite processamento paralelo seguro de assinaturas distintas

---

## 9. Estratégia de Cache

### O que é Cacheado

- **Assinatura ativa por `user_id`**: lookup da assinatura com status ATIVA ou PENDENTE_PAGAMENTO para um dado usuário
- Cache key pattern: `subscription:active:{userId}`

### Configuração de TTL

- **TTL**: entre 30 segundos e 5 minutos (configurável por ambiente)
- TTL curto garante consistência eventual com o banco
- TTL mais longo pode ser usado em ambientes de alta carga com tolerância a stale data

### Triggers de Invalidação

O cache é invalidado (entry removida) nos seguintes eventos:

| Evento | Ação no Cache |
|--------|--------------|
| Criação de assinatura | Invalidar cache do `user_id` |
| Renovação de assinatura | Invalidar cache do `user_id` |
| Cancelamento efetivado | Invalidar cache do `user_id` |
| Suspensão de assinatura | Invalidar cache do `user_id` |

### Estratégia de Evolução

1. **V1 — Caffeine (in-memory)**: Cache local para single-instance deployment, sem necessidade de infraestrutura externa
2. **V2 — Redis/Memorystore (distributed)**: Cache distribuído para multi-instance deployment, garantindo consistência entre réplicas

---

## 10. Eventos de Domínio

O sistema emite eventos de domínio para comunicar mudanças significativas no estado das assinaturas. Eventos são persistidos na tabela `subscription_events` (outbox pattern) antes de serem publicados.

### Eventos Disponíveis

#### SubscriptionCreated

Emitido quando uma nova assinatura é criada com sucesso.

```json
{
  "subscription_id": "UUID",
  "event_type": "SUBSCRIPTION_CREATED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "plan": "BASICO | PREMIUM | FAMILIA",
    "status": "ATIVA",
    "start_date": "YYYY-MM-DD",
    "expiration_date": "YYYY-MM-DD"
  }
}
```

#### SubscriptionRenewed

Emitido quando uma assinatura é renovada automaticamente com sucesso.

```json
{
  "subscription_id": "UUID",
  "event_type": "SUBSCRIPTION_RENEWED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "plan": "BASICO | PREMIUM | FAMILIA",
    "new_expiration_date": "YYYY-MM-DD",
    "previous_expiration_date": "YYYY-MM-DD",
    "payment_amount": "19.90 | 39.90 | 59.90"
  }
}
```

#### SubscriptionCanceled

Emitido quando o cancelamento de uma assinatura é efetivado (na `expiration_date`).

```json
{
  "subscription_id": "UUID",
  "event_type": "SUBSCRIPTION_CANCELED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "plan": "BASICO | PREMIUM | FAMILIA",
    "cancel_requested_at": "ISO-8601",
    "effective_cancellation_date": "YYYY-MM-DD"
  }
}
```

#### SubscriptionSuspended

Emitido quando uma assinatura é suspensa após 3 falhas consecutivas de pagamento.

```json
{
  "subscription_id": "UUID",
  "event_type": "SUBSCRIPTION_SUSPENDED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "plan": "BASICO | PREMIUM | FAMILIA",
    "failed_attempts": 3,
    "last_failure_reason": "string"
  }
}
```

#### PaymentFailed

Emitido quando uma tentativa de pagamento falha.

```json
{
  "subscription_id": "UUID",
  "event_type": "PAYMENT_FAILED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "attempt_number": 1,
    "idempotency_key": "subscription:{id}:billing-cycle:{date}",
    "error_code": "string",
    "error_message": "string",
    "amount": "decimal"
  }
}
```

#### PaymentApproved

Emitido quando um pagamento é processado com sucesso.

```json
{
  "subscription_id": "UUID",
  "event_type": "PAYMENT_APPROVED",
  "timestamp": "ISO-8601",
  "payload": {
    "user_id": "UUID",
    "amount": "decimal",
    "idempotency_key": "subscription:{id}:billing-cycle:{date}",
    "provider_transaction_id": "string"
  }
}
```

### Campos Mínimos Obrigatórios

Todo evento de domínio deve conter, no mínimo:
- `subscription_id` — identificador da assinatura
- `event_type` — tipo do evento
- `timestamp` — momento da ocorrência (ISO-8601)

### Persistência e Publicação (Outbox Pattern)

1. Evento é inserido na tabela `subscription_events` dentro da mesma transação da operação de domínio
2. Um processo assíncrono (poller ou CDC) lê eventos não publicados (`published_at IS NULL`)
3. Evento é publicado no message broker (Pub/Sub, Kafka, etc.)
4. `published_at` é atualizado com o timestamp da publicação
5. Garante at-least-once delivery — consumers devem ser idempotentes

---

## 11. Padrões de Resiliência para Pagamento

A integração com o Payment Gateway externo utiliza múltiplos padrões de resiliência para garantir estabilidade e degradação graciosa.

### 11.1 Circuit Breaker

| Parâmetro | Valor |
|-----------|-------|
| Failure threshold para abrir | 5 a 10 falhas consecutivas |
| Estado OPEN duration | Configurável (ex: 30-60 segundos) |
| Half-open requests | 1-3 tentativas de teste |
| Reset automático | Após sucesso em half-open |

**Comportamento:**
- **CLOSED**: Requisições são enviadas normalmente ao Payment Gateway
- **OPEN**: Requisições falham imediatamente sem chamar o provider (fail-fast)
- **HALF-OPEN**: Permite número limitado de requisições para testar recuperação

### 11.2 Retry com Exponential Backoff

| Parâmetro | Valor |
|-----------|-------|
| Máximo de tentativas | 3 |
| Backoff inicial | Configurável (ex: 1 segundo) |
| Multiplicador | 2x (exponential) |
| Max backoff | Configurável (ex: 10 segundos) |

**Cálculo do delay:**
- 1ª tentativa: imediata
- 2ª tentativa: ~1s delay
- 3ª tentativa: ~2s delay

**Nota:** Este retry é para falhas transientes na comunicação (timeout, connection reset). Diferente do retry de billing cycle (Seção 6) que ocorre em execuções separadas do scheduler.

### 11.3 Timeout

| Parâmetro | Valor |
|-----------|-------|
| Timeout por requisição | 5 a 30 segundos (configurável) |
| Connect timeout | Subset do timeout total |
| Read timeout | Subset do timeout total |

- Cada chamada ao Payment Gateway tem um timeout máximo definido
- Requisições que excedem o timeout são abortadas e tratadas como falha
- Previne que uma chamada lenta bloqueie o thread de processamento

### 11.4 Fallback Behavior

Quando o Payment Gateway está indisponível (circuit breaker OPEN) ou todas as tentativas falharam:

1. A tentativa de pagamento é marcada como **FAILED** na tabela `payment_attempts`
2. O `failed_attempts` da assinatura é incrementado
3. A assinatura é agendada para **retry no próximo ciclo** do scheduler
4. O batch de renovação **não é bloqueado** — continua processando as próximas assinaturas
5. Evento `PaymentFailed` é publicado com detalhes da falha

**Princípio:** Uma falha individual de pagamento nunca deve bloquear o processamento do batch de renovação. O sistema opera com graceful degradation — falhas são registradas e agendadas para retry posterior.

---

## Glossário Técnico

| Termo | Definição |
|-------|-----------|
| Billing cycle | Período entre `start_date` e `expiration_date` de uma assinatura |
| Idempotency key | Chave única que garante que uma operação não seja processada mais de uma vez |
| Optimistic locking | Estratégia de concorrência que detecta conflitos via coluna `version` |
| Circuit breaker | Padrão que interrompe chamadas a serviços externos após falhas consecutivas |
| Outbox pattern | Padrão que garante publicação confiável de eventos via tabela intermediária |
| TTL (Time-To-Live) | Tempo de validade de uma entrada no cache antes de expiração automática |
| Distributed lock | Mecanismo de exclusão mútua entre múltiplas instâncias de serviço |
| FOR UPDATE SKIP LOCKED | Cláusula SQL que pula rows já travadas por outra transação |
