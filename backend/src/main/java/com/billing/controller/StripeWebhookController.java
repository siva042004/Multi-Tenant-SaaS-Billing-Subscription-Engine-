package com.billing.controller;

import com.billing.config.StripeConfig;
import com.billing.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeConfig stripeConfig;
    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeConfig stripeConfig, StripeWebhookService webhookService) {
        this.stripeConfig = stripeConfig;
        this.webhookService = webhookService;
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
                                                 @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            // Signature verification is mandatory: without it, anyone could POST
            // fabricated "payment succeeded" events to this endpoint.
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            webhookService.handle(event);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Error processing Stripe event {}", event.getId(), e);
            // Return 500 so Stripe retries with backoff instead of silently dropping the event.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing error");
        }
    }
}
