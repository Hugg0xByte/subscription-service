package com.globo.subscription.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.globo.subscription.domain.enums.SubscriptionStatus;
import com.globo.subscription.domain.event.DomainEvent;
import com.globo.subscription.domain.event.PaymentApproved;
import com.globo.subscription.domain.event.PaymentFailed;
import com.globo.subscription.domain.event.SubscriptionCanceled;
import com.globo.subscription.domain.event.SubscriptionRenewed;
import com.globo.subscription.domain.event.SubscriptionSuspended;
import com.globo.subscription.domain.vo.Money;

/**
 * Domain entity representing a user subscription.
 * Contains all business logic for subscription lifecycle management.
 * Pure domain object with no framework dependencies.
 */
public class Subscription {

    private UUID id;
    private UUID userId;
    private UUID planId;
    private Money priceAtPurchase;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate expirationDate;
    private Instant cancelRequestedAt;
    private Instant suspendedAt;
    private int failedAttempts;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public Subscription(UUID id, UUID userId, UUID planId, Money priceAtPurchase,
                        SubscriptionStatus status, LocalDate startDate, LocalDate expirationDate,
                        Instant cancelRequestedAt, Instant suspendedAt, int failedAttempts,
                        long version, Instant createdAt, Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("Subscription id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Subscription userId must not be null");
        }
        if (planId == null) {
            throw new IllegalArgumentException("Subscription planId must not be null");
        }
        if (priceAtPurchase == null) {
            throw new IllegalArgumentException("Subscription priceAtPurchase must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Subscription status must not be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Subscription startDate must not be null");
        }
        if (expirationDate == null) {
            throw new IllegalArgumentException("Subscription expirationDate must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Subscription createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Subscription updatedAt must not be null");
        }

        this.id = id;
        this.userId = userId;
        this.planId = planId;
        this.priceAtPurchase = priceAtPurchase;
        this.status = status;
        this.startDate = startDate;
        this.expirationDate = expirationDate;
        this.cancelRequestedAt = cancelRequestedAt;
        this.suspendedAt = suspendedAt;
        this.failedAttempts = failedAttempts;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Processes a successful payment: advances expiration date by 1 month,
     * resets failed attempts, sets status to ATIVA, and registers domain events.
     *
     * @throws IllegalStateException if subscription is CANCELADA or not eligible for renewal
     */
    public void processSuccessfulPayment() {
        rejectIfCancelada();
        validateEligibleForRenewal();

        this.expirationDate = this.expirationDate.plusMonths(1);
        this.failedAttempts = 0;
        this.status = SubscriptionStatus.ATIVA;
        this.updatedAt = Instant.now();

        domainEvents.add(new PaymentApproved(this.id, null, Instant.now()));
        domainEvents.add(new SubscriptionRenewed(this.id, this.expirationDate, Instant.now()));
    }

    /**
     * Processes a failed payment: increments failed attempts.
     * If failed attempts reach 3, sets status to SUSPENSA and records suspension time.
     * Otherwise sets status to PENDENTE_PAGAMENTO.
     *
     * @throws IllegalStateException if subscription is CANCELADA or not eligible for renewal
     */
    public void processFailedPayment() {
        rejectIfCancelada();
        validateEligibleForRenewal();

        this.failedAttempts++;
        this.updatedAt = Instant.now();

        if (this.failedAttempts >= 3) {
            this.status = SubscriptionStatus.SUSPENSA;
            this.suspendedAt = Instant.now();
            domainEvents.add(new PaymentFailed(this.id, this.failedAttempts, null, null, Instant.now()));
            domainEvents.add(new SubscriptionSuspended(this.id, this.failedAttempts, this.suspendedAt, Instant.now()));
        } else {
            this.status = SubscriptionStatus.PENDENTE_PAGAMENTO;
            domainEvents.add(new PaymentFailed(this.id, this.failedAttempts, null, null, Instant.now()));
        }
    }

    /**
     * Requests cancellation without changing the current status.
     * Records the cancellation request timestamp and registers SubscriptionCanceled event.
     *
     * @throws IllegalStateException if subscription is CANCELADA
     */
    public void requestCancellation() {
        rejectIfCancelada();

        this.cancelRequestedAt = Instant.now();
        this.updatedAt = Instant.now();

        domainEvents.add(new SubscriptionCanceled(this.id, this.cancelRequestedAt, Instant.now()));
    }

    /**
     * Effectuates the cancellation when expiration date is reached.
     * Transitions status to CANCELADA. Called by the scheduler when
     * cancel_requested_at IS NOT NULL and expiration_date <= today.
     *
     * @throws IllegalStateException if subscription is already CANCELADA
     */
    public void effectuateCancellation() {
        rejectIfCancelada();
        this.status = SubscriptionStatus.CANCELADA;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the subscription as pending payment (PENDENTE_PAGAMENTO).
     * Valid only when the current status is ATIVA or PENDENTE_PAGAMENTO.
     *
     * @throws IllegalStateException if subscription is CANCELADA or not eligible for this transition
     */
    public void markAsPendingPayment() {
        rejectIfCancelada();
        if (this.status != SubscriptionStatus.ATIVA && this.status != SubscriptionStatus.PENDENTE_PAGAMENTO) {
            throw new IllegalStateException(
                    "Cannot mark as pending payment. Current status: " + this.status);
        }
        this.status = SubscriptionStatus.PENDENTE_PAGAMENTO;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if the subscription is eligible for renewal.
     * A subscription is eligible if its status is ATIVA or PENDENTE_PAGAMENTO.
     *
     * @return true if eligible for renewal, false otherwise
     */
    public boolean isEligibleForRenewal() {
        return this.status == SubscriptionStatus.ATIVA
                || this.status == SubscriptionStatus.PENDENTE_PAGAMENTO;
    }

    /**
     * Returns an unmodifiable view of the domain events registered on this entity.
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all registered domain events.
     * Typically called after events have been published.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // --- Private helper methods ---

    private void rejectIfCancelada() {
        if (this.status == SubscriptionStatus.CANCELADA) {
            throw new IllegalStateException(
                    "Cannot perform operation on a subscription with status CANCELADA");
        }
    }

    private void validateEligibleForRenewal() {
        if (!isEligibleForRenewal()) {
            throw new IllegalStateException(
                    "Subscription is not eligible for renewal. Current status: " + this.status);
        }
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Money getPriceAtPurchase() {
        return priceAtPurchase;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
