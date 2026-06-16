package com.globo.subscription.adapter.outbound.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.globo.subscription.application.port.LockManagerPort;

/**
 * In-memory implementation of LockManagerPort for single-instance local development.
 * Uses ReentrantLock per lock name with TTL-based expiration.
 */
@Component
public class InMemoryLockManagerAdapter implements LockManagerPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLockManagerAdapter.class);

    private final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean acquireLock(String lockName, Duration ttl) {
        LockEntry entry = locks.computeIfAbsent(lockName, k -> new LockEntry());

        if (entry.lock.tryLock()) {
            entry.expiresAt = Instant.now().plus(ttl);
            log.info("Lock acquired: name={}, ttl={}", lockName, ttl);
            return true;
        }

        // Check if the existing lock has expired
        if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) {
            // Force acquire expired lock
            entry.lock = new ReentrantLock();
            entry.lock.lock();
            entry.expiresAt = Instant.now().plus(ttl);
            log.info("Expired lock reclaimed: name={}, ttl={}", lockName, ttl);
            return true;
        }

        log.warn("Lock not acquired (already held): name={}", lockName);
        return false;
    }

    @Override
    public void releaseLock(String lockName) {
        LockEntry entry = locks.get(lockName);
        if (entry != null && entry.lock.isHeldByCurrentThread()) {
            entry.expiresAt = null;
            entry.lock.unlock();
            log.info("Lock released: name={}", lockName);
        } else {
            log.warn("Lock release attempted but not held by current thread: name={}", lockName);
        }
    }

    private static class LockEntry {
        volatile ReentrantLock lock = new ReentrantLock();
        volatile Instant expiresAt;
    }
}
