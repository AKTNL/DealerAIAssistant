import { beforeEach, describe, expect, it, vi } from "vitest";
import { getDataStatus } from "../dataStatus";

const requestJsonMock = vi.fn();
const getAuthTokenMock = vi.fn();

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

vi.mock("../sessionToken", () => ({
  getAuthToken: () => getAuthTokenMock()
}));

describe("dataStatus", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    getAuthTokenMock.mockReset();
  });

  it("loads normalized data status with the bearer token", async () => {
    getAuthTokenMock.mockReturnValue("signed-token");
    requestJsonMock.mockResolvedValue({
      data: { fallbackActive: true, source: "built-in-sample" }
    });

    await expect(getDataStatus()).resolves.toEqual({
      fallbackActive: true,
      source: "built-in-sample"
    });
    expect(requestJsonMock).toHaveBeenCalledWith("/api/data-status", {
      headers: { Authorization: "Bearer signed-token" }
    });
  });

  it("uses safe defaults for malformed responses", async () => {
    requestJsonMock.mockResolvedValue({});

    await expect(getDataStatus()).resolves.toEqual({
      fallbackActive: false,
      source: "pending"
    });
  });
});
