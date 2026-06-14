package com.globo.subscription.adapter.outbound.persistence.mapper;

import org.springframework.stereotype.Component;

import com.globo.subscription.adapter.outbound.persistence.entity.PlanJpaEntity;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.vo.Money;

/**
 * Mapper that converts between Plan domain entity and PlanJpaEntity.
 * Handles the Money value object ↔ BigDecimal + String currency conversion.
 * Ensures JPA annotations do not leak to the domain layer.
 */
@Component
public class PlanPersistenceMapper {

    /**
     * Converts a domain Plan entity to a JPA entity for persistence.
     *
     * @param domain the domain entity
     * @return the JPA entity
     */
    public PlanJpaEntity toJpaEntity(Plan domain) {
        if (domain == null) {
            return null;
        }

        PlanJpaEntity jpa = new PlanJpaEntity();
        jpa.setId(domain.getId());
        jpa.setName(domain.getName());
        jpa.setDisplayName(domain.getDisplayName());
        jpa.setMonthlyPrice(domain.getMonthlyPrice().amount());
        jpa.setCurrency(domain.getMonthlyPrice().currency());
        jpa.setActive(domain.isActive());
        jpa.setCreatedAt(domain.getCreatedAt());
        return jpa;
    }

    /**
     * Converts a JPA entity to a domain Plan entity.
     *
     * @param jpa the JPA entity
     * @return the domain entity
     */
    public Plan toDomainEntity(PlanJpaEntity jpa) {
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
