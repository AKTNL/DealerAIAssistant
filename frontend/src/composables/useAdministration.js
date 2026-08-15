import { computed, ref } from "vue";
import {
  assignUserRoles as assignUserRolesRequest,
  changeUserEmail as changeUserEmailRequest,
  changeUserEnabled as changeUserEnabledRequest,
  createOrganizationNode as createOrganizationNodeRequest,
  createRole as createRoleRequest,
  createUser as createUserRequest,
  listAuditEvents,
  listDealerMappings,
  listOrganizationNodes,
  listRoles,
  listSubjectGrants,
  listUserSessions,
  listUsers,
  mapDealer as mapDealerRequest,
  replaceSubjectGrants as replaceSubjectGrantsRequest,
  resetUserPassword as resetUserPasswordRequest,
  revokeUserSessions as revokeUserSessionsRequest,
  updateOrganizationNode as updateOrganizationNodeRequest,
  updateRolePermissions as updateRolePermissionsRequest
} from "../api/administration";
import { createTemporaryPassword } from "../utils/temporaryPassword";

export function useAdministration({ currentUser, dictionary, onAuthExpired, onIdentityRevoked }) {
  const users = ref([]);
  const roles = ref([]);
  const organizationNodes = ref([]);
  const dealerMappings = ref([]);
  const auditEvents = ref([]);
  const userSessions = ref([]);
  const subjectGrants = ref([]);
  const loading = ref(false);
  const loadError = ref(null);
  const operationError = ref(null);
  const successMessage = ref("");
  const pendingAction = ref("");
  const oneTimePassword = ref(null);

  const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
  const canReadUsers = computed(() => permissions.value.has("USER_READ"));
  const canManageUsers = computed(() => permissions.value.has("USER_MANAGE"));
  const canReadRoles = computed(() => permissions.value.has("ROLE_READ"));
  const canManageRoles = computed(() => permissions.value.has("ROLE_MANAGE"));
  const canReadOrganization = computed(() => permissions.value.has("ORGANIZATION_READ"));
  const canManageOrganization = computed(() => permissions.value.has("ORGANIZATION_MANAGE"));
  const canManageGrants = computed(() => permissions.value.has("ORGANIZATION_GRANT_MANAGE"));
  const canReadModelUsage = computed(() => permissions.value.has("MODEL_USAGE_READ"));

  async function initialize() {
    loading.value = true;
    loadError.value = null;
    try {
      const tasks = [];
      if (canReadUsers.value) {
        tasks.push(listUsers().then((data) => { users.value = normalizeList(data); }));
        tasks.push(listAuditEvents().then((data) => { auditEvents.value = normalizeList(data); }));
      }
      if (canReadRoles.value) {
        tasks.push(listRoles().then((data) => { roles.value = normalizeList(data); }));
      }
      if (canReadOrganization.value) {
        tasks.push(listOrganizationNodes().then((data) => { organizationNodes.value = normalizeList(data); }));
        tasks.push(listDealerMappings().then((data) => { dealerMappings.value = normalizeList(data); }));
      }
      await Promise.all(tasks);
    } catch (error) {
      loadError.value = normalizeError(error);
      handleExpiredSession(error);
    } finally {
      loading.value = false;
    }
  }

  async function createUser(input) {
    const temporaryPassword = createTemporaryPassword();
    const user = await perform("create-user", () => createUserRequest({
      ...input,
      temporaryPassword
    }), dictionary.value.adminUserCreated);
    if (user) {
      users.value = [...users.value, user];
      oneTimePassword.value = {
        label: user.displayName || user.username,
        password: temporaryPassword
      };
    }
    return user;
  }

  async function changeUserEnabled(user, enabled) {
    const updated = await perform(
      `user-enabled-${user.id}`,
      () => changeUserEnabledRequest(user.id, enabled, user.version),
      enabled ? dictionary.value.adminUserEnabled : dictionary.value.adminUserDisabled
    );
    if (updated) {
      replaceById(users, updated);
      if (!enabled && isCurrentUser(user.id)) {
        onIdentityRevoked?.();
      }
    }
    return updated;
  }

  async function changeUserEmail(user, email) {
    const updated = await perform(
      `user-email-${user.id}`,
      () => changeUserEmailRequest(user.id, email?.trim() || null, user.version),
      dictionary.value.adminEmailUpdated
    );
    if (updated) {
      replaceById(users, updated);
    }
    return updated;
  }

  async function assignUserRoles(user, roleKeys) {
    const updated = await perform(
      `user-roles-${user.id}`,
      () => assignUserRolesRequest(user.id, roleKeys, user.version),
      dictionary.value.adminRolesAssigned
    );
    if (updated) {
      replaceById(users, updated);
      if (isCurrentUser(user.id)) {
        onIdentityRevoked?.();
      }
    }
    return updated;
  }

  async function resetUserPassword(user) {
    const temporaryPassword = createTemporaryPassword();
    const updated = await perform(
      `reset-password-${user.id}`,
      () => resetUserPasswordRequest(user.id, temporaryPassword, user.version),
      dictionary.value.adminPasswordReset
    );
    if (updated) {
      replaceById(users, updated);
      oneTimePassword.value = {
        label: user.displayName || user.username,
        password: temporaryPassword
      };
      if (isCurrentUser(user.id)) {
        onIdentityRevoked?.();
      }
      return true;
    }
    return false;
  }

  async function loadSessions(userId) {
    const result = await perform(
      `load-sessions-${userId}`,
      () => listUserSessions(userId),
      "",
      { quiet: true }
    );
    if (result) {
      userSessions.value = normalizeList(result);
    }
    return result;
  }

  async function revokeSessions(user) {
    const sessions = await perform(
      `revoke-sessions-${user.id}`,
      () => revokeUserSessionsRequest(user.id),
      dictionary.value.adminSessionsRevoked
    );
    if (sessions) {
      userSessions.value = normalizeList(sessions);
      if (isCurrentUser(user.id)) {
        onIdentityRevoked?.();
      }
    }
    return sessions;
  }

  async function createRole(input) {
    const role = await perform("create-role", () => createRoleRequest(input), dictionary.value.adminRoleCreated);
    if (role) {
      roles.value = [...roles.value, role];
    }
    return role;
  }

  async function updateRolePermissions(role, permissionKeys) {
    const updated = await perform(
      `role-permissions-${role.id}`,
      () => updateRolePermissionsRequest(role.id, permissionKeys, role.version),
      dictionary.value.adminPermissionsUpdated
    );
    if (updated) {
      replaceById(roles, updated);
      if ((currentUser.value?.roles ?? []).includes(role.roleKey)) {
        onIdentityRevoked?.();
      }
    }
    return updated;
  }

  async function createOrganizationNode(input) {
    const node = await perform(
      "create-organization-node",
      () => createOrganizationNodeRequest(input),
      dictionary.value.adminOrganizationCreated
    );
    if (node) {
      organizationNodes.value = [...organizationNodes.value, node];
    }
    return node;
  }

  async function updateOrganizationNode(nodeId, input) {
    const node = await perform(
      `update-organization-node-${nodeId}`,
      () => updateOrganizationNodeRequest(nodeId, input),
      dictionary.value.adminOrganizationUpdated
    );
    if (node) {
      replaceById(organizationNodes, node);
    }
    return node;
  }

  async function mapDealer(dealerCode, organizationNodeId) {
    const mapping = await perform(
      `map-dealer-${dealerCode}`,
      () => mapDealerRequest(dealerCode, organizationNodeId),
      dictionary.value.adminDealerMapped
    );
    if (mapping) {
      const withoutDealer = dealerMappings.value.filter((item) => item.dealerCode !== mapping.dealerCode);
      dealerMappings.value = [...withoutDealer, mapping];
    }
    return mapping;
  }

  async function loadGrants(subjectType, subjectId) {
    const grants = await perform(
      `load-grants-${subjectType}-${subjectId}`,
      () => listSubjectGrants(subjectType, subjectId),
      "",
      { quiet: true }
    );
    if (grants) {
      subjectGrants.value = normalizeList(grants);
    }
    return grants;
  }

  async function replaceGrants(subjectType, subjectId, grants) {
    const updated = await perform(
      `replace-grants-${subjectType}-${subjectId}`,
      () => replaceSubjectGrantsRequest(subjectType, subjectId, grants),
      dictionary.value.adminGrantsUpdated
    );
    if (updated) {
      subjectGrants.value = normalizeList(updated);
    }
    return updated;
  }

  async function refreshAuditEvents() {
    const events = await perform("refresh-audit", listAuditEvents, "", { quiet: true });
    if (events) {
      auditEvents.value = normalizeList(events);
    }
    return events;
  }

  function dismissOneTimePassword() {
    oneTimePassword.value = null;
  }

  function clearFeedback() {
    operationError.value = null;
    successMessage.value = "";
  }

  async function perform(actionKey, action, notice, { quiet = false } = {}) {
    pendingAction.value = actionKey;
    operationError.value = null;
    if (!quiet) {
      successMessage.value = "";
    }
    try {
      const result = await action();
      if (!quiet) {
        successMessage.value = notice ?? "";
      }
      return result === undefined ? true : result;
    } catch (error) {
      operationError.value = normalizeError(error);
      handleExpiredSession(error);
      return null;
    } finally {
      pendingAction.value = "";
    }
  }

  function normalizeError(error) {
    const status = Number(error?.status ?? 0);
    const messageByStatus = {
      400: dictionary.value.adminValidationError,
      403: dictionary.value.adminForbiddenError,
      409: dictionary.value.adminConflictError
    };
    return {
      status,
      message: messageByStatus[status] ?? dictionary.value.adminRequestError,
      detail: status === 400 ? String(error?.message ?? "") : ""
    };
  }

  function handleExpiredSession(error) {
    if (error?.status === 401) {
      onAuthExpired?.();
    }
  }

  function isCurrentUser(userId) {
    return String(currentUser.value?.id ?? "") === String(userId ?? "");
  }

  return {
    auditEvents,
    canManageGrants,
    canManageOrganization,
    canManageRoles,
    canManageUsers,
    canReadOrganization,
    canReadModelUsage,
    canReadRoles,
    canReadUsers,
    changeUserEnabled,
    changeUserEmail,
    clearFeedback,
    createOrganizationNode,
    createRole,
    createUser,
    dealerMappings,
    dismissOneTimePassword,
    initialize,
    loadError,
    loadGrants,
    loadSessions,
    loading,
    mapDealer,
    oneTimePassword,
    operationError,
    organizationNodes,
    pendingAction,
    refreshAuditEvents,
    replaceGrants,
    resetUserPassword,
    revokeSessions,
    roles,
    subjectGrants,
    successMessage,
    updateOrganizationNode,
    updateRolePermissions,
    userSessions,
    users,
    assignUserRoles
  };
}

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}

function replaceById(target, replacement) {
  target.value = target.value.map((item) => item.id === replacement.id ? replacement : item);
}
