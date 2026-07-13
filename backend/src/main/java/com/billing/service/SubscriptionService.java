package com.billing.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.dto.SubscriptionDto;
import com.billing.dto.UpgradeRequest;
import com.billing.entity.Plan;
import com.billing.entity.Subscription;
import com.billing.exception.ConcurrentUpdateException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.param.SubscriptionUpdateParams;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    public List<SubscriptionDto> listAll() {
        return subscriptionRepository.findAll().stream()
                .map(SubscriptionDto::from)
                .toList();
    }

    public List<SubscriptionDto> listForTenant(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId).stream()
                .map(SubscriptionDto::from)
                .toList();
    }

    public SubscriptionDto get(UUID id) {
        return SubscriptionDto.from(findOrThrow(id));
    }

    /**
     * Upgrade or downgrade a subscription's plan with proration.
     *
     * Concurrency strategy: we take a pessimistic write lock on the row for the
     * duration of the transaction (protects against two upgrade requests racing),
     * AND check an optimistic version token supplied by the client (protects
     * against the client acting on stale data, e.g. after a webhook already
     * changed the subscription's status).
     */
    @Transactional
    public SubscriptionDto upgrade(UUID subscriptionId, UpgradeRequest request) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (request.getExpectedVersion() != null
                && !request.getExpectedVersion().equals(subscription.getVersion())) {
            throw new ConcurrentUpdateException(
                    "Subscription has changed since it was loaded (expected version "
                            + request.getExpectedVersion() + " but found " + subscription.getVersion() + ")");
        }

        if (subscription.getStatus() == Subscription.Status.CANCELED) {
            throw new IllegalStateException("Cannot change plan on a canceled subscription");
        }

        Plan newPlan = planRepository.findByCode(request.getNewPlanCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown plan: " + request.getNewPlanCode()));

        if (newPlan.getCode().equals(subscription.getPlan().getCode())) {
            return SubscriptionDto.from(subscription); // no-op, idempotent
        }

        try {
            com.stripe.model.Subscription stripeSub =
                    com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());

            String currentItemId = stripeSub.getItems().getData().get(0).getId();

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    // Stripe computes prorated charges/credits automatically for the
                    // remainder of the billing period when proration_behavior=create_prorations.
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(currentItemId)
                            .setPrice(newPlan.getStripePriceId())
                            .build())
                    .build();

            com.stripe.model.Subscription updated = stripeSub.update(params);

            subscription.setPlan(newPlan);
            subscription.setStatus(mapStripeStatus(updated.getStatus()));
            subscription.setCurrentPeriodStart(Instant.ofEpochSecond(updated.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(Instant.ofEpochSecond(updated.getCurrentPeriodEnd()));
            subscription.setUpdatedAt(Instant.now());

            log.info("Subscription {} upgraded to plan {} with proration", subscriptionId, newPlan.getCode());
            return SubscriptionDto.from(subscriptionRepository.save(subscription));

        } catch (StripeException e) {
            log.error("Stripe error while upgrading subscription {}", subscriptionId, e);
            throw new RuntimeException("Failed to update subscription in Stripe: " + e.getMessage(), e);
        }
    }

    @Transactional
    public SubscriptionDto cancel(UUID subscriptionId, boolean immediately) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        try {
            if (immediately) {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
                stripeSub.cancel();
                subscription.setStatus(Subscription.Status.CANCELED);
                subscription.setCancelAtPeriodEnd(false);
            } else {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
                SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true)
                        .build();
                stripeSub.update(params);
                subscription.setCancelAtPeriodEnd(true);
            }
            subscription.setUpdatedAt(Instant.now());
            return SubscriptionDto.from(subscriptionRepository.save(subscription));
        } catch (StripeException e) {
            log.error("Stripe error while canceling subscription {}", subscriptionId, e);
            throw new RuntimeException("Failed to cancel subscription in Stripe: " + e.getMessage(), e);
        }
    }

    private Subscription findOrThrow(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
    }

    public static Subscription.Status mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> Subscription.Status.ACTIVE;
            case "past_due" -> Subscription.Status.PAST_DUE;
            case "canceled" -> Subscription.Status.CANCELED;
            case "trialing" -> Subscription.Status.TRIALING;
            default -> Subscription.Status.INCOMPLETE;
        };
    }
}
