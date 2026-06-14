package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.springframework.stereotype.Component;

import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionJpaEntity;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.vo.Money;

/**
 * Mapper that converts between Subscription domain entity and SubscriptionJpaEntity.
 * Handles the Money value object ↔ BigDecimal + String currency conversion.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Component
public class SubscriptionPersistenceMapper {

    /**
     * Converts a domain Subscription entity to a JPA entity for persistence.
     *
     * @param domain the domain entity
     * @return the JPA entity
     */
    public SubscriptionJpaEntity toJpaEntity(Subscription domain) {
        if (domain == null) {
            return null;
        }

        SubscriptionJpaEntity jpa = new SubscriptionJpaEntity();
        jpa.setId(domain.getId());
        jpa.setUserId(domain.getUserId());
        jpa.setPlanId(domain.getPlanId());
        jpa.setPriceAtPurchase(domain.getPriceAtPurchase().amount());
        jpa.setCurrencyAtPurchase(domain.getPriceAtPurchase().currency());
        jpa.setStatus(domain.getStatus());
        jpa.setStartDate(domain.getStartDate());
        jpa.setExpirationDate(domain.getExpirationDate());
        jpa.setCancelRequestedAt(domain.getCancelRequestedAt());
        jpa.setSuspendedAt(domain.getSuspendedAt());
        jpa.setFailedAttempts(domain.getFailedAttempts());
        jpa.setVersion(domain.getVersion());
        jpa.setCreatedAt(domain.getCreatedAt());
        jpa.setUpdatedAt(domain.getUpdatedAt());
        return jpa;
    }

    /**
     * Converts a JPA entity to a domain Subscription entity.
     *
     * @param jpa the JPA entity
     * @return the domain entity
     */
    public Subscription toDomainEntity(SubscriptionJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }

        Money priceAtPurchase = new Money(jpa.getPriceAtPurchase(), jpa.getCurrencyAtPurchase());

        return new Subscription(
                jpa.getId(),
                jpa.getUserId(),
                jpa.getPlanId(),
                priceAtPurchase,
                jpa.getStatus(),
                jpa.getStartDate(),
                jpa.getExpirationDate(),
                jpa.getCancelRequestedAt(),
                jpa.getSuspendedAt(),
                jpa.getFailedAttempts(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}
