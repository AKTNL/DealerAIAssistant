import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.hoisted(() => vi.fn());

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

import {
  addModelPrice,
  getModelBudget,
  getModelUsageSummary,
  getPlatformModelUsageSummary,
  listModelPrices,
  listModelUsageEvents,
  saveModelBudget
} from "../modelUsage";

describe("model usage API", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestJsonMock.mockResolvedValue({ data: null });
  });

  it("uses bounded range reads and tenant-scoped governance paths", async () => {
    const range = { from: "2026-08-01T00:00:00.000Z", to: "2026-08-16T00:00:00.000Z" };
    const price = { provider: "openai-compatible", model: "gpt-test", currency: "USD" };
    const budget = { monthlyLimit: 100, currency: "USD", version: 2 };

    await getModelUsageSummary(range);
    await listModelUsageEvents(range);
    await listModelPrices();
    await addModelPrice(price);
    await getModelBudget();
    await saveModelBudget(budget);
    await getPlatformModelUsageSummary(range);

    const query = "from=2026-08-01T00%3A00%3A00.000Z&to=2026-08-16T00%3A00%3A00.000Z";
    expect(requestJsonMock).toHaveBeenNthCalledWith(1, `/api/admin/model-usage/summary?${query}`);
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, `/api/admin/model-usage/events?${query}`);
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/admin/model-usage/prices");
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/admin/model-usage/prices", {
      method: "POST",
      body: JSON.stringify(price)
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, "/api/admin/model-usage/budget");
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, "/api/admin/model-usage/budget", {
      method: "PUT",
      body: JSON.stringify(budget)
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(7, `/api/platform/model-usage/summary?${query}`);
  });
});
