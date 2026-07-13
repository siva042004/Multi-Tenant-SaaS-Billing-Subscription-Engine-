package com.billing.dto;

import com.billing.entity.Invoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class InvoiceDto {
    private UUID id;
    private String stripeInvoiceId;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private String status;
    private Integer attemptCount;
    private Instant createdAt;

    public static InvoiceDto from(Invoice i) {
        InvoiceDto dto = new InvoiceDto();
        dto.id = i.getId();
        dto.stripeInvoiceId = i.getStripeInvoiceId();
        dto.amountDue = i.getAmountDue();
        dto.amountPaid = i.getAmountPaid();
        dto.status = i.getStatus().name();
        dto.attemptCount = i.getAttemptCount();
        dto.createdAt = i.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public BigDecimal getAmountDue() { return amountDue; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public String getStatus() { return status; }
    public Integer getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
}
