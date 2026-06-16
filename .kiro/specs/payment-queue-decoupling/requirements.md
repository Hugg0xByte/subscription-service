# Requirements Document

## Introduction

Este documento descreve os requisitos para desacoplar o fluxo de pagamento de renovação de assinaturas usando Google Cloud Pub/Sub como mecanismo de filas. O objetivo é substituir o processamento síncrono atual (onde o `RenewExpiredSubscriptionsUseCase` chama diretamente o `PaymentGatewayPort`) por um fluxo assíncrono baseado em mensagens. O sistema atual identifica assinaturas vencidas e processa pagamentos de forma síncrona dentro do mesmo batch. Com a nova arquitetura, o scheduler publica mensagens em um tópico "pendente-de-pagamento", um consumer mock processa o pagamento e publica o resultado em "pagamento-processado", e o microserviço de assinatura consome o resultado para atualizar o estado da Subscription.

## Glossary

- **Subscription_Service**: O microserviço de gestão de assinaturas (aplicação Spring Boot existente)
- **Renewal_Scheduler**: Componente scheduler que identifica assinaturas vencidas e dispara o processo de renovação
- **Payment_Publisher**: Componente responsável por publicar mensagens de cobrança pendente no tópico Pub/Sub
- **Payment_Consumer_Mock**: Serviço consumidor mock que simula o processamento de pagamento por um gateway externo
- **Payment_Result_Listener**: Componente do Subscription_Service que consome mensagens de resultado de pagamento
- **Pub_Sub**: Google Cloud Pub/Sub — serviço de mensageria gerenciado do GCP
- **Pub_Sub_Emulator**: Emulador local do Google Cloud Pub/Sub executado via Docker para desenvolvimento
- **Topic_Pendente_Pagamento**: Tópico Pub/Sub para mensagens de cobrança pendentes de processamento
- **Topic_Pagamento_Processado**: Tópico Pub/Sub para mensagens com resultado do processamento de pagamento
- **Subscription_Pub_Sub**: Assinatura (subscription) no contexto do Pub/Sub que vincula um subscriber a um tópico (não confundir com Subscription do domínio)
- **Dead_Letter_Queue**: Fila para mensagens que falharam repetidamente no processamento
- **Message_Publisher_Port**: Interface (port) hexagonal para publicação de mensagens em filas
- **Message_Consumer_Port**: Interface (port) hexagonal para consumo de mensagens de filas
- **PaymentRequestMessage**: Mensagem publicada no Topic_Pendente_Pagamento contendo dados da cobrança
- **PaymentResultMessage**: Mensagem publicada no Topic_Pagamento_Processado contendo resultado do pagamento

## Requirements

### Requirement 1: Publicação de cobrança pendente na fila

**User Story:** Como sistema de renovação, eu quero publicar mensagens de cobrança pendente em uma fila, para que o processamento de pagamento seja desacoplado e assíncrono.

#### Acceptance Criteria

1. WHEN the Renewal_Scheduler identifies subscriptions due for renewal, THE Payment_Publisher SHALL publish one PaymentRequestMessage per subscription to the Topic_Pendente_Pagamento
2. THE PaymentRequestMessage SHALL contain subscriptionId, userId, planId, amount, currency, attemptNumber, and idempotencyKey
3. WHEN a PaymentRequestMessage is published, THE Payment_Publisher SHALL include a unique messageId and a timestamp
4. IF the Pub_Sub is unavailable during publication, THEN THE Payment_Publisher SHALL retry the publication with exponential backoff up to 3 attempts
5. IF all retry attempts fail, THEN THE Payment_Publisher SHALL log the failure and skip that subscription without aborting the batch

### Requirement 2: Consumo e processamento mock de pagamento

**User Story:** Como serviço externo de pagamento (mock), eu quero consumir mensagens de cobrança pendente e retornar o resultado, para simular o fluxo de um gateway de pagamento real.

#### Acceptance Criteria

1. WHEN a PaymentRequestMessage is available in the Topic_Pendente_Pagamento, THE Payment_Consumer_Mock SHALL consume the message within 30 seconds
2. THE Payment_Consumer_Mock SHALL simulate processamento de pagamento e publicar uma PaymentResultMessage no Topic_Pagamento_Processado
3. THE PaymentResultMessage SHALL contain subscriptionId, userId, status (APPROVED ou FAILED), providerTransactionId (quando aprovado), errorCode e errorMessage (quando falhado), idempotencyKey, and processedAt
4. THE Payment_Consumer_Mock SHALL acknowledge the message no Pub_Sub after successful processing
5. IF the Payment_Consumer_Mock fails to process a message, THEN THE Pub_Sub SHALL redeliver the message according to its retry policy

### Requirement 3: Consumo do resultado de pagamento e atualização da Subscription

**User Story:** Como microserviço de assinatura, eu quero consumir mensagens de resultado de pagamento, para atualizar o estado da Subscription com base no sucesso ou falha do pagamento.

#### Acceptance Criteria

1. WHEN a PaymentResultMessage with status APPROVED is available in the Topic_Pagamento_Processado, THE Payment_Result_Listener SHALL invoke processSuccessfulPayment on the corresponding Subscription
2. WHEN a PaymentResultMessage with status FAILED is available in the Topic_Pagamento_Processado, THE Payment_Result_Listener SHALL invoke processFailedPayment on the corresponding Subscription
3. AFTER processing the PaymentResultMessage, THE Payment_Result_Listener SHALL persist the updated Subscription, evict the cache, and publish the domain events
4. IF the Subscription referenced in the PaymentResultMessage does not exist, THEN THE Payment_Result_Listener SHALL log a warning and acknowledge the message without retrying
5. THE Payment_Result_Listener SHALL process messages idempotently using the idempotencyKey to prevent duplicate processing

