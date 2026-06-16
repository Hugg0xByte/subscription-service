# Guia de Teste — Fluxo Completo com Filas Pub/Sub

## Pré-requisitos

1. **Java 25** configurado como JDK ativo:
   ```bash
   sdk use java 25.0.3-amzn
   ```

2. **Docker** rodando com os containers:
   ```bash
   docker compose up -d
   ```

3. **Aplicação** rodando:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## Fluxo Completo

O sistema funciona assim:

```
Criar Usuário → Criar Assinatura (ATIVA)
→ Trigger Renovação (scheduler/manual)
→ Publica na fila "pendente-de-pagamento"
→ PaymentConsumerMock consome e simula pagamento
→ Publica resultado na fila "pagamento-processado"
→ PaymentResultListener consome e atualiza a assinatura
```

---

## Passo 1 — Criar um Usuário

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Hugo Oliveira",
    "email": "hugo@teste.com"
  }' | jq .
```

**Resposta esperada (201 Created):**
```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "name": "Hugo Oliveira",
  "email": "hugo@teste.com"
}
```

> ⚠️ **Anote o `id` do usuário** — será usado no próximo passo.

---

## Passo 2 — Consultar os Planos Disponíveis

Os planos são inseridos via Liquibase com IDs gerados dinamicamente. Para obter o `planId`, consulte o banco:

```bash
docker exec subscription-db psql -U huggooliveira -d subscription_db \
  -c "SELECT id, name, monthly_price FROM plans;"
```

**Saída esperada:**
```
                  id                  |  name   | monthly_price
--------------------------------------+---------+---------------
 <uuid-basico>                        | BASICO  |         19.90
 <uuid-premium>                       | PREMIUM |         39.90
 <uuid-familia>                       | FAMILIA |         59.90
```

> ⚠️ **Anote o `id` do plano desejado** (ex: BASICO).

---

## Passo 3 — Criar uma Assinatura

Substitua `{USER_ID}` e `{PLAN_ID}` pelos valores obtidos:

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "{USER_ID}",
    "planId": "{PLAN_ID}"
  }' | jq .
```

**Resposta esperada (201 Created):**
```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "userId": "...",
  "planName": "BASICO",
  "status": "ATIVA",
  "startDate": "2026-06-15",
  "expirationDate": "2026-07-15",
  "priceAtPurchase": 19.90
}
```

A assinatura foi criada com status **ATIVA** e expira em 1 mês.

---

## Passo 4 — Verificar Assinatura Ativa

```bash
curl -s "http://localhost:8080/api/v1/subscriptions/active?userId={USER_ID}" | jq .
```

---

## Passo 5 — Simular Expiração (forçar renovação)

O scheduler só processa assinaturas com `expiration_date <= hoje`. Para testar sem esperar, atualize a data de expiração no banco:

```bash
docker exec subscription-db psql -U huggooliveira -d subscription_db \
  -c "UPDATE subscriptions SET expiration_date = CURRENT_DATE WHERE user_id = '{USER_ID}';"
```

---

## Passo 6 — Disparar Renovação Manual (aciona as filas)

```bash
curl -s -X POST "http://localhost:8080/api/v1/subscriptions/renewals/trigger?batchSize=100" | jq .
```

**Resposta esperada:**
```json
{
  "status": "triggered",
  "date": "2026-06-15",
  "batchSize": 100,
  "message": "Renewal process executed. Check application logs for details."
}
```

### O que acontece internamente:

1. `RenewExpiredSubscriptionsUseCase` encontra assinaturas vencidas
2. Publica uma mensagem na fila **`pendente-de-pagamento`** (Pub/Sub)
3. A assinatura muda para status **`PENDENTE_PAGAMENTO`**
4. O `PaymentConsumerMock` (rodando na mesma JVM) consome a mensagem
5. Simula o pagamento via `MockPaymentGatewayAdapter` (80% aprovação, 15% rejeição, 5% timeout)
6. Publica o resultado na fila **`pagamento-processado`**
7. O `PaymentResultListener` consome e atualiza a assinatura:
   - **APPROVED** → status volta para `ATIVA`, `expiration_date` + 1 mês, `failed_attempts` = 0
   - **FAILED** → `failed_attempts` incrementa; se chegar a 3, status → `SUSPENSA`

