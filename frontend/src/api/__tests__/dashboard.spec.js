import { beforeEach, describe, expect, it, vi } from "vitest";
import { getDashboardSummary } from "../dashboard";

const requestJsonMock = vi.fn();
const getAuthTokenMock = vi.fn();

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

vi.mock("../sessionToken", () => ({
  getAuthToken: () => getAuthTokenMock()
}));

describe("dashboard api", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    getAuthTokenMock.mockReset();
  });

  it("loads the dashboard summary with the bearer token", async () => {
    getAuthTokenMock.mockReturnValue("signed-token");
    requestJsonMock.mockResolvedValue({
      data: {
        dataStatus: {
          source: "configured-workbook",
          fallbackActive: false,
          simulatedData: true,
          lowConfidence: true,
          issueSummaries: "bad-shape"
        },
        overview: { dealerCount: 3 }
      }
    });

    await expect(getDashboardSummary()).resolves.toMatchObject({
      dataStatus: {
        source: "configured-workbook",
        fallbackActive: false,
        simulatedData: true,
        lowConfidence: true,
        issueSummaries: []
      },
      overview: { dealerCount: 3 },
      targetAchievement: {}
    });
    expect(requestJsonMock).toHaveBeenCalledWith("/api/dashboard", {
      headers: { Authorization: "Bearer signed-token" }
    });
  });

  it("returns null for malformed responses", async () => {
    requestJsonMock.mockResolvedValue({});

    await expect(getDashboardSummary()).resolves.toBeNull();
  });
});
