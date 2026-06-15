package com.globo.subscription.adapter.inbound.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.globo.subscription.application.port.LockManagerPort;
import com.globo.subscription.application.usecase.RenewExpiredSubscriptionsUseCase;

/**
 * Inbound adapter that triggers subscription renewal processing on a configurable cron schedule.
 * Acquires a distributed lock before processing to prevent duplicate executions across instances.
 * Delegates all business logic to RenewExpiredSubscriptionsUseCase.
 */
@Component
public class RenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(RenewalScheduler.class);
    private static final String LOCK_NAME = "renewal-scheduler";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RenewExpiredSubscriptionsUseCase renewExpiredSubscriptionsUseCase;
    private final LockManagerPort lockManagerPort;
    private final int batchSize;

    public RenewalScheduler(RenewExpiredSubscriptionsUseCase renewExpiredSubscriptionsUseCase,
                            LockManagerPort lockManagerPort,
                            @Value("${scheduler.renewal.batch-size:100}") int batchSize) {
        this.renewExpiredSubscriptionsUseCase = renewExpiredSubscriptionsUseCase;
        this.lockManagerPort = lockManagerPort;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${scheduler.renewal.cron:0 0 * * * *}")
    public void processRenewals() {
        Instant start = Instant.now();
        log.info("Renewal batch started.");

        boolean lockAcquired = false;
        try {
            lockAcquired = lockManagerPort.acquireLock(LOCK_NAME, LOCK_TTL);
            if (!lockAcquired) {
                log.warn("Could not acquire lock '{}'. Skipping renewal batch execution.", LOCK_NAME);
                return;
            }

            renewExpiredSubscriptionsUseCase.execute(LocalDate.now(), batchSize);
        } catch (Exception e) {
            log.error("Unexpected error during renewal batch processing: {}", e.getMessage(), e);
        } finally {
            if (lockAcquired) {
                lockManagerPort.releaseLock(LOCK_NAME);
            }
            Duration duration = Duration.between(start, Instant.now());
            log.info("Renewal batch ended. Total duration: {}ms", duration.toMillis());
        }
    }
}
