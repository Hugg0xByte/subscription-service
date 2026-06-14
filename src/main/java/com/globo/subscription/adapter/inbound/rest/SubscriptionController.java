package com.globo.subscription.adapter.inbound.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.globo.subscription.adapter.inbound.rest.dto.CreateSubscriptionRequest;
import com.globo.subscription.adapter.inbound.rest.dto.SubscriptionResponse;
import com.globo.subscription.adapter.inbound.rest.mapper.SubscriptionRestMapper;
import com.globo.subscription.application.port.PlanRepositoryPort;
import com.globo.subscription.application.usecase.CancelSubscriptionUseCase;
import com.globo.subscription.application.usecase.CreateSubscriptionUseCase;
import com.globo.subscription.application.usecase.GetActiveSubscriptionUseCase;
import com.globo.subscription.domain.entity.Plan;
import com.globo.subscription.domain.entity.Subscription;

import jakarta.validation.Valid;

/**
 * REST controller for subscription management endpoints.
 * Delegates business logic to application use cases and maps domain entities to response DTOs.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final CreateSubscriptionUseCase createSubscriptionUseCase;
    private final GetActiveSubscriptionUseCase getActiveSubscriptionUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;
    private final SubscriptionRestMapper subscriptionRestMapper;
    private final PlanRepositoryPort planRepositoryPort;

    public SubscriptionController(CreateSubscriptionUseCase createSubscriptionUseCase,
                                  GetActiveSubscriptionUseCase getActiveSubscriptionUseCase,
                                  CancelSubscriptionUseCase cancelSubscriptionUseCase,
                                  SubscriptionRestMapper subscriptionRestMapper,
                                  PlanRepositoryPort planRepositoryPort) {
        this.createSubscriptionUseCase = createSubscriptionUseCase;
        this.getActiveSubscriptionUseCase = getActiveSubscriptionUseCase;
        this.cancelSubscriptionUseCase = cancelSubscriptionUseCase;
        this.subscriptionRestMapper = subscriptionRestMapper;
        this.planRepositoryPort = planRepositoryPort;
    }

    /**
     * Creates a new subscription for a user.
     *
     * @param request the subscription creation request containing userId and planId
     * @return 201 Created with the subscription response
     */
    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        Subscription subscription = createSubscriptionUseCase.execute(request.userId(), request.planId());
        Plan plan = planRepositoryPort.findById(subscription.getPlanId())
                .orElseThrow();
        SubscriptionResponse response = subscriptionRestMapper.toResponse(subscription, plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves the active subscription for a given user.
     *
     * @param userId the ID of the user whose active subscription is requested
     * @return 200 OK with the subscription response, or 404 if no active subscription exists
     */
    @GetMapping("/active")
    public ResponseEntity<SubscriptionResponse> getActiveSubscription(@RequestParam UUID userId) {
        return getActiveSubscriptionUseCase.execute(userId)
                .map(subscription -> {
                    Plan plan = planRepositoryPort.findById(subscription.getPlanId())
                            .orElseThrow();
                    return ResponseEntity.ok(subscriptionRestMapper.toResponse(subscription, plan));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cancels a subscription by its ID.
     *
     * @param id the ID of the subscription to cancel
     * @return 204 No Content on successful cancellation
     */
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSubscription(@PathVariable UUID id) {
        cancelSubscriptionUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