### Requirement 4: Ports hexagonais para mensageria

**User Story:** Como arquiteto, eu quero interfaces (ports) desacopladas para publicação e consumo de mensagens, para manter a independência de provider e facilitar a troca futura de tecnologia de filas.

#### Acceptance Criteria

1. THE Subscription_Service SHALL define a Message_Publisher_Port interface with a method to publish messages to a named topic
2. THE Subscription_Service SHALL define a Message_Consumer_Port interface (ou listener annotation pattern) for receiving messages from a named subscription
3. THE Message_Publisher_Port SHALL accept a generic message payload and a topic identifier as parameters
4. THE Subscription_Service SHALL provide a Pub/Sub adapter implementation of the Message_Publisher_Port
5. THE Subscription_Service SHALL provide a Pub/Sub adapter implementation of the Message_Consumer_Port using Spring Cloud GCP Pub/Sub or the google-cloud-pubsub SDK

### Requirement 5: Infraestrutura Pub/Sub local com emulador

**User Story:** Como desenvolvedor, eu quero rodar o Pub/Sub localmente via emulador Docker, para não depender de infraestrutura cloud durante o desenvolvimento.

#### Acceptance Criteria

1. THE docker-compose.yml SHALL include a Pub_Sub_Emulator service using the official Google Cloud Pub/Sub emulator image
2. WHEN the Pub_Sub_Emulator starts, THE Subscription_Service SHALL automatically create the Topic_Pendente_Pagamento and its associated Subscription_Pub_Sub
3. WHEN the Pub_Sub_Emulator starts, THE Subscription_Service SHALL automatically create the Topic_Pagamento_Processado and its associated Subscription_Pub_Sub
4. THE Subscription_Service SHALL configure the Pub/Sub client to connect to the Pub_Sub_Emulator via environment variable (PUBSUB_EMULATOR_HOST)
5. WHILE running in local profile, THE Subscription_Service SHALL use the Pub_Sub_Emulator without requiring GCP credentials

### Requirement 6: Refatoração do RenewExpiredSubscriptionsUseCase

**User Story:** Como desenvolvedor, eu quero refatorar o use case de renovação para publicar na fila em vez de processar pagamento diretamente, para que o fluxo seja totalmente assíncrono.

#### Acceptance Criteria

1. THE RenewExpiredSubscriptionsUseCase SHALL publish a PaymentRequestMessage to the Topic_Pendente_Pagamento for each subscription due for renewal, instead of calling the PaymentGatewayPort directly
2. THE RenewExpiredSubscriptionsUseCase SHALL NOT invoke PaymentGatewayPort.processPayment after the refactoring
3. THE RenewExpiredSubscriptionsUseCase SHALL update the subscription status to PENDENTE_PAGAMENTO after publishing the message successfully
4. IF the message publication fails for a given subscription, THEN THE RenewExpiredSubscriptionsUseCase SHALL log the error and continue processing the remaining subscriptions
5. THE RenewExpiredSubscriptionsUseCase SHALL retain the distributed lock mechanism and batch processing logic

### Requirement 7: Dead Letter Queue e tratamento de mensagens problemáticas

**User Story:** Como operador do sistema, eu quero que mensagens que falhem repetidamente sejam movidas para uma Dead Letter Queue, para que mensagens problemáticas não bloqueiem o processamento normal.

#### Acceptance Criteria

1. THE Subscription_Pub_Sub for Topic_Pagamento_Processado SHALL have a Dead_Letter_Queue configured with a maximum delivery attempts of 5
2. THE Subscription_Pub_Sub for Topic_Pendente_Pagamento SHALL have a Dead_Letter_Queue configured with a maximum delivery attempts of 5
3. WHEN a message exceeds the maximum delivery attempts, THE Pub_Sub SHALL move the message to the corresponding Dead_Letter_Queue
4. THE Subscription_Service SHALL log a structured warning when a message is moved to the Dead_Letter_Queue

### Requirement 8: Serialização e deserialização de mensagens

**User Story:** Como desenvolvedor, eu quero que as mensagens sejam serializadas em JSON, para garantir interoperabilidade e facilitar debug.

#### Acceptance Criteria

1. THE Message_Publisher_Port adapter SHALL serialize PaymentRequestMessage to JSON before publishing to Pub_Sub
2. THE Payment_Result_Listener SHALL deserialize PaymentResultMessage from JSON when consuming from Pub_Sub
3. THE Payment_Consumer_Mock SHALL deserialize PaymentRequestMessage from JSON and serialize PaymentResultMessage to JSON
4. FOR ALL valid PaymentRequestMessage objects, serializing to JSON and deserializing back SHALL produce an equivalent object (round-trip property)
5. FOR ALL valid PaymentResultMessage objects, serializing to JSON and deserializing back SHALL produce an equivalent object (round-trip property)
6. IF a message cannot be deserialized, THEN THE consuming component SHALL log the error, acknowledge the message, and move on without retrying

### Requirement 9: Configuração e observabilidade

**User Story:** Como operador do sistema, eu quero métricas e logs estruturados para o fluxo de mensagens, para monitorar a saúde do sistema assíncrono.

#### Acceptance Criteria

1. THE Payment_Publisher SHALL record a counter metric for each message published with success or failure tag
2. THE Payment_Result_Listener SHALL record a counter metric for each message consumed with outcome tag (approved, failed, error)
3. THE Payment_Result_Listener SHALL record a timer metric for message processing duration
4. THE Subscription_Service SHALL log structured messages (JSON) for publish, consume, and error events with subscriptionId and idempotencyKey as MDC context
5. THE Subscription_Service SHALL expose Pub/Sub topic and subscription names via application.yml configuration properties
