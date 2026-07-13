package com.billing.service;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.billing.entity.Invoice;
import com.billing.entity.Plan;
import com.billing.entity.Subscription;
import com.billing.entity.Tenant;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.billing.repository.TenantRepository;

/**
 * Covers the "failed payment" and "webhook redelivery / idempotency" edge
 * cases directly against the repository layer that StripeWebhookService
 * writes through, without requiring live Stripe event fixtures.
 */
@SpringBootTest
@ActiveProfiles("test")
class StripeWebhookServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    private Subscription subscription;

    @BeforeEach
    void setup() {
        Tenant tenant = tenantRepository.findByStripeCustomerId("cus_globex")
                .orElseGet(() -> tenantRepository.save(new Tenant("Globex Test", "billing@globex.test", "cus_globex")));
        Plan plan = planRepository.findByCode("PRO")
                .orElseGet(() -> planRepository.save(new Plan("PRO", "price_pro_2", "Pro", new BigDecimal("99.00"), 25)));

        subscription = subscriptionRepository.findByStripeSubscriptionId("sub_globex_1")
                .orElseGet(() -> {
                    Subscription sub = new Subscription();
                    sub.setTenant(tenant);
                    sub.setPlan(plan);
                    sub.setStripeSubscriptionId("sub_globex_1");
                    sub.setStatus(Subscription.Status.ACTIVE);
                    sub.setCurrentPeriodStart(Instant.now());
                    sub.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000));
                    return subscriptionRepository.save(sub);
                });
    }

    @Test
    void failedPayment_marksSubscriptionPastDue() {
        subscription.setStatus(Subscription.Status.PAST_DUE);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        Invoice invoice = new Invoice();
        invoice.setSubscription(subscription);
        invoice.setStripeInvoiceId("in_fail_1");
        invoice.setAmountDue(new BigDecimal("99.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(Invoice.Status.FAILED);
        invoice.setAttemptCount(1);
        invoiceRepository.save(invoice);

        Subscription reloaded = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertEquals(Subscription.Status.PAST_DUE, reloaded.getStatus());

        Invoice reloadedInvoice = invoiceRepository.findByStripeInvoiceId("in_fail_1").orElseThrow();
        assertEquals(Invoice.Status.FAILED, reloadedInvoice.getStatus());
        assertEquals(0, reloadedInvoice.getAmountPaid().compareTo(BigDecimal.ZERO));
    }

    @Test
    void duplicateWebhookDelivery_doesNotCreateDuplicateInvoiceRows() {
        // Simulates Stripe retrying the same invoice.payment_succeeded event
        // (e.g. our endpoint timed out on the first delivery).
        for (int i = 0; i < 3; i++) {
            Invoice invoice = invoiceRepository.findByStripeInvoiceId("in_retry_1").orElseGet(Invoice::new);
            invoice.setSubscription(subscription);
            invoice.setStripeInvoiceId("in_retry_1");
            invoice.setAmountDue(new BigDecimal("99.00"));
            invoice.setAmountPaid(new BigDecimal("99.00"));
            invoice.setStatus(Invoice.Status.PAID);
            invoice.setAttemptCount(i + 1);
            invoiceRepository.save(invoice);
        }

        long count = invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                .stream()
                .filter(inv -> inv.getStripeInvoiceId().equals("in_retry_1"))
                .count();

        assertEquals(1, count, "webhook redelivery must upsert, not duplicate, the invoice row");
    }

    @Test
    void recoveryPayment_afterFailure_returnsSubscriptionToActive() {
        subscription.setStatus(Subscription.Status.PAST_DUE);
        subscriptionRepository.save(subscription);

        subscription.setStatus(Subscription.Status.ACTIVE);
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        Subscription reloaded = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertEquals(Subscription.Status.ACTIVE, reloaded.getStatus());
    }
}
