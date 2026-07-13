package com.billing.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.billing.entity.Plan;
import com.billing.entity.Subscription;
import com.billing.entity.Tenant;
import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.billing.repository.TenantRepository;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedDemoData(PlanRepository planRepository,
                                   TenantRepository tenantRepository,
                                   SubscriptionRepository subscriptionRepository) {
        return args -> {
            Plan basic = ensurePlan(planRepository, "BASIC", "price_basic_demo", "Basic", new BigDecimal("10.00"), 5);
            Plan pro = ensurePlan(planRepository, "PRO", "price_pro_demo", "Pro", new BigDecimal("25.00"), 20);
            Plan enterprise = ensurePlan(planRepository, "ENTERPRISE", "price_enterprise_demo", "Enterprise", new BigDecimal("99.00"), 100);

            Tenant acme = ensureTenant(tenantRepository, "Acme Corp", "acme@example.com", "cus_acme_demo");
            Tenant globex = ensureTenant(tenantRepository, "Globex", "globex@example.com", "cus_globex_demo");

            if (subscriptionRepository.count() == 0) {
                Instant now = Instant.now();
                subscriptionRepository.saveAll(List.of(
                        createSubscription(acme, basic, "sub_acme_basic", Subscription.Status.ACTIVE, now.minusSeconds(86400 * 30), now.plusSeconds(86400 * 30)),
                        createSubscription(globex, pro, "sub_globex_pro", Subscription.Status.ACTIVE, now.minusSeconds(86400 * 15), now.plusSeconds(86400 * 45))
                ));
            }
        };
    }

    private Plan ensurePlan(PlanRepository planRepository, String code, String stripePriceId, String name,
                            BigDecimal monthlyPrice, Integer seatLimit) {
        return planRepository.findByCode(code).orElseGet(() -> planRepository.save(
                new Plan(code, stripePriceId, name, monthlyPrice, seatLimit)));
    }

    private Tenant ensureTenant(TenantRepository tenantRepository, String name, String email, String stripeCustomerId) {
        return tenantRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseGet(() -> tenantRepository.save(new Tenant(name, email, stripeCustomerId)));
    }

    private Subscription createSubscription(Tenant tenant, Plan plan, String stripeSubscriptionId,
                                            Subscription.Status status, Instant currentPeriodStart,
                                            Instant currentPeriodEnd) {
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(plan);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(currentPeriodStart);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }
}
