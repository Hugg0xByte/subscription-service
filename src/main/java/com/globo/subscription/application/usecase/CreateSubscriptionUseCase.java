package com.globo.subscription.application.usecase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.globo.subscription.application.exception.ActiveSubscriptionExistsException;
import com.globo.subscription.application.exception.PlanNotFoundException;
import com.globo.subscription.application.port.EventPublisherPort;
import com.globo.subscription.application.port.PlanCachePort;
import com.globo.subscription.application.port.PlanRepositoryPort;
import com.globo.subscription.application.port.SubscriptionCachePort;
import com.globo.subscription.application.port.SubscriptionRepositoryPort;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.SubscriptionCreated;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Use case responsible for creating a new subscription.
 * Validates no active subscription exists, retrieves the plan (with cache),
 * creates the subscription, persists it, evicts cache, and publishes the domain event.
 */
@Service
@Transactional
public class CreateSubscriptionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateSubscriptionUseCase.class);

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final PlanCachePort planCachePort;
    private final PlanRepositoryPort planRepositoryPort;
    private final SubscriptionCachePort subscriptionCachePort;
    private final EventPublisherPort eventPublisherPort;
    private final Timer executionTimer;
    private final Counter executionCounter;

    public CreateSubscriptionUseCase(SubscriptionRepositoryPort subscriptionRepositoryPort,
                                     PlanCachePort planCachePort,
                                     PlanRepositoryPort planRepositoryPort,
                                     SubscriptionCachePort subscriptionCachePort,
                                     EventPublisherPort eventPublisherPort,
                                     MeterRegistry meterRegistry) {
        this.subscriptionRepositoryPort = subscriptionRepositoryPort;
        this.planCachePort = planCachePort;
        this.planRepositoryPort = planRepositoryPort;
        this.subscriptionCachePort = subscriptionCachePort;
        this.eventPublisherPort = eventPublisherPort;
        this.executionTimer = Timer.builder("subscription.usecase.duration")
                .tag("usecase", "create_subscription")
                .description("Duration of CreateSubscriptionUseCase execution")
                .register(meterRegistry);
        this.executionCounter = Counter.builder("subscription.usecase.count")
                .tag("usecase", "create_subscription")
                .description("Number of CreateSubscriptionUseCase executions")
                .register(meterRegistry);
    }

    /**
     * Creates a new subscription for the given user and plan.
     *
     * @param userId the ID of the user subscribing
     * @param planId the ID of the plan to subscribe to
     * @return the persisted Subscription entity
     * @throws ActiveSubscriptionExistsException if the user already has an active subscription
     * @throws PlanNotFoundException if the plan cannot be found
     */
    public Subscription execute(UUID userId, UUID planId) {
        executionCounter.increment();
        return executionTimer.record(() -> {
            log.info("Creating subscription for userId={}, planId={}", userId, planId);

            // 1. Validate no active subscription exists for the user
            if (subscriptionRepositoryPort.existsActiveForUser(userId)) {
                throw new ActiveSubscriptionExistsException(userId);
            }

            // 2. Retrieve Plan via cache (with fallback to repository on miss)
            Plan plan = findPlan(planId);

            // 3. Create new Subscription with status ATIVA and priceAtPurchase snapshot
            Instant now = Instant.now();
            LocalDate today = LocalDate.now();
            LocalDate expirationDate = today.plusMonths(1);

            Subscription subscription = new Subscription(
                    UUID.randomUUID(),
                    userId,
                    planId,
                    plan.getMonthlyPrice(),
                    SubscriptionStatus.ATIVA,
                    today,
                    expirationDate,
                    null,   // cancelRequestedAt
                    null,   // suspendedAt
                    0,      // failedAttempts
                    0L,     // version
                    now,
                    now
            );

            // 4. Persist the subscription
            Subscription persisted = subscriptionRepositoryPort.save(subscription);

            // 5. Evict subscription cache for the user
            subscriptionCachePort.evictActiveSubscription(userId);

            // 6. Publish SubscriptionCreated domain event
            SubscriptionCreated event = new SubscriptionCreated(
                    persisted.getId(),
                    userId,
                    planId,
                    persisted.getPriceAtPurchase(),
                    persisted.getStartDate(),
                    persisted.getExpirationDate(),
                    Instant.now()
            );
            eventPublisherPort.publish(event);

            log.info("Subscription created successfully: id={}", persisted.getId());
            return persisted;
        });
    }

    /**
     * Finds a plan by ID, first checking the plan cache, then falling back to the repository.
     * On cache miss, populates the cache with all active plans from the repository.
     */
    private Plan findPlan(UUID planId) {
        // Try cache first
        var cachedPlans = planCachePort.getAllActivePlans();
        if (cachedPlans.isPresent()) {
            return cachedPlans.get().stream()
                    .filter(p -> p.getId().equals(planId))
                    .findFirst()
                    .orElseThrow(() -> new PlanNotFoundException(planId));
        }

        // Cache miss: query repository and populate cache
        List<Plan> allActivePlans = planRepositoryPort.findAllActive();
        planCachePort.putAllActivePlans(allActivePlans);

        return allActivePlans.stream()
                .filter(p -> p.getId().equals(planId))
                .findFirst()
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }
}
