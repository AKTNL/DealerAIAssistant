import { requestJson } from "./client";

export async function listReportSubscriptions() {
  return readData(await requestJson("/api/report-subscriptions"), []);
}

export async function listReportSubscriptionRecipients() {
  return readData(await requestJson("/api/report-subscriptions/recipients"), []);
}

export async function createReportSubscription(input) {
  return readData(await requestJson("/api/report-subscriptions", {
    method: "POST",
    body: JSON.stringify(input)
  }), null);
}

export async function updateReportSubscription(subscriptionId, input) {
  return readData(await requestJson(`/api/report-subscriptions/${subscriptionId}`, {
    method: "PUT",
    body: JSON.stringify(input)
  }), null);
}

export async function changeReportSubscriptionEnabled(subscriptionId, enabled, version) {
  return readData(await requestJson(`/api/report-subscriptions/${subscriptionId}/enabled`, {
    method: "PATCH",
    body: JSON.stringify({ enabled, version })
  }), null);
}

export async function deleteReportSubscription(subscriptionId, version) {
  await requestJson(`/api/report-subscriptions/${subscriptionId}`, {
    method: "DELETE",
    body: JSON.stringify({ version })
  });
}

function readData(response, fallback) {
  return response?.data ?? fallback;
}