---

## Passo 7 — Verificar Resultado da Renovação

Aguarde 1-2 segundos (processamento assíncrono) e consulte novamente:

```bash
curl -s "http://localhost:8080/api/v1/subscriptions/active?userId={USER_ID}" | jq .
```

**Se pagamento aprovado:** status = `ATIVA`, `expirationDate` avançou 1 mês.

**Se pagamento falhou:** status = `PENDENTE_PAGAMENTO`, `failedAttempts` incrementou.

### Verificar pelo banco (mais detalhes):

```bash
docker exec subscription-db psql -U huggooliveira -d subscription_db \
  -c "SELECT id, status, expiration_date, failed_attempts, version FROM subscriptions WHERE user_id = '{USER_ID}';"
```

---

## Passo 8 — Verificar Eventos de Domínio

Cada operação gera eventos na tabela `subscription_events`:

```bash
docker exec subscription-db psql -U huggooliveira -d subscription_db \
  -c "SELECT event_type, payload, created_at FROM subscription_events ORDER BY created_at DESC LIMIT 5;"
```

---

## Passo 9 — Testar Fluxo de Falha (3 falhas → Suspensão)

Para garantir que o pagamento falhe, repita o ciclo de renovação 3 vezes. Como o mock tem 80% de aprovação, pode ser necessário repetir várias vezes. Alternativamente, acompanhe os logs:

```bash
# Repita os passos 5 e 6 até ver failed_attempts = 3:
docker exec subscription-db psql -U huggooliveira -d subscription_db \
  -c "SELECT status, failed_attempts FROM subscriptions WHERE user_id = '{USER_ID}';"
```

Quando `failed_attempts = 3`, o status será **`SUSPENSA`**.

---

## Passo 10 — Testar Cancelamento

```bash
curl -s -X DELETE "http://localhost:8080/api/v1/subscriptions/{SUBSCRIPTION_ID}/cancel"
```

**Resposta:** 204 No Content

O cancelamento marca `cancel_requested_at` mas mantém acesso até `expiration_date`. Ao expirar, a renovação não é processada e o status muda para `CANCELADA`.

---

## Passo 11 — Monitorar via Actuator

```bash
# Health check (inclui status do circuit breaker)
curl -s http://localhost:8080/actuator/health | jq .

# Métricas do Pub/Sub
curl -s http://localhost:8080/actuator/metrics/pubsub.message.consumed | jq .
curl -s http://localhost:8080/actuator/metrics/pubsub.message.published | jq .

# Métricas de renovação
curl -s http://localhost:8080/actuator/metrics/subscription.renewal.batch.duration | jq .

# Prometheus (todas as métricas)
curl -s http://localhost:8080/actuator/prometheus | grep subscription
```

---

## Resumo dos Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/users` | Criar usuário |
| POST | `/api/v1/subscriptions` | Criar assinatura |
| GET | `/api/v1/subscriptions/active?userId=` | Consultar assinatura ativa |
| DELETE | `/api/v1/subscriptions/{id}/cancel` | Cancelar assinatura |
| POST | `/api/v1/subscriptions/renewals/trigger` | Disparar renovação manual |

---

## Logs Importantes para Acompanhar

Ao rodar a aplicação, observe nos logs:

```
PubSubInitializer      - Created topic: pendente-de-pagamento
PubSubInitializer      - Created subscription: pendente-de-pagamento-sub -> pendente-de-pagamento
PaymentConsumerMock    - PaymentConsumerMock subscribed to 'pendente-de-pagamento-sub'
PaymentResultListener  - PaymentResultListener subscribed to 'pagamento-processado-sub'
```

Ao disparar renovação:
```
RenewExpiredSubscriptions - Renewal batch started. Found 1 subscriptions due for renewal.
PubSubMessagePublisher    - Message published to topic 'pendente-de-pagamento' successfully
PaymentConsumerMock       - Processing payment for subscription xxx with idempotencyKey xxx
PaymentConsumerMock       - Payment result published for subscription xxx: APPROVED
PaymentResultListener     - [processamento do resultado]
```
