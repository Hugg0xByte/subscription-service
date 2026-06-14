package com.globo.subscription.adapter.inbound.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.globo.subscription.adapter.inbound.rest.dto.SubscriptionResponse;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;

/**
 * MapStruct mapper that converts a Subscription domain entity to SubscriptionResponse DTO.
 * Handles Money value object decomposition (amount + currency) and status enum to String conversion.
 * Requires a Plan entity parameter to resolve planName since the Subscription only holds a planId.
 */
@Mapper(componentModel = "spring")
public interface SubscriptionRestMapper {

    /**
     * Maps a Subscription domain entity to a SubscriptionResponse DTO.
     * Requires the associated Plan entity to resolve the plan name.
     *
     * @param subscription the domain subscription entity
     * @param plan the associated plan entity for name resolution
     * @return the response DTO with all fields mapped
     */
    @Mapping(target = "planName", expression = "java(plan.getName())")
    @Mapping(target = "priceAtPurchase", expression = "java(subscription.getPriceAtPurchase().amount())")
    @Mapping(target = "currency", expression = "java(subscription.getPriceAtPurchase().currency())")
    @Mapping(target = "status", expression = "java(subscription.getStatus().name())")
    @Mapping(source = "subscription.id", target = "id")
    @Mapping(source = "subscription.userId", target = "userId")
    @Mapping(source = "subscription.startDate", target = "startDate")
    @Mapping(source = "subscription.expirationDate", target = "expirationDate")
    @Mapping(source = "subscription.createdAt", target = "createdAt")
    SubscriptionResponse toResponse(Subscription subscription, Plan plan);
}
