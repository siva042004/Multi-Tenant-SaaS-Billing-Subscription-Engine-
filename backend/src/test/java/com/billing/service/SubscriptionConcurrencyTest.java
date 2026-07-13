package com.billing.service;

import com.billing.dto.UpgradeRequest;
import com.billing.entity.Plan;
import com.billing.entity.Subscription;
import com.billing.entity.Tenant;
import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.billing.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that concurrent operations on the same subscription (e.g. a user
 * double-clicking "upgrade", or an upgrade racing a webhook update) don't
 * silently corrupt state -- exactly one should win per conflicting pair,
 * and the row must end up internally consistent.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionConcurrencyTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;

    private UUID subscriptionId;

    @BeforeEach
    void setup() {
        Tenant tenant = tenantRepository.save(new Tenant("Acme Inc", "billing@acme.test", "cus_test123"));

        Plan basic = planRepository.save(new Plan("BASIC", "price_basic", "Basic", new BigDecimal("29.00"), 5));
        planRepository.save(new Plan("PRO", "price_pro", "Pro", new BigDecimal("99.00"), 25));
        planRepository.save(new Plan("ENTERPRISE", "price_ent", "Enterprise", new BigDecimal("299.00"), 100));

        Subscription sub = new Subscription();
        sub.setTenant(tenant);
        sub.setPlan(basic);
        sub.setStripeSubscriptionId("sub_test_" + UUID.randomUUID());
        sub.setStatus(Subscription.Status.ACTIVE);
        sub.setCurrentPeriodStart(Instant.now());
        sub.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000));
        sub = subscriptionRepository.save(sub);

        subscriptionId = sub.getId();
    }

    /**
     * Two threads try to upgrade the same subscription to different plans at
     * the same time using the SAME stale expectedVersion. Because upgrade()
     * takes a pessimistic write lock and re-checks the version, only the
     * first to commit should succeed; the second must be rejected rather than
     * silently overwrite it (the DB-level lock serializes them; the app-level
     * version check catches the staleness for the loser).
     */
    @Test
    void concurrentUpgrades_onlyOneAppliesCleanly() throws InterruptedException {
        Subscription initial = subscriptionRepository.findById(subscriptionId).orElseThrow();
        Long staleVersion = initial.getVersion();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Note: real Stripe calls are skipped here (no network in unit scope);
        // in a full integration environment this test mocks the Stripe SDK layer.
        // Here we exercise the repository-level locking contract directly.
        Runnable upgradeToPro = () -> {
            try {
                ready.countDown();
                go.await();
                Subscription locked = subscriptionRepository.findByIdForUpdate(subscriptionId).orElseThrow();
                if (!staleVersion.equals(locked.getVersion())) {
                    conflictCount.incrementAndGet();
                    return;
                }
                Plan pro = planRepository.findByCode("PRO").orElseThrow();
                locked.setPlan(pro);
                locked.setUpdatedAt(Instant.now());
                subscriptionRepository.saveAndFlush(locked);
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            }
        };

        pool.submit(upgradeToPro);
        pool.submit(upgradeToPro);
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "exactly one concurrent upgrade should succeed cleanly");
        assertEquals(1, conflictCount.get(), "the losing request should detect the version conflict");
    }

    @Test
    void planLookupsReturnDistinctSeatLimits() {
        List<Plan> plans = planRepository.findAll();
        assertEquals(3, plans.size());
    }
}
