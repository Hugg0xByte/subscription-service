# Implementation Plan: Durable Idempotency Check

## Overview

Replace the volatile `ConcurrentHashMap`-based idempotency mechanism in `PaymentResultListener` with a two-layer approach: a bounded Caffeine in-memory cache (L1) backed by the existing `payment_attempts` PostgreSQL table (L2). The implementation follows hexagonal architecture with a new `IdempotencyCheckPort` interface and `DurableIdempotencyAdapter` outbound adapter.

## Tasks

- [ ] 1. Create port interface and exception in the application layer
  - [ ] 1.1 Create `IdempotencyCheckPort` interface in `com.globo.subscription.application.port`
    - Define `boolean isAlreadyProcessed(String idempotencyKey)` method with Javadoc
    - Define `void markAsProcessed(String idempotencyKey)` method with Javadoc
    - Document that `isAlreadyProcessed` throws `IdempotencyCheckException` on transient DB errors
    - _Requirements: 4.1, 4.3, 4.4_

  - [ ] 1.2 Create `IdempotencyCheckException` in `com.globo.subscription.application.exception`
    - Extend `RuntimeException`
    - Constructor accepting `String message` and `Throwable cause`
    - _Requirements: 3.1_

- [ ] 2. Implement the durable idempotency adapter
  - [ ] 2.1 Add `existsByIdempotencyKey(String idempotencyKey)` method to `PaymentAttemptJpaRepository`
    - Create `PaymentAttemptJpaRepository` interface in `com.globo.subscription.adapter.outbound.persistence.repository`
    - Extend `JpaRepository<PaymentAttemptJpaEntity, UUID>`
    - Add derived query method `boolean existsByIdempotencyKey(String idempotencyKey)`
    - _Requirements: 1.3_

  - [ ] 2.2 Create `DurableIdempotencyAdapter` in `com.globo.subscription.adapter.outbound.idempotency`
    - Implement `IdempotencyCheckPort`
    - Inject `PaymentAttemptJpaRepository`, `MeterRegistry`, and configuration values via `@Value`
    - Initialize Caffeine cache with `maximumSize` and `expireAfterWrite` from `cache.idempotency.max-size` and `cache.idempotency.ttl-minutes`
    - Implement `isAlreadyProcessed`: check Caffeine first, then query DB, throw `IdempotencyCheckException` on `DataAccessException`
    - Implement `markAsProcessed`: put key in Caffeine cache
    - Register Micrometer counters: `idempotency.check.duplicate` with tag `source=memory`, `idempotency.check.duplicate` with tag `source=database`, `idempotency.check.new` with tag `source=new`
    - Warm cache on DB hit (add key to Caffeine when found in DB)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 3.1, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3_

  - [ ]* 2.3 Write property tests for `DurableIdempotencyAdapter` (Properties 1–4, 7)
    - **Property 1: Cache-first duplicate detection** — For any key present in cache, `isAlreadyProcessed` returns true without DB query
    - **Property 2: Database fallback with cache warming** — For any key not in cache but in DB, `isAlreadyProcessed` returns true and key is subsequently in cache
    - **Property 3: New key detection** — For any key not in cache or DB, `isAlreadyProcessed` returns false
    - **Property 4: Post-processing cache registration round-trip** — After `markAsProcessed(key)`, `isAlreadyProcessed(key)` returns true via cache
    - **Property 7: Bounded cache with database fallback after eviction** — After eviction, keys still detected via DB
    - Use jqwik `@ForAll String` for arbitrary idempotency keys
    - Mock `PaymentAttemptJpaRepository`, use real Caffeine cache with small `maxSize`
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 3.3, 5.1, 5.2, 5.3**

  - [ ]* 2.4 Write unit tests for `DurableIdempotencyAdapter`
    - Test metric counter increments for memory hit, database hit, and new message paths
    - Test that `DataAccessException` from repository is wrapped in `IdempotencyCheckException`
    - Test INFO log on initialization with max-size and ttl values
    - _Requirements: 6.1, 6.2, 6.3, 3.1_

