package com.billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code; // e.g. "BASIC", "PRO", "ENTERPRISE"

    @Column(nullable = false)
    private String stripePriceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal monthlyPrice;

    @Column(nullable = false)
    private Integer seatLimit;

    public Plan() {}

    public Plan(String code, String stripePriceId, String name, BigDecimal monthlyPrice, Integer seatLimit) {
        this.code = code;
        this.stripePriceId = stripePriceId;
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.seatLimit = seatLimit;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStripePriceId() { return stripePriceId; }
    public void setStripePriceId(String stripePriceId) { this.stripePriceId = stripePriceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public Integer getSeatLimit() { return seatLimit; }
    public void setSeatLimit(Integer seatLimit) { this.seatLimit = seatLimit; }
}
