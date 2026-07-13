package com.billing.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.billing.dto.InvoiceDto;
import com.billing.dto.SubscriptionDto;
import com.billing.dto.UpgradeRequest;
import com.billing.repository.InvoiceRepository;
import com.billing.service.SubscriptionEventPublisher;
import com.billing.service.SubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionEventPublisher eventPublisher;
    private final InvoiceRepository invoiceRepository;

    public SubscriptionController(SubscriptionService subscriptionService,
                                   SubscriptionEventPublisher eventPublisher,
                                   InvoiceRepository invoiceRepository) {
        this.subscriptionService = subscriptionService;
        this.eventPublisher = eventPublisher;
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionDto> listAll() {
        return subscriptionService.listAll();
    }

    @GetMapping("/tenants/{tenantId}/subscriptions")
    public List<SubscriptionDto> listForTenant(@PathVariable UUID tenantId) {
        return subscriptionService.listForTenant(tenantId);
    }

    @GetMapping("/subscriptions/{id}")
    public SubscriptionDto get(@PathVariable UUID id) {
        return subscriptionService.get(id);
    }

    @PutMapping("/subscriptions/{id}/upgrade")
    public ResponseEntity<SubscriptionDto> upgrade(@PathVariable UUID id, @Valid @RequestBody UpgradeRequest request) {
        return ResponseEntity.ok(subscriptionService.upgrade(id, request));
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<SubscriptionDto> cancel(@PathVariable UUID id,
                                                    @RequestParam(defaultValue = "false") boolean immediately) {
        return ResponseEntity.ok(subscriptionService.cancel(id, immediately));
    }

    @GetMapping("/subscriptions/{id}/invoices")
    public List<InvoiceDto> invoices(@PathVariable UUID id) {
        return invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(id).stream()
                .map(InvoiceDto::from)
                .toList();
    }

    // Real-time payment/subscription updates for the dashboard, per tenant.
    @GetMapping(value = "/tenants/{tenantId}/events", produces = "text/event-stream")
    public SseEmitter streamEvents(@PathVariable UUID tenantId) {
        return eventPublisher.subscribe(tenantId);
    }
}
