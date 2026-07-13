package com.billing.service;

import com.billing.entity.Invoice;
import com.billing.entity.Subscription;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.SubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionEventPublisher eventPublisher;

    public StripeWebhookService(SubscriptionRepository subscriptionRepository,
                                 InvoiceRepository invoiceRepository,
                                 SubscriptionEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Dispatches verified Stripe events. Idempotent: replaying the same event
     * (Stripe retries on non-2xx, or manual redelivery) must not double-apply effects.
     */
    @Transactional
    public void handle(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> objOpt = deserializer.getObject();
        if (objOpt.isEmpty()) {
            log.warn("Could not deserialize Stripe event {} of type {}", event.getId(), event.getType());
            return;
        }
        StripeObject obj = objOpt.get();

        switch (event.getType()) {
            case "invoice.payment_succeeded" -> handleInvoicePaid((com.stripe.model.Invoice) obj);
            case "invoice.payment_failed" -> handleInvoiceFailed((com.stripe.model.Invoice) obj);
            case "customer.subscription.updated" -> handleSubscriptionUpdated((com.stripe.model.Subscription) obj);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted((com.stripe.model.Subscription) obj);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleInvoicePaid(com.stripe.model.Invoice stripeInvoice) {
        subscriptionRepository.findByStripeSubscriptionId(stripeInvoice.getSubscription())
                .ifPresentOrElse(sub -> {
                    upsertInvoice(sub, stripeInvoice, Invoice.Status.PAID);
                    // A successful payment always clears PAST_DUE.
                    if (sub.getStatus() != Subscription.Status.ACTIVE) {
                        sub.setStatus(Subscription.Status.ACTIVE);
                        sub.setUpdatedAt(Instant.now());
                        subscriptionRepository.save(sub);
                    }
                    eventPublisher.publish(sub.getTenant().getId(), "PAYMENT_SUCCEEDED", sub.getId());
                    log.info("Invoice {} paid for subscription {}", stripeInvoice.getId(), sub.getId());
                }, () -> log.warn("No local subscription found for Stripe subscription {}", stripeInvoice.getSubscription()));
    }

    private void handleInvoiceFailed(com.stripe.model.Invoice stripeInvoice) {
        subscriptionRepository.findByStripeSubscriptionId(stripeInvoice.getSubscription())
                .ifPresentOrElse(sub -> {
                    upsertInvoice(sub, stripeInvoice, Invoice.Status.FAILED);
                    sub.setStatus(Subscription.Status.PAST_DUE);
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);
                    // Stripe's Smart Retries will attempt the charge again automatically;
                    // we surface PAST_DUE immediately so the dashboard reflects it in real time.
                    eventPublisher.publish(sub.getTenant().getId(), "PAYMENT_FAILED", sub.getId());
                    log.warn("Invoice {} failed for subscription {} (attempt {})",
                            stripeInvoice.getId(), sub.getId(), stripeInvoice.getAttemptCount());
                }, () -> log.warn("No local subscription found for Stripe subscription {}", stripeInvoice.getSubscription()));
    }

    private void handleSubscriptionUpdated(com.stripe.model.Subscription stripeSub) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionService.mapStripeStatus(stripeSub.getStatus()));
                    sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()));
                    sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
                    sub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);
                    eventPublisher.publish(sub.getTenant().getId(), "SUBSCRIPTION_UPDATED", sub.getId());
                });
    }

    private void handleSubscriptionDeleted(com.stripe.model.Subscription stripeSub) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresent(sub -> {
                    sub.setStatus(Subscription.Status.CANCELED);
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);
                    eventPublisher.publish(sub.getTenant().getId(), "SUBSCRIPTION_CANCELED", sub.getId());
                });
    }

    private void upsertInvoice(Subscription sub, com.stripe.model.Invoice stripeInvoice, Invoice.Status status) {
        // Idempotency guard: if we've already recorded this Stripe invoice ID,
        // update it in place instead of creating a duplicate row (handles webhook retries).
        Invoice invoice = invoiceRepository.findByStripeInvoiceId(stripeInvoice.getId())
                .orElseGet(Invoice::new);
        invoice.setSubscription(sub);
        invoice.setStripeInvoiceId(stripeInvoice.getId());
        invoice.setAmountDue(toDollars(stripeInvoice.getAmountDue()));
        invoice.setAmountPaid(toDollars(stripeInvoice.getAmountPaid()));
        invoice.setStatus(status);
        invoice.setAttemptCount(stripeInvoice.getAttemptCount() == null ? 0 : stripeInvoice.getAttemptCount().intValue());
        invoiceRepository.save(invoice);
    }

    private BigDecimal toDollars(Long cents) {
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }
}
