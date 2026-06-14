package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.globo.subscription.adapter.outbound.persistence.entity.SubscriptionJpaEntity;
import com.globo.subscription.domain.entity.Subscription;
import com.globo.subscription.domain.vo.Money;

/**
 * MapStruct mapper that converts between Subscription domain entity and SubscriptionJpaEntity.
 * Handles the Money value object ↔ BigDecimal + String currency conversion.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Mapper(componentModel = "spring")
public interface SubscriptionPersistenceMapper {

    /**
     * Converts a domain Subscription entity to a JPA entity for persistence.
     * Decomposes Money into priceAtPurchase (BigDecimal) + currencyAtPurchase (String).
     */
    @Mapping(target = "priceAtPurchase", expression = "java(domain.getPriceAtPurchase().amount())")
    @Mapping(target = "currencyAtPurchase", expression = "java(domain.getPriceAtPurchase().currency())")
    SubscriptionJpaEntity toJpaEntity(Subscription domain);

    /**
     * Converts a JPA entity to a domain Subscription entity.
     * Recomposes BigDecimal + currency into Money value object.
     */
    default Subscription toDomainEntity(SubscriptionJpaEntity jpa) {
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
