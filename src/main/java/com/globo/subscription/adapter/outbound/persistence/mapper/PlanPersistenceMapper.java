package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.globo.subscription.adapter.outbound.persistence.entity.PlanJpaEntity;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.vo.Money;

/**
 * MapStruct mapper that converts between Plan domain entity and PlanJpaEntity.
 * Handles the Money value object ↔ BigDecimal + String currency conversion.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Mapper(componentModel = "spring")
public interface PlanPersistenceMapper {

    /**
     * Converts a domain Plan entity to a JPA entity for persistence.
     * Decomposes Money into monthlyPrice (BigDecimal) + currency (String).
     */
    @Mapping(target = "monthlyPrice", expression = "java(domain.getMonthlyPrice().amount())")
    @Mapping(target = "currency", expression = "java(domain.getMonthlyPrice().currency())")
    PlanJpaEntity toJpaEntity(Plan domain);

    /**
     * Converts a JPA entity to a domain Plan entity.
     * Recomposes BigDecimal + currency into Money value object.
     */
    default Plan toDomainEntity(PlanJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        Money monthlyPrice = new Money(jpa.getMonthlyPrice(), jpa.getCurrency());
        return new Plan(
                jpa.getId(),
                jpa.getName(),
                jpa.getDisplayName(),
                monthlyPrice,
                jpa.isActive(),
                jpa.getCreatedAt()
        );
    }
}
