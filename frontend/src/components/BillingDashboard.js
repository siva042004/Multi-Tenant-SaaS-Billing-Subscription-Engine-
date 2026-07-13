import React, { useCallback, useEffect, useState } from "react";
import { billingApi, extractErrorMessage } from "../services/billingApi";
import { useSubscriptionEvents } from "../services/useSubscriptionEvents";
import SubscriptionManager from "./SubscriptionManager";

export default function BillingDashboard({ tenantId }) {
  const [subscriptions, setSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const { lastEvent, connectionState } = useSubscriptionEvents(tenantId);

  const loadSubscriptions = useCallback(async () => {
    try {
      const data = await billingApi.listSubscriptions(tenantId);
      setSubscriptions(data);
      setError(null);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    loadSubscriptions();
  }, [loadSubscriptions]);

  // Real-time refresh: whenever Stripe pushes a payment/subscription event
  // for this tenant, silently refetch so the dashboard reflects it without
  // the user needing to reload the page.
  useEffect(() => {
    if (lastEvent) {
      loadSubscriptions();
    }
  }, [lastEvent, loadSubscriptions]);

  function handleSubscriptionChanged(updated) {
    setSubscriptions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
  }

  if (loading) return <div>Loading billing dashboard…</div>;
  if (error) return <div role="alert">{error}</div>;

  return (
    <div data-testid="billing-dashboard" style={{ maxWidth: 720, margin: "0 auto", padding: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16 }}>
        <h2>Billing &amp; Subscriptions</h2>
        <span style={{ fontSize: 12, color: connectionState === "connected" ? "#1a7f37" : "#9a6700" }}>
          {connectionState === "connected" ? "● Live" : "○ Reconnecting…"}
        </span>
      </div>

      {subscriptions.length === 0 && <div>No subscriptions found for this tenant.</div>}

      {subscriptions.map((sub) => (
        <SubscriptionManager key={sub.id} subscription={sub} onChanged={handleSubscriptionChanged} />
      ))}
    </div>
  );
}
