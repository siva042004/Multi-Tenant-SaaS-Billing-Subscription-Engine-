import React from "react";

const STATUS_STYLES = {
  ACTIVE: { label: "Active", color: "#1a7f37", bg: "#dafbe1" },
  PAST_DUE: { label: "Payment Failed", color: "#9a1c1c", bg: "#ffebe9" },
  CANCELED: { label: "Canceled", color: "#57606a", bg: "#eaeef2" },
  INCOMPLETE: { label: "Incomplete", color: "#9a6700", bg: "#fff8c5" },
  TRIALING: { label: "Trialing", color: "#0969da", bg: "#ddf4ff" },
};

export default function PaymentStatus({ status, cancelAtPeriodEnd }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.INCOMPLETE;

  return (
    <span
      data-testid="payment-status-badge"
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "4px 10px",
        borderRadius: 999,
        fontSize: 13,
        fontWeight: 600,
        color: style.color,
        backgroundColor: style.bg,
      }}
    >
      {style.label}
      {cancelAtPeriodEnd && status !== "CANCELED" && (
        <span style={{ fontWeight: 400 }}>(cancels at period end)</span>
      )}
    </span>
  );
}
