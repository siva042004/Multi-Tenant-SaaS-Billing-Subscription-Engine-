import axios from "axios";

const API_BASE = process.env.REACT_APP_API_BASE_URL || "http://localhost:8081/api";

const client = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
});

export const billingApi = {
  listSubscriptions: (tenantId) =>
    client.get(`/tenants/${tenantId}/subscriptions`).then((r) => r.data),

  getSubscription: (subscriptionId) =>
    client.get(`/subscriptions/${subscriptionId}`).then((r) => r.data),

  // expectedVersion enables optimistic-concurrency detection: if another
  // operation (e.g. a webhook-driven status change) touched the subscription
  // since it was last loaded, the backend returns 409 instead of clobbering it.
  upgradeSubscription: (subscriptionId, newPlanCode, expectedVersion) =>
    client
      .put(`/subscriptions/${subscriptionId}/upgrade`, { newPlanCode, expectedVersion })
      .then((r) => r.data),

  cancelSubscription: (subscriptionId, immediately = false) =>
    client
      .post(`/subscriptions/${subscriptionId}/cancel`, null, { params: { immediately } })
      .then((r) => r.data),

  listInvoices: (subscriptionId) =>
    client.get(`/subscriptions/${subscriptionId}/invoices`).then((r) => r.data),

  createTenant: (payload) => client.post(`/tenants`, payload).then((r) => r.data),

  eventsUrl: (tenantId) => `${API_BASE}/tenants/${tenantId}/events`,
};

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export function extractErrorMessage(error) {
  if (error.response?.data?.error) return error.response.data.error;
  if (error.response?.status === 409)
    return "This subscription changed elsewhere. Refresh to see the latest state.";
  return error.message || "Something went wrong.";
}
