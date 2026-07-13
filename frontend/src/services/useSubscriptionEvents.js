import { useEffect, useRef, useState } from "react";
import { billingApi } from "./billingApi";

/**
 * Subscribes to server-sent events for a tenant and returns the most recent
 * event plus a monotonically increasing counter, so consumers can trigger
 * a refetch whenever anything relevant happens (payment succeeded/failed,
 * subscription updated/canceled) without polling.
 */
export function useSubscriptionEvents(tenantId) {
  const [lastEvent, setLastEvent] = useState(null);
  const [connectionState, setConnectionState] = useState("connecting");
  const eventSourceRef = useRef(null);

  useEffect(() => {
    if (!tenantId) return undefined;

    const es = new EventSource(billingApi.eventsUrl(tenantId));
    eventSourceRef.current = es;

    es.addEventListener("CONNECTED", () => setConnectionState("connected"));

    const relevantEvents = [
      "PAYMENT_SUCCEEDED",
      "PAYMENT_FAILED",
      "SUBSCRIPTION_UPDATED",
      "SUBSCRIPTION_CANCELED",
    ];

    relevantEvents.forEach((type) => {
      es.addEventListener(type, (e) => {
        const data = JSON.parse(e.data);
        setLastEvent({ type, ...data, receivedAt: Date.now() });
      });
    });

    es.onerror = () => {
      // EventSource auto-reconnects; we just reflect the transient state.
      setConnectionState("reconnecting");
    };

    return () => {
      es.close();
      eventSourceRef.current = null;
    };
  }, [tenantId]);

  return { lastEvent, connectionState };
}
