package com.billing.controller;

import com.billing.dto.CreateTenantRequest;
import com.billing.dto.SubscriptionDto;
import com.billing.service.TenantOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantOnboardingService onboardingService;

    public TenantController(TenantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        SubscriptionDto result = onboardingService.onboardTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
