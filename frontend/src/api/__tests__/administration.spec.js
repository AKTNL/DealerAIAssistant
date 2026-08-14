import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  changeUserEmail,
  changeUserEnabled,
  listAuditEvents,
  listSubjectGrants,
  replaceSubjectGrants,
  resetUserPassword,
  updateOrganizationNode,
  updateRolePermissions
} from "../administration";

const requestJsonMock = vi.fn();

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

describe("administration api", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestJsonMock.mockResolvedValue({ data: [] });
  });

  it("sends optimistic versions for identity and organization mutations", async () => {
    await changeUserEmail(7, "analyst@example.com", 2);
    await changeUserEnabled(7, false, 3);
    await resetUserPassword(7, "temporary-password", 4);
    await updateRolePermissions(9, ["DATA_READ"], 5);
    await updateOrganizationNode(11, {
      displayName: "North",
      nodeType: "REGION",
      parentId: 1,
      enabled: true,
      version: 6
    });

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/admin/users/7/email", {
      method: "PATCH",
      body: JSON.stringify({ email: "analyst@example.com", version: 2 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/admin/users/7/enabled", {
      method: "PATCH",
      body: JSON.stringify({ enabled: false, version: 3 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/admin/users/7/reset-password", {
      method: "POST",
      body: JSON.stringify({ temporaryPassword: "temporary-password", version: 4 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/admin/roles/9/permissions", {
      method: "PUT",
      body: JSON.stringify({ permissions: ["DATA_READ"], version: 5 })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(5, "/api/admin/organizations/nodes/11", {
      method: "PUT",
      body: JSON.stringify({
        displayName: "North",
        nodeType: "REGION",
        parentId: 1,
        enabled: true,
        version: 6
      })
    });
  });

  it("uses readable grant and audit endpoints", async () => {
    await listSubjectGrants("user", 3);
    await listSubjectGrants("role", 4);
    await replaceSubjectGrants("role", 4, [{ organizationNodeId: 8, includeDescendants: true }]);
    await listAuditEvents();

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/admin/organizations/user-grants/3");
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/admin/organizations/role-grants/4");
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/admin/organizations/role-grants/4", {
      method: "PUT",
      body: JSON.stringify({ grants: [{ organizationNodeId: 8, includeDescendants: true }] })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/admin/audit-events");
  });
});
