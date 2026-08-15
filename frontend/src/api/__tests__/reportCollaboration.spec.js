import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.hoisted(() => vi.fn());

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

import {
  addReportCollaborationComment,
  changeReportCollaborationAssignee,
  changeReportCollaborationStatus,
  getReportCollaboration,
  listReportCollaborationAssignees,
  listReportCollaborations
} from "../reportCollaboration";

describe("report collaboration API", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestJsonMock.mockResolvedValue({ data: [] });
  });

  it("uses the shared tenant-aware client for filtered reads and versioned mutations", async () => {
    await listReportCollaborations({
      status: "IN_PROGRESS",
      assigneeUserId: 7,
      organizationId: 11,
      generatedFrom: "2026-08-01T00:00:00.000Z",
      generatedTo: "2026-08-15T23:59:59.999Z"
    });
    await getReportCollaboration("report / 1");
    await listReportCollaborationAssignees("report / 1");
    await changeReportCollaborationStatus("report-1", "RESOLVED", 3);
    await changeReportCollaborationAssignee("report-1", 8, 4);
    await addReportCollaborationComment("report-1", "Reviewed", 5);

    expect(requestJsonMock).toHaveBeenNthCalledWith(1,
      "/api/report-collaborations?status=IN_PROGRESS&assigneeUserId=7&organizationId=11&generatedFrom=2026-08-01T00%3A00%3A00.000Z&generatedTo=2026-08-15T23%3A59%3A59.999Z");
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/report-collaborations/report%20%2F%201");
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/report-collaborations/report%20%2F%201/assignees");
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/report-collaborations/report-1/status", {
      method: "PATCH",
      body: JSON.stringify({ status: "RESOLVED", version: 3 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, "/api/report-collaborations/report-1/assignee", {
      method: "PATCH",
      body: JSON.stringify({ assigneeUserId: 8, version: 4 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(6, "/api/report-collaborations/report-1/comments", {
      method: "POST",
      body: JSON.stringify({ body: "Reviewed", version: 5 })
    });
  });
});
