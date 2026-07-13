import React, { useState } from "react";
import { billingApi, extractErrorMessage } from "../services/billingApi";
import PaymentStatus from "./PaymentStatus";

const PLAN_OPTIONS = [
  { code: "BASIC", name: "Basic", price: "$29/mo" },
  { code: "PRO", name: "Pro", price: "$99/mo" },
  { code: "ENTERPRISE", name: "Enterprise", price: "$299/mo" },
];

export default function SubscriptionManager({ subscription, onChanged }) {
  const [selectedPlan, setSelectedPlan] = useState(subscription.planCode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const isDowngrade = (fromCode, toCode) => {
    const order = ["BASIC", "PRO", "ENTERPRISE"];
    return order.indexOf(toCode) < order.indexOf(fromCode);
  };

  async function handlePlanChange() {
    if (selectedPlan === subscription.planCode) return;
    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await billingApi.upgradeSubscription(
        subscription.id,
        selectedPlan,
        subscription.version
      );
      const downgraded = isDowngrade(subscription.planCode, selectedPlan);
      setNotice(
        downgraded
          ? "Plan changed. A prorated credit will appear on your next invoice."
          : "Plan upgraded. A prorated charge has been applied for the remainder of this period."
      );
      onChanged(updated);
    } catch (err) {
      if (err.response?.status === 409) {
        setError(
          "This subscription was updated elsewhere (e.g. a payment event just came in). Please review the latest state before retrying."
        );
      } else if (err.response?.status === 402) {
        setError(extractErrorMessage(err));
      } else {
        setError(extractErrorMessage(err));
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleCancel(immediately) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await billingApi.cancelSubscription(subscription.id, immediately);
      setNotice(
        immediately
          ? "Subscription canceled immediately."
          : "Subscription will cancel at the end of the current billing period."
      );
      onChanged(updated);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      data-testid="subscription-manager"
      style={{ border: "1px solid #d0d7de", borderRadius: 8, padding: 16, marginBottom: 12 }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <strong>{subscription.tenantName}</strong>
          <div style={{ fontSize: 13, color: "#57606a" }}>
            Current plan: {subscription.planName}
          </div>
        </div>
        <PaymentStatus status={subscription.status} cancelAtPeriodEnd={subscription.cancelAtPeriodEnd} />
      </div>

      <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "center" }}>
        <select
          value={selectedPlan}
          onChange={(e) => setSelectedPlan(e.target.value)}
          disabled={busy || subscription.status === "CANCELED"}
          aria-label="Select plan"
        >
          {PLAN_OPTIONS.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name} ({p.price})
            </option>
          ))}
        </select>
        <button
          onClick={handlePlanChange}
          disabled={busy || selectedPlan === subscription.planCode || subscription.status === "CANCELED"}
        >
          {busy ? "Processing…" : "Change Plan"}
        </button>
        <button
          onClick={() => handleCancel(false)}
          disabled={busy || subscription.status === "CANCELED" || subscription.cancelAtPeriodEnd}
        >
          Cancel at Period End
        </button>
        <button
          onClick={() => handleCancel(true)}
          disabled={busy || subscription.status === "CANCELED"}
          style={{ color: "#9a1c1c" }}
        >
          Cancel Immediately
        </button>
      </div>

      {notice && (
        <div role="status" style={{ marginTop: 10, color: "#1a7f37", fontSize: 13 }}>
          {notice}
        </div>
      )}
      {error && (
        <div role="alert" style={{ marginTop: 10, color: "#9a1c1c", fontSize: 13 }}>
          {error}
        </div>
      )}
    </div>
  );
}
