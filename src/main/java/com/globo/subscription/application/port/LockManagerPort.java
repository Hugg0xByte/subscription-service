package com.globo.subscription.application.port;

import java.time.Duration;

/**
 * Port interface for distributed lock management.
 * Implemented by outbound adapters (e.g., InMemoryLockManagerAdapter for local dev).
 */
public interface LockManagerPort {

    boolean acquireLock(String lockName, Duration ttl);

    void releaseLock(String lockName);
}
