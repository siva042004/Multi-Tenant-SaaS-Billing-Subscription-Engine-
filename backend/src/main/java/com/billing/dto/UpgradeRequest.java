package com.billing.dto;

import jakarta.validation.constraints.NotBlank;

public class UpgradeRequest {
    @NotBlank
    private String newPlanCode;

    // Optimistic concurrency token from the client's last known state.
    // If it doesn't match the current row, the upgrade is rejected
    // rather than silently overwriting a concurrent change.
    private Long expectedVersion;

    public String getNewPlanCode() { return newPlanCode; }
    public void setNewPlanCode(String newPlanCode) { this.newPlanCode = newPlanCode; }
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
}
