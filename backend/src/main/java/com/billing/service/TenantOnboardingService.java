package com.billing.service;

import com.billing.dto.CreateTenantRequest;
import com.billing.dto.SubscriptionDto;
import com.billing.entity.Plan;
import com.billing.entity.Subscription;
import com.billing.entity.Tenant;
import com.billing.exception.PaymentFailedException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.billing.repository.TenantRepository;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(TenantOnboardingService.class);

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public TenantOnboardingService(TenantRepository tenantRepository,
                                    PlanRepository planRepository,
                                    SubscriptionRepository subscriptionRepository) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public SubscriptionDto onboardTenant(CreateTenantRequest request) {
        Plan plan = planRepository.findByCode(request.getPlanCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown plan: " + request.getPlanCode()));

        try {
            Customer customer = Customer.create(CustomerCreateParams.builder()
                    .setName(request.getName())
                    .setEmail(request.getEmail())
                    .setPaymentMethod(request.getPaymentMethodId())
                    .setInvoiceSettings(
                            CustomerCreateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(request.getPaymentMethodId())
                                    .build())
                    .build());

            Tenant tenant = new Tenant(request.getName(), request.getEmail(), customer.getId());
            tenant = tenantRepository.save(tenant);

            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(plan.getStripePriceId())
                            .build())
                    // Expand latest_invoice.payment_intent so we can immediately detect
                    // a failed initial payment instead of finding out only via webhook.
                    .addExpand("latest_invoice.payment_intent")
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .build();

            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.create(params);

            Subscription subscription = new Subscription();
            subscription.setTenant(tenant);
            subscription.setPlan(plan);
            subscription.setStripeSubscriptionId(stripeSub.getId());
            subscription.setStatus(SubscriptionService.mapStripeStatus(stripeSub.getStatus()));
            subscription.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
            subscription = subscriptionRepository.save(subscription);

            if (subscription.getStatus() == Subscription.Status.INCOMPLETE) {
                log.warn("Initial payment for tenant {} is incomplete (requires action or failed)", tenant.getId());
            }

            return SubscriptionDto.from(subscription);

        } catch (CardException e) {
            // Card declined synchronously during initial charge
            log.warn("Card declined during onboarding for {}: {}", request.getEmail(), e.getMessage());
            throw new PaymentFailedException("Payment failed: " + e.getMessage());
        } catch (StripeException e) {
            log.error("Stripe error during onboarding", e);
            throw new RuntimeException("Failed to onboard tenant: " + e.getMessage(), e);
        }
    }
}
