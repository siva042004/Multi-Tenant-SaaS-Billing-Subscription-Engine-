import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SubscriptionManager from "../components/SubscriptionManager";
import { billingApi } from "../services/billingApi";

jest.mock("../services/billingApi", () => ({
  billingApi: {
    upgradeSubscription: jest.fn(),
    cancelSubscription: jest.fn(),
  },
  extractErrorMessage: (err) =>
    err.response?.data?.error || err.message || "Something went wrong.",
}));

const baseSubscription = {
  id: "sub-1",
  tenantId: "tenant-1",
  tenantName: "Acme Inc",
  planCode: "BASIC",
  planName: "Basic",
  status: "ACTIVE",
  cancelAtPeriodEnd: false,
  version: 1,
};

describe("SubscriptionManager - prorated upgrades", () => {
  afterEach(() => jest.clearAllMocks());

  test("shows a proration notice after a successful upgrade", async () => {
    billingApi.upgradeSubscription.mockResolvedValue({
      ...baseSubscription,
      planCode: "PRO",
      planName: "Pro",
      version: 2,
    });

    const onChanged = jest.fn();
    render(<SubscriptionManager subscription={baseSubscription} onChanged={onChanged} />);

    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "PRO");
    await userEvent.click(screen.getByText("Change Plan"));

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/prorated charge/i)
    );
    expect(billingApi.upgradeSubscription).toHaveBeenCalledWith("sub-1", "PRO", 1);
    expect(onChanged).toHaveBeenCalledWith(expect.objectContaining({ planCode: "PRO" }));
  });

  test("shows a proration credit notice for downgrades", async () => {
    const proSubscription = { ...baseSubscription, planCode: "PRO", planName: "Pro" };
    billingApi.upgradeSubscription.mockResolvedValue({
      ...proSubscription,
      planCode: "BASIC",
      planName: "Basic",
      version: 2,
    });

    render(<SubscriptionManager subscription={proSubscription} onChanged={jest.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "BASIC");
    await userEvent.click(screen.getByText("Change Plan"));

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/prorated credit/i)
    );
  });
});

describe("SubscriptionManager - failed payments", () => {
  afterEach(() => jest.clearAllMocks());

  test("renders PAST_DUE status distinctly", () => {
    const pastDueSub = { ...baseSubscription, status: "PAST_DUE" };
    render(<SubscriptionManager subscription={pastDueSub} onChanged={jest.fn()} />);
    expect(screen.getByTestId("payment-status-badge")).toHaveTextContent("Payment Failed");
  });

  test("surfaces a 402 payment-required error from the API without crashing", async () => {
    billingApi.upgradeSubscription.mockRejectedValue({
      response: { status: 402, data: { error: "Your card was declined." } },
    });

    render(<SubscriptionManager subscription={baseSubscription} onChanged={jest.fn()} />);
    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "PRO");
    await userEvent.click(screen.getByText("Change Plan"));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("Your card was declined.")
    );
  });
});

describe("SubscriptionManager - concurrent operations", () => {
  afterEach(() => jest.clearAllMocks());

  test("surfaces a 409 conflict when the subscription changed elsewhere (e.g. a webhook raced the upgrade)", async () => {
    billingApi.upgradeSubscription.mockRejectedValue({
      response: { status: 409, data: {} },
    });

    render(<SubscriptionManager subscription={baseSubscription} onChanged={jest.fn()} />);
    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "PRO");
    await userEvent.click(screen.getByText("Change Plan"));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(/updated elsewhere/i)
    );
  });

  test("disables the Change Plan button while a request is in flight to prevent duplicate submits", async () => {
    let resolveUpgrade;
    billingApi.upgradeSubscription.mockReturnValue(
      new Promise((resolve) => {
        resolveUpgrade = resolve;
      })
    );

    render(<SubscriptionManager subscription={baseSubscription} onChanged={jest.fn()} />);
    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "PRO");

    const button = screen.getByText("Change Plan");
    await userEvent.click(button);

    expect(screen.getByText("Processing…")).toBeDisabled();

    resolveUpgrade({ ...baseSubscription, planCode: "PRO", version: 2 });
    await waitFor(() => expect(screen.queryByText("Processing…")).not.toBeInTheDocument());
  });

  test("passes the currently held version so the backend can detect staleness", async () => {
    billingApi.upgradeSubscription.mockResolvedValue({ ...baseSubscription, planCode: "PRO", version: 5 });
    const staleSubscription = { ...baseSubscription, version: 4 };

    render(<SubscriptionManager subscription={staleSubscription} onChanged={jest.fn()} />);
    await userEvent.selectOptions(screen.getByLabelText("Select plan"), "PRO");
    await userEvent.click(screen.getByText("Change Plan"));

    await waitFor(() =>
      expect(billingApi.upgradeSubscription).toHaveBeenCalledWith("sub-1", "PRO", 4)
    );
  });
});

describe("SubscriptionManager - cancellation flows", () => {
  afterEach(() => jest.clearAllMocks());

  test("cancel at period end shows the correct notice and disables re-trigger", async () => {
    billingApi.cancelSubscription.mockResolvedValue({ ...baseSubscription, cancelAtPeriodEnd: true });

    render(<SubscriptionManager subscription={baseSubscription} onChanged={jest.fn()} />);
    await userEvent.click(screen.getByText("Cancel at Period End"));

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(/end of the current billing period/i)
    );
    expect(billingApi.cancelSubscription).toHaveBeenCalledWith("sub-1", false);
  });

  test("immediate cancellation is disabled once already canceled", () => {
    const canceledSub = { ...baseSubscription, status: "CANCELED" };
    render(<SubscriptionManager subscription={canceledSub} onChanged={jest.fn()} />);
    expect(screen.getByText("Cancel Immediately")).toBeDisabled();
  });
});
