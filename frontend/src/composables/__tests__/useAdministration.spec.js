import { beforeEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import { useAdministration } from "../useAdministration";

const api = vi.hoisted(() => ({
  assignUserRoles: vi.fn(),
  changeUserEnabled: vi.fn(),
  createOrganizationNode: vi.fn(),
  createRole: vi.fn(),
  createUser: vi.fn(),
  listAuditEvents: vi.fn(),
  listDealerMappings: vi.fn(),
  listOrganizationNodes: vi.fn(),
  listRoles: vi.fn(),
  listSubjectGrants: vi.fn(),
  listUserSessions: vi.fn(),
  listUsers: vi.fn(),
  mapDealer: vi.fn(),
  replaceSubjectGrants: vi.fn(),
  resetUserPassword: vi.fn(),
  revokeUserSessions: vi.fn(),
  updateOrganizationNode: vi.fn(),
  updateRolePermissions: vi.fn()
}));

vi.mock("../../api/administration", () => api);
vi.mock("../../utils/temporaryPassword", () => ({
  createTemporaryPassword: () => "Generated-Password-2!"
}));

const dictionary = ref({
  adminConflictError: "Refresh after conflict",
  adminForbiddenError: "Forbidden",
  adminRequestError: "Request failed",
  adminUserCreated: "Created",
  adminValidationError: "Invalid"
});

describe("useAdministration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listUsers.mockResolvedValue([]);
    api.listRoles.mockResolvedValue([]);
    api.listOrganizationNodes.mockResolvedValue([]);
    api.listDealerMappings.mockResolvedValue([]);
    api.listAuditEvents.mockResolvedValue([]);
  });

  it("loads only resource groups allowed by current permissions", async () => {
    const administration = useAdministration({
      currentUser: ref({ id: 1, permissions: ["USER_READ"] }),
      dictionary
    });

    await administration.initialize();

    expect(api.listUsers).toHaveBeenCalledOnce();
    expect(api.listAuditEvents).toHaveBeenCalledOnce();
    expect(api.listRoles).not.toHaveBeenCalled();
    expect(api.listOrganizationNodes).not.toHaveBeenCalled();
  });

  it("keeps a generated password only in the one-time in-memory response", async () => {
    api.createUser.mockResolvedValue({ id: 3, username: "viewer", displayName: "Viewer", roles: ["VIEWER"] });
    const administration = useAdministration({
      currentUser: ref({ id: 1, permissions: ["USER_READ", "USER_MANAGE"] }),
      dictionary
    });

    await administration.createUser({ username: "viewer", displayName: "Viewer", roles: ["VIEWER"] });

    expect(api.createUser).toHaveBeenCalledWith({
      username: "viewer",
      displayName: "Viewer",
      roles: ["VIEWER"],
      temporaryPassword: "Generated-Password-2!"
    });
    expect(administration.oneTimePassword.value).toEqual({
      label: "Viewer",
      password: "Generated-Password-2!"
    });

    administration.dismissOneTimePassword();
    expect(administration.oneTimePassword.value).toBeNull();
  });

  it("classifies optimistic conflicts without exposing server detail", async () => {
    api.changeUserEnabled.mockRejectedValue({ status: 409, message: "database version was 9" });
    const administration = useAdministration({
      currentUser: ref({ id: 1, permissions: ["USER_MANAGE"] }),
      dictionary
    });

    await administration.changeUserEnabled({ id: 2, version: 3 }, false);

    expect(administration.operationError.value).toEqual({
      status: 409,
      message: "Refresh after conflict",
      detail: ""
    });
  });
});