- [ ] 3. Add configuration properties
  - [ ] 3.1 Add idempotency cache properties to `application.yml`
    - Add `cache.idempotency.max-size: 50000`
    - Add `cache.idempotency.ttl-minutes: 30`
    - _Requirements: 5.1_

- [ ] 4. Checkpoint - Ensure adapter compiles and unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Refactor PaymentResultListener to use IdempotencyCheckPort
  - [ ] 5.1 Update `PaymentResultListener` to inject `IdempotencyCheckPort`
    - Remove `ConcurrentHashMap<String>` field (`processedKeys`)
    - Add `IdempotencyCheckPort idempotencyCheckPort` as constructor parameter
    - Replace `processedKeys.contains(key)` with `idempotencyCheckPort.isAlreadyProcessed(key)`
    - Replace `processedKeys.add(key)` with `idempotencyCheckPort.markAsProcessed(key)`
    - Add `catch (IdempotencyCheckException e)` block: log WARN with idempotency key, NACK message
    - Add `catch (DataIntegrityViolationException e)` block: log INFO, mark as processed in cache, ACK message
    - Import `com.globo.subscription.application.port.IdempotencyCheckPort`
    - Import `com.globo.subscription.application.exception.IdempotencyCheckException`
    - Import `org.springframework.dao.DataIntegrityViolationException`
    - _Requirements: 4.2, 3.1, 3.2, 2.3_

  - [ ]* 5.2 Write property tests for `PaymentResultListener` (Properties 5–6)
    - **Property 5: Constraint violation treated as duplicate** — For any message where processing throws `DataIntegrityViolationException`, listener ACKs without error
    - **Property 6: Database failure causes NACK** — For any key not in cache, if DB throws `DataAccessException`, listener NACKs message
    - Mock `IdempotencyCheckPort` and `PaymentResultProcessor`
    - Generate arbitrary `PaymentResultMessage` instances via jqwik
    - **Validates: Requirements 2.3, 3.1, 3.2**

  - [ ]* 5.3 Write unit tests for updated `PaymentResultListener`
    - Test happy path: non-duplicate → process → markAsProcessed → ACK
    - Test duplicate via port: `isAlreadyProcessed` returns true → ACK without processing
    - Test `IdempotencyCheckException` → NACK + WARN log
    - Test `DataIntegrityViolationException` → ACK + mark in cache
    - Test `JsonProcessingException` → ACK (no regression)
    - Test generic exception → NACK + ERROR log
    - _Requirements: 1.1, 1.2, 2.3, 3.1, 3.2_

- [ ] 6. Checkpoint - Ensure full build passes
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Integration and architecture verification
  - [ ]* 7.1 Write integration test for full idempotency flow
    - Test: process message → verify `payment_attempts` record exists → send same message → verify deduplication via DB
    - Test restart scenario: clear Caffeine cache, send same key → verify deduplication via DB lookup
    - Use Testcontainers for PostgreSQL
    - _Requirements: 1.3, 1.4, 2.2, 5.3_

  - [ ]* 7.2 Add ArchUnit rules for idempotency architecture
    - Assert `PaymentResultListener` depends on `IdempotencyCheckPort` not `DurableIdempotencyAdapter`
    - Assert `IdempotencyCheckPort` resides in `com.globo.subscription.application.port`
    - Assert `DurableIdempotencyAdapter` resides in `com.globo.subscription.adapter.outbound.idempotency`
    - _Requirements: 4.1, 4.2_

- [ ] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design (Properties 1–7)
- Unit tests validate specific examples and edge cases
- The `PaymentAttemptJpaRepository` does not exist yet and must be created (other repositories exist in the same package)
- No schema migration is needed — `payment_attempts` table already has UNIQUE constraint on `idempotency_key`
- The Caffeine library is already available in the project (used by `CaffeinePlanCacheAdapter` and `CaffeineSubscriptionCacheAdapter`)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["2.3", "2.4"] },
    { "id": 4, "tasks": ["5.1"] },
    { "id": 5, "tasks": ["5.2", "5.3"] },
    { "id": 6, "tasks": ["7.1", "7.2"] }
  ]
}
```
