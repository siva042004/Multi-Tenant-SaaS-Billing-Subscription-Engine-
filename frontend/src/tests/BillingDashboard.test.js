import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import BillingDashboard from "../components/BillingDashboard";
import { billingApi } from "../services/billingApi";

jest.mock("../services/billingApi", () => ({
  billingApi: {
    listSubscriptions: jest.fn(),
    eventsUrl: jest.fn(() => "http://localhost/api/tenants/t1/events"),
  },
  extractErrorMessage: (err) => err.message || "Something went wrong.",
}));

// Minimal EventSource mock so BillingDashboard's SSE hook doesn't blow up in jsdom.
class MockEventSource {
  constructor(url) {
    this.url = url;
    MockEventSource.instances.push(this);
    this.listeners = {};
  }
  addEventListener(type, cb) {
    this.listeners[type] = cb;
  }
  close() {}
  emit(type, data) {
    this.listeners[type]?.({ data: JSON.stringify(data) });
  }
}
MockEventSource.instances = [];
global.EventSource = MockEventSource;

const sampleSubscription = {
  id: "sub-1",
  tenantId: "tenant-1",
  tenantName: "Acme Inc",
  planCode: "BASIC",
  planName: "Basic",
  status: "ACTIVE",
  cancelAtPeriodEnd: false,
  version: 1,
};

describe("BillingDashboard", () => {
  beforeEach(() => {
    MockEventSource.instances = [];
    jest.clearAllMocks();
  });

  test("loads and renders subscriptions for the tenant", async () => {
    billingApi.listSubscriptions.mockResolvedValue([sampleSubscription]);

    render(<BillingDashboard tenantId="tenant-1" />);

    await waitFor(() => expect(screen.getByTestId("billing-dashboard")).toBeInTheDocument());
    expect(screen.getByText("Acme Inc")).toBeInTheDocument();
    expect(billingApi.listSubscriptions).toHaveBeenCalledWith("tenant-1");
  });

  test("shows an error state when the initial load fails", async () => {
    billingApi.listSubscriptions.mockRejectedValue(new Error("Network down"));

    render(<BillingDashboard tenantId="tenant-1" />);

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Network down"));
  });

  test("refetches subscriptions in real time when a PAYMENT_FAILED event arrives", async () => {
    billingApi.listSubscriptions
      .mockResolvedValueOnce([sampleSubscription])
      .mockResolvedValueOnce([{ ...sampleSubscription, status: "PAST_DUE" }]);

    render(<BillingDashboard tenantId="tenant-1" />);
    await waitFor(() => expect(screen.getByTestId("billing-dashboard")).toBeInTheDocument());

    const es = MockEventSource.instances[0];
    es.emit("PAYMENT_FAILED", { subscriptionId: "sub-1", type: "PAYMENT_FAILED" });

    await waitFor(() => expect(billingApi.listSubscriptions).toHaveBeenCalledTimes(2));
  });

  test("shows empty state when tenant has no subscriptions", async () => {
    billingApi.listSubscriptions.mockResolvedValue([]);
    render(<BillingDashboard tenantId="tenant-1" />);
    await waitFor(() =>
      expect(screen.getByText("No subscriptions found for this tenant.")).toBeInTheDocument()
    );
  });
});
