package com.billing.dto;

import com.billing.entity.Subscription;
import java.time.Instant;
import java.util.UUID;

public class SubscriptionDto {
    private UUID id;
    private UUID tenantId;
    private String tenantName;
    private String planCode;
    private String planName;
    private String status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private long version;

    public static SubscriptionDto from(Subscription s) {
        SubscriptionDto dto = new SubscriptionDto();
        dto.id = s.getId();
        dto.tenantId = s.getTenant().getId();
        dto.tenantName = s.getTenant().getName();
        dto.planCode = s.getPlan().getCode();
        dto.planName = s.getPlan().getName();
        dto.status = s.getStatus().name();
        dto.currentPeriodStart = s.getCurrentPeriodStart();
        dto.currentPeriodEnd = s.getCurrentPeriodEnd();
        dto.cancelAtPeriodEnd = Boolean.TRUE.equals(s.getCancelAtPeriodEnd());
        dto.version = s.getVersion() == null ? 0 : s.getVersion();
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public String getPlanCode() { return planCode; }
    public String getPlanName() { return planName; }
    public String getStatus() { return status; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public long getVersion() { return version; }
}
