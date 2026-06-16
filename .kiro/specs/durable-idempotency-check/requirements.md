# Requirements Document

## Introduction

O mecanismo de idempotência do `PaymentResultListener` atualmente utiliza apenas um `ConcurrentHashMap` em memória para detectar mensagens duplicadas do Pub/Sub. Essa abordagem é insuficiente para produção: chaves são perdidas em restart da aplicação e múltiplas instâncias não compartilham o conjunto. Esta feature adiciona uma verificação durável de idempotência usando a tabela `payment_attempts` (que já possui constraint UNIQUE na coluna `idempotency_key`), mantendo o set em memória como camada de cache rápida para redeliveries imediatos.

## Glossary

- **Idempotency_Service**: Componente responsável por verificar e registrar chaves de idempotência, utilizando cache em memória e banco de dados como camadas complementares.
- **In_Memory_Cache**: Estrutura `ConcurrentHashMap<String>` local que armazena chaves de idempotência já processadas na instância corrente, servindo como fast-path para redeliveries do Pub/Sub.
- **Payment_Attempts_Table**: Tabela `payment_attempts` no PostgreSQL com constraint UNIQUE na coluna `idempotency_key`, que serve como fonte de verdade durável para verificação de duplicatas.
- **PaymentResultListener**: Adapter inbound que consome mensagens da subscription "pagamento-processado" do Pub/Sub e delega o processamento ao `PaymentResultProcessor`.
- **PaymentResultProcessor**: Serviço transacional que processa resultados de pagamento, atualizando o estado da assinatura e publicando eventos de domínio.
- **Idempotency_Key**: Chave única no formato `subscription:{subscriptionId}:billing-cycle:{expirationDate}` que identifica uma operação de pagamento dentro de um billing cycle.
- **Redelivery**: Reenvio de mensagem pelo Pub/Sub quando o consumidor falha em fazer acknowledge dentro do deadline.

## Requirements

### Requirement 1: Verificação de idempotência em duas camadas

**User Story:** As a system operator, I want payment result processing to survive application restarts and multi-instance deployments without processing duplicates, so that no subscription is charged or updated twice for the same billing cycle.

#### Acceptance Criteria

1. WHEN a payment result message is received, THE Idempotency_Service SHALL first check the In_Memory_Cache for the presence of the Idempotency_Key.
2. WHEN the Idempotency_Key is found in the In_Memory_Cache, THE Idempotency_Service SHALL return a duplicate indication without querying the Payment_Attempts_Table.
3. WHEN the Idempotency_Key is not found in the In_Memory_Cache, THE Idempotency_Service SHALL query the Payment_Attempts_Table for a record with the matching idempotency_key.
4. WHEN the Idempotency_Key is found in the Payment_Attempts_Table, THE Idempotency_Service SHALL add the key to the In_Memory_Cache and return a duplicate indication.
5. WHEN the Idempotency_Key is not found in the In_Memory_Cache nor in the Payment_Attempts_Table, THE Idempotency_Service SHALL return a non-duplicate indication allowing processing to proceed.

### Requirement 2: Registro durável após processamento bem-sucedido

**User Story:** As a system operator, I want successful payment processing to be durably recorded in the database, so that all instances can detect duplicates even after restarts.

#### Acceptance Criteria

1. WHEN the PaymentResultProcessor completes processing of a payment result successfully, THE Idempotency_Service SHALL add the Idempotency_Key to the In_Memory_Cache.
2. THE Payment_Attempts_Table SHALL contain a record for each processed Idempotency_Key, persisted within the same transaction as the subscription state update.
3. IF a database constraint violation occurs on the idempotency_key column during insert, THEN THE Idempotency_Service SHALL treat the message as a duplicate and acknowledge it without error.

### Requirement 3: Resiliência a falhas de banco na verificação

**User Story:** As a system operator, I want the system to handle database unavailability gracefully during idempotency checks, so that transient database failures do not cause message loss or duplicate processing.

#### Acceptance Criteria

1. IF the Payment_Attempts_Table query fails due to a transient database error, THEN THE PaymentResultListener SHALL nack the message to allow Pub/Sub redelivery.
2. IF the Payment_Attempts_Table query fails due to a transient database error, THEN THE PaymentResultListener SHALL log the error with severity WARN including the Idempotency_Key.
3. WHILE the In_Memory_Cache contains the Idempotency_Key, THE Idempotency_Service SHALL return a duplicate indication regardless of database availability.

### Requirement 4: Isolamento da verificação via port da arquitetura hexagonal

**User Story:** As a developer, I want the idempotency check to be abstracted behind a port interface, so that the implementation can be tested and replaced independently of the messaging adapter.

#### Acceptance Criteria

1. THE Idempotency_Service SHALL be exposed through a port interface in the application layer following the hexagonal architecture pattern of the project.
2. THE PaymentResultListener SHALL depend only on the port interface for idempotency verification, not on the concrete implementation.
3. WHEN verifying idempotency, THE port interface SHALL provide a method that accepts an Idempotency_Key and returns a boolean indicating whether the key was already processed.
4. WHEN registering a processed key, THE port interface SHALL provide a method that accepts an Idempotency_Key and records it in the In_Memory_Cache.

### Requirement 5: Boundedness do cache em memória

**User Story:** As a system operator, I want the in-memory cache to have bounded size, so that long-running instances do not experience unbounded memory growth.

#### Acceptance Criteria

1. THE In_Memory_Cache SHALL have a configurable maximum size limit.
2. WHEN the In_Memory_Cache reaches the maximum size limit, THE Idempotency_Service SHALL evict the oldest entries to make room for new ones.
3. WHEN an entry is evicted from the In_Memory_Cache, THE Idempotency_Service SHALL still detect duplicates via the Payment_Attempts_Table query on subsequent checks.

### Requirement 6: Observabilidade da verificação de idempotência

**User Story:** As a system operator, I want metrics on duplicate detection, so that I can monitor redelivery rates and detect anomalies in message processing.

#### Acceptance Criteria

1. WHEN a duplicate is detected via the In_Memory_Cache, THE Idempotency_Service SHALL increment a metric counter with tag source=memory.
2. WHEN a duplicate is detected via the Payment_Attempts_Table, THE Idempotency_Service SHALL increment a metric counter with tag source=database.
3. WHEN a message passes idempotency verification as non-duplicate, THE Idempotency_Service SHALL increment a metric counter with tag source=new.
