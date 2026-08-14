import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.hoisted(() => vi.fn());

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

import {
  changeReportSubscriptionEnabled,
  createReportSubscription,
  deleteReportSubscription,
  listReportSubscriptionRecipients,
  listReportSubscriptions,
  updateReportSubscription
} from "../reportSubscriptions";

describe("report subscription API", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestJsonMock.mockResolvedValue({ data: [] });
  });

  it("uses the tenant-aware shared client for reads and mutations", async () => {
    const input = { reportType: "daily", scheduleKind: "DAILY" };

    await listReportSubscriptions();
    await listReportSubscriptionRecipients();
    await createReportSubscription(input);
    await updateReportSubscription(9, { ...input, version: 2 });
    await changeReportSubscriptionEnabled(9, false, 3);
    await deleteReportSubscription(9, 4);

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/report-subscriptions");
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/report-subscriptions/recipients");
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/report-subscriptions", {
      method: "POST",
      body: JSON.stringify(input)
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/report-subscriptions/9", {
      method: "PUT",
      body: JSON.stringify({ ...input, version: 2 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, "/api/report-subscriptions/9/enabled", {
      method: "PATCH",
      body: JSON.stringify({ enabled: false, version: 3 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, "/api/report-subscriptions/9", {
      method: "DELETE",
      body: JSON.stringify({ version: 4 })
    });
  });
});
