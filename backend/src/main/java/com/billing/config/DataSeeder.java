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
            if (planRepository.count() == 0) {
                planRepository.saveAll(List.of(
                        new Plan("BASIC", "price_basic_demo", "Basic", new BigDecimal("10.00"), 5),
                        new Plan("PRO", "price_pro_demo", "Pro", new BigDecimal("25.00"), 20),
                        new Plan("ENTERPRISE", "price_enterprise_demo", "Enterprise", new BigDecimal("99.00"), 100)
                ));
            }

            if (tenantRepository.count() == 0) {
                Tenant acme = tenantRepository.save(new Tenant("Acme Corp", "acme@example.com", "cus_acme_demo"));
                Tenant globex = tenantRepository.save(new Tenant("Globex", "globex@example.com", "cus_globex_demo"));

                Plan basic = planRepository.findByCode("BASIC").orElseThrow();
                Plan pro = planRepository.findByCode("PRO").orElseThrow();

                Instant now = Instant.now();
                subscriptionRepository.saveAll(List.of(
                        createSubscription(acme, basic, "sub_acme_basic", Subscription.Status.ACTIVE, now.minusSeconds(86400 * 30), now.plusSeconds(86400 * 30)),
                        createSubscription(globex, pro, "sub_globex_pro", Subscription.Status.ACTIVE, now.minusSeconds(86400 * 15), now.plusSeconds(86400 * 45))
                ));
            }
        };
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
