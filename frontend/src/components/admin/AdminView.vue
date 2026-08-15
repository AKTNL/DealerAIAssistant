<script setup>
import { computed, onMounted, ref } from "vue";
import { useAdministration } from "../../composables/useAdministration";
import { PERMISSION_KEYS } from "../../constants/permissionCatalog";
import ModelUsagePanel from "./ModelUsagePanel.vue";
import NotificationSmtpPanel from "./NotificationSmtpPanel.vue";

const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  },
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    required: true
  }
});

const emit = defineEmits(["sign-out"]);

const admin = useAdministration({
  currentUser: computed(() => props.currentUser),
  dictionary: computed(() => props.dictionary),
  onAuthExpired: () => emit("sign-out"),
  onIdentityRevoked: () => emit("sign-out")
});

const activeSection = ref("");
const selectedUserId = ref("");
const selectedRoleId = ref("");
const selectedNodeId = ref("");
const userRoleDraft = ref([]);
const userEmailDraft = ref("");
const rolePermissionDraft = ref([]);
const grantSubjectType = ref("user");
const grantSubjectId = ref("");
const grantDraft = ref({});
const userForm = ref({ username: "", displayName: "", email: "", roles: [] });
const roleForm = ref({ roleKey: "", displayName: "", permissions: ["DASHBOARD_READ"] });
const nodeForm = ref({
  nodeKey: "",
  displayName: "",
  nodeType: "GROUP",
  parentId: "",
  enabled: true
});
const nodeEditForm = ref({
  displayName: "",
  nodeType: "GROUP",
  parentId: "",
  enabled: true
});
const dealerForm = ref({ dealerCode: "", organizationNodeId: "" });

const sections = computed(() => [
  admin.canReadUsers.value ? { id: "users", label: props.dictionary.adminUsersTab, icon: "group" } : null,
  admin.canManageUsers.value ? { id: "smtp", label: props.dictionary.smtpTab, icon: "mail" } : null,
  admin.canReadRoles.value ? { id: "roles", label: props.dictionary.adminRolesTab, icon: "shield" } : null,
  admin.canReadOrganization.value
    ? { id: "organization", label: props.dictionary.adminOrganizationTab, icon: "account_tree" }
    : null,
  admin.canReadOrganization.value && admin.canManageGrants.value
    ? { id: "grants", label: props.dictionary.adminGrantsTab, icon: "policy" }
    : null,
  admin.canReadModelUsage.value
    ? { id: "model-usage", label: props.dictionary.modelUsageTab, icon: "monitoring" }
    : null,
  admin.canReadUsers.value ? { id: "audit", label: props.dictionary.adminAuditTab, icon: "history" } : null
].filter(Boolean));

const selectedUser = computed(() => admin.users.value.find(
  (user) => String(user.id) === String(selectedUserId.value)
) ?? null);
const selectedRole = computed(() => admin.roles.value.find(
  (role) => String(role.id) === String(selectedRoleId.value)
) ?? null);
const selectedNode = computed(() => admin.organizationNodes.value.find(
  (node) => String(node.id) === String(selectedNodeId.value)
) ?? null);
const dealerNodes = computed(() => admin.organizationNodes.value.filter(
  (node) => node.nodeType === "DEALER" && node.enabled
));
const flattenedNodes = computed(() => flattenNodes(admin.organizationNodes.value));
const grantSubjects = computed(() => grantSubjectType.value === "role" ? admin.roles.value : admin.users.value);

onMounted(async () => {
  await admin.initialize();
  activeSection.value = sections.value[0]?.id ?? "";
  if (admin.users.value.length > 0) {
    await selectUser(admin.users.value[0]);
  }
  if (admin.roles.value.length > 0) {
    selectRole(admin.roles.value[0]);
  }
  if (admin.organizationNodes.value.length > 0) {
    selectNode(admin.organizationNodes.value[0]);
  }
});

async function handleCreateUser() {
  const created = await admin.createUser({
    username: userForm.value.username,
    displayName: userForm.value.displayName,
    email: userForm.value.email,
    roles: [...userForm.value.roles]
  });
  if (created) {
    userForm.value = { username: "", displayName: "", email: "", roles: [] };
    await selectUser(created);
  }
}

async function selectUser(user) {
  selectedUserId.value = user?.id ?? "";
  userRoleDraft.value = [...(user?.roles ?? [])];
  userEmailDraft.value = user?.email ?? "";
  admin.userSessions.value = [];
  if (user?.id != null) {
    await admin.loadSessions(user.id);
  }
}

async function handleSaveUserEmail() {
  if (!selectedUser.value) {
    return;
  }
  const updated = await admin.changeUserEmail(selectedUser.value, userEmailDraft.value);
  if (updated) {
    userEmailDraft.value = updated.email ?? "";
  }
}

async function handleToggleUser() {
  if (!selectedUser.value) {
    return;
  }
  const enabled = !selectedUser.value.enabled;
  const message = enabled
    ? props.dictionary.adminConfirmEnableUser
    : props.dictionary.adminConfirmDisableUser;
  if (!confirmAction(message)) {
    return;
  }
  await admin.changeUserEnabled(selectedUser.value, enabled);
}

async function handleSaveUserRoles() {
  if (!selectedUser.value || !confirmAction(props.dictionary.adminConfirmRoleAssignment)) {
    return;
  }
  const updated = await admin.assignUserRoles(selectedUser.value, [...userRoleDraft.value]);
  if (updated) {
    userRoleDraft.value = [...(updated.roles ?? [])];
  }
}

async function handleResetPassword() {
  if (selectedUser.value && confirmAction(props.dictionary.adminConfirmPasswordReset)) {
    await admin.resetUserPassword(selectedUser.value);
  }
}

async function handleRevokeSessions() {
  if (selectedUser.value && confirmAction(props.dictionary.adminConfirmSessionRevoke)) {
    await admin.revokeSessions(selectedUser.value);
  }
}

async function handleCreateRole() {
  const created = await admin.createRole({
    roleKey: roleForm.value.roleKey,
    displayName: roleForm.value.displayName,
    permissions: [...roleForm.value.permissions]
  });
  if (created) {
    roleForm.value = { roleKey: "", displayName: "", permissions: ["DASHBOARD_READ"] };
    selectRole(created);
  }
}

function selectRole(role) {
  selectedRoleId.value = role?.id ?? "";
  rolePermissionDraft.value = [...(role?.permissions ?? [])];
}

async function handleSaveRolePermissions() {
  if (!selectedRole.value || selectedRole.value.builtIn) {
    return;
  }
  if (!confirmAction(props.dictionary.adminConfirmPermissionUpdate)) {
    return;
  }
  const updated = await admin.updateRolePermissions(selectedRole.value, [...rolePermissionDraft.value]);
  if (updated) {
    rolePermissionDraft.value = [...(updated.permissions ?? [])];
  }
}

async function handleCreateNode() {
  const created = await admin.createOrganizationNode(normalizeNodeForm(nodeForm.value, true));
  if (created) {
    nodeForm.value = {
      nodeKey: "",
      displayName: "",
      nodeType: "GROUP",
      parentId: "",
      enabled: true
    };
    selectNode(created);
  }
}

function selectNode(node) {
  selectedNodeId.value = node?.id ?? "";
  nodeEditForm.value = {
    displayName: node?.displayName ?? "",
    nodeType: node?.nodeType ?? "GROUP",
    parentId: node?.parentId ?? "",
    enabled: node?.enabled ?? true
  };
}

async function handleUpdateNode() {
  if (!selectedNode.value || !confirmAction(props.dictionary.adminConfirmOrganizationUpdate)) {
    return;
  }
  const updated = await admin.updateOrganizationNode(
    selectedNode.value.id,
    normalizeNodeForm(nodeEditForm.value, false)
  );
  if (updated) {
    selectNode(updated);
  }
}

async function handleMapDealer() {
  const mapping = await admin.mapDealer(
    dealerForm.value.dealerCode.trim(),
    Number(dealerForm.value.organizationNodeId)
  );
  if (mapping) {
    dealerForm.value = { dealerCode: "", organizationNodeId: "" };
  }
}

async function handleGrantSubjectChange() {
  grantDraft.value = {};
  admin.subjectGrants.value = [];
  if (!grantSubjectId.value) {
    return;
  }
  const grants = await admin.loadGrants(grantSubjectType.value, grantSubjectId.value);
  if (grants) {
    grantDraft.value = Object.fromEntries(grants.map((grant) => [
      String(grant.organizationNodeId),
      { includeDescendants: grant.includeDescendants === true }
    ]));
  }
}

function handleGrantTypeChange() {
  grantSubjectId.value = "";
  grantDraft.value = {};
  admin.subjectGrants.value = [];
}

function toggleGrant(nodeId, enabled) {
  const key = String(nodeId);
  const next = { ...grantDraft.value };
  if (enabled) {
    next[key] = { includeDescendants: false };
  } else {
    delete next[key];
  }
  grantDraft.value = next;
}

function toggleGrantDescendants(nodeId, enabled) {
  const key = String(nodeId);
  if (!grantDraft.value[key]) {
    return;
  }
  grantDraft.value = {
    ...grantDraft.value,
    [key]: { includeDescendants: enabled }
  };
}

async function handleSaveGrants() {
  if (!grantSubjectId.value || !confirmAction(props.dictionary.adminConfirmGrantUpdate)) {
    return;
  }
  const grants = Object.entries(grantDraft.value).map(([organizationNodeId, grant]) => ({
    organizationNodeId: Number(organizationNodeId),
    includeDescendants: grant.includeDescendants === true
  }));
  await admin.replaceGrants(grantSubjectType.value, grantSubjectId.value, grants);
}

function normalizeNodeForm(form, includeKey) {
  const normalized = {
    displayName: form.displayName.trim(),
    nodeType: form.nodeType,
    parentId: form.parentId === "" ? null : Number(form.parentId),
    enabled: form.enabled === true,
    ...(includeKey ? {} : { version: selectedNode.value?.version ?? null })
  };
  return includeKey ? { ...normalized, nodeKey: form.nodeKey.trim() } : normalized;
}

function flattenNodes(nodes) {
  const normalized = Array.isArray(nodes) ? nodes : [];
  const children = new Map();
  for (const node of normalized) {
    const parentKey = node.parentId == null ? "root" : String(node.parentId);
    children.set(parentKey, [...(children.get(parentKey) ?? []), node]);
  }
  for (const entries of children.values()) {
    entries.sort((left, right) => String(left.displayName).localeCompare(String(right.displayName)));
  }

  const flattened = [];
  const visited = new Set();
  function visit(node, depth) {
    const key = String(node.id);
    if (visited.has(key)) {
      return;
    }
    visited.add(key);
    flattened.push({ ...node, depth });
    for (const child of children.get(key) ?? []) {
      visit(child, depth + 1);
    }
  }
  for (const root of children.get("root") ?? []) {
    visit(root, 0);
  }
  for (const node of normalized) {
    visit(node, 0);
  }
  return flattened;
}

function permissionLabel(permission) {
  return props.dictionary.adminPermissionLabels?.[permission] ?? permission;
}

function nodeLabel(node) {
  return `${"— ".repeat(node.depth ?? 0)}${node.displayName} · ${node.nodeType}`;
}

function subjectLabel(subject) {
  return grantSubjectType.value === "role"
    ? `${subject.displayName} (${subject.roleKey})`
    : `${subject.displayName || subject.username} (${subject.username})`;
}

function formatDate(value) {
  if (!value) {
    return props.dictionary.adminNotAvailable;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return props.dictionary.adminNotAvailable;
  }
  return new Intl.DateTimeFormat(props.locale === "zh" ? "zh-CN" : "en", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function confirmAction(message) {
  return typeof window === "undefined" || window.confirm(message);
}
</script>

<template>
  <section class="admin-screen" aria-labelledby="admin-title">
    <header class="admin-hero">
      <div>
        <p class="eyebrow">{{ dictionary.adminEyebrow }}</p>
        <h2 id="admin-title">{{ dictionary.adminTitle }}</h2>
        <p>{{ dictionary.adminSubtitle }}</p>
      </div>
      <button class="ghost-button" type="button" :disabled="admin.loading.value" @click="admin.initialize">
        <span class="material-icons" aria-hidden="true">refresh</span>
        {{ dictionary.adminRefresh }}
      </button>
    </header>

    <div v-if="admin.loading.value" class="admin-state-card" role="status">
      <span class="skeleton-spinner-icon" aria-hidden="true"></span>
      <div>
        <h3>{{ dictionary.adminLoadingTitle }}</h3>
        <p>{{ dictionary.adminLoadingBody }}</p>
      </div>
    </div>

    <div v-else-if="admin.loadError.value" class="admin-state-card admin-error-card" role="alert">
      <span class="material-icons" aria-hidden="true">lock</span>
      <div>
        <h3>{{ admin.loadError.value.status === 403 ? dictionary.adminForbiddenTitle : dictionary.adminErrorTitle }}</h3>
        <p>{{ admin.loadError.value.message }}</p>
        <button class="ghost-button" type="button" @click="admin.initialize">{{ dictionary.adminRetry }}</button>
      </div>
    </div>

    <template v-else>
      <nav class="admin-section-tabs" :aria-label="dictionary.adminSectionNavigation">
        <button
          v-for="section in sections"
          :key="section.id"
          :class="['admin-section-tab', { active: activeSection === section.id }]"
          type="button"
          :aria-pressed="activeSection === section.id"
          @click="activeSection = section.id; admin.clearFeedback()"
        >
          <span class="material-icons" aria-hidden="true">{{ section.icon }}</span>
          {{ section.label }}
        </button>
      </nav>

      <div v-if="admin.operationError.value" class="admin-feedback admin-feedback-error" role="alert">
        <strong>{{ admin.operationError.value.message }}</strong>
        <span v-if="admin.operationError.value.detail">{{ admin.operationError.value.detail }}</span>
      </div>
      <div v-else-if="admin.successMessage.value" class="admin-feedback" role="status">
        {{ admin.successMessage.value }}
      </div>

      <section v-if="activeSection === 'users'" class="admin-panel" aria-labelledby="admin-users-heading">
        <div class="admin-panel-heading">
          <div>
            <h3 id="admin-users-heading">{{ dictionary.adminUsersTitle }}</h3>
            <p>{{ dictionary.adminUsersBody }}</p>
          </div>
        </div>

        <form v-if="admin.canManageUsers.value" class="admin-inline-form" @submit.prevent="handleCreateUser">
          <label>
            <span>{{ dictionary.adminUsernameLabel }}</span>
            <input v-model="userForm.username" class="text-input" required autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminDisplayNameLabel }}</span>
            <input v-model="userForm.displayName" class="text-input" autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminEmailLabel }}</span>
            <input v-model="userForm.email" class="text-input" type="email" autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminRolesLabel }}</span>
            <select v-model="userForm.roles" class="text-input" multiple required>
              <option v-for="role in admin.roles.value" :key="role.id" :value="role.roleKey">
                {{ role.displayName }} ({{ role.roleKey }})
              </option>
            </select>
          </label>
          <button class="primary-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
            {{ dictionary.adminCreateUser }}
          </button>
        </form>

        <div v-if="admin.users.value.length === 0" class="admin-empty-card">
          <span class="material-icons" aria-hidden="true">person_off</span>
          <p>{{ dictionary.adminUsersEmpty }}</p>
        </div>
        <div v-else class="admin-master-detail">
          <div class="admin-list" role="list">
            <button
              v-for="user in admin.users.value"
              :key="user.id"
              :class="['admin-list-item', { active: String(selectedUserId) === String(user.id) }]"
              type="button"
              @click="selectUser(user)"
            >
              <span>
                <strong>{{ user.displayName || user.username }}</strong>
                <small>{{ user.username }}</small>
              </span>
              <span :class="['admin-status-badge', { muted: !user.enabled }]">
                {{ user.enabled ? dictionary.adminEnabled : dictionary.adminDisabled }}
              </span>
            </button>
          </div>

          <div v-if="selectedUser" class="admin-detail-card">
            <div class="admin-detail-heading">
              <div>
                <h4>{{ selectedUser.displayName || selectedUser.username }}</h4>
                <p>{{ selectedUser.username }}</p>
              </div>
              <span v-if="selectedUser.mustChangePassword" class="admin-status-badge warning">
                {{ dictionary.adminTemporaryPasswordRequired }}
              </span>
            </div>

            <fieldset class="admin-checkbox-grid" :disabled="!admin.canManageUsers.value">
              <legend>{{ dictionary.adminAssignedRoles }}</legend>
              <label v-for="role in admin.roles.value" :key="role.id">
                <input v-model="userRoleDraft" type="checkbox" :value="role.roleKey" />
                <span>{{ role.displayName }} · {{ role.roleKey }}</span>
              </label>
            </fieldset>

            <form v-if="admin.canManageUsers.value" class="admin-inline-form" @submit.prevent="handleSaveUserEmail">
              <label>
                <span>{{ dictionary.adminEmailLabel }}</span>
                <input v-model="userEmailDraft" class="text-input" type="email" autocomplete="off" />
              </label>
              <button class="ghost-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
                {{ dictionary.adminSaveEmail }}
              </button>
            </form>

            <div v-if="admin.canManageUsers.value" class="admin-action-row">
              <button class="primary-button" type="button" :disabled="Boolean(admin.pendingAction.value)" @click="handleSaveUserRoles">
                {{ dictionary.adminSaveRoles }}
              </button>
              <button class="ghost-button" type="button" :disabled="Boolean(admin.pendingAction.value)" @click="handleToggleUser">
                {{ selectedUser.enabled ? dictionary.adminDisableUser : dictionary.adminEnableUser }}
              </button>
              <button class="admin-danger-button" type="button" :disabled="Boolean(admin.pendingAction.value)" @click="handleResetPassword">
                {{ dictionary.adminResetPassword }}
              </button>
              <button class="admin-danger-button" type="button" :disabled="Boolean(admin.pendingAction.value)" @click="handleRevokeSessions">
                {{ dictionary.adminRevokeSessions }}
              </button>
            </div>

            <div class="admin-subsection-heading">
              <div>
                <h4>{{ dictionary.adminSessionsTitle }}</h4>
                <p>{{ dictionary.adminSessionsBody }}</p>
              </div>
              <button class="ghost-button" type="button" @click="admin.loadSessions(selectedUser.id)">
                {{ dictionary.adminRefresh }}
              </button>
            </div>
            <p v-if="admin.userSessions.value.length === 0" class="admin-empty-line">
              {{ dictionary.adminSessionsEmpty }}
            </p>
            <div v-else class="admin-table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>{{ dictionary.adminSessionIssued }}</th>
                    <th>{{ dictionary.adminSessionExpires }}</th>
                    <th>{{ dictionary.adminStatusLabel }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="session in admin.userSessions.value" :key="session.id">
                    <td>{{ formatDate(session.issuedAt) }}</td>
                    <td>{{ formatDate(session.refreshExpiresAt) }}</td>
                    <td>
                      <span :class="['admin-status-badge', { muted: !session.active }]">
                        {{ session.active ? dictionary.adminSessionActive : dictionary.adminSessionRevoked }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <NotificationSmtpPanel
        v-else-if="activeSection === 'smtp'"
        :dictionary="dictionary"
        @sign-out="emit('sign-out')"
      />

      <ModelUsagePanel
        v-else-if="activeSection === 'model-usage'"
        :current-user="currentUser"
        :dictionary="dictionary"
        :locale="locale"
        @sign-out="emit('sign-out')"
      />

      <section v-else-if="activeSection === 'roles'" class="admin-panel" aria-labelledby="admin-roles-heading">
        <div class="admin-panel-heading">
          <div>
            <h3 id="admin-roles-heading">{{ dictionary.adminRolesTitle }}</h3>
            <p>{{ dictionary.adminRolesBody }}</p>
          </div>
        </div>

        <form v-if="admin.canManageRoles.value" class="admin-inline-form" @submit.prevent="handleCreateRole">
          <label>
            <span>{{ dictionary.adminRoleKeyLabel }}</span>
            <input v-model="roleForm.roleKey" class="text-input" required autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminDisplayNameLabel }}</span>
            <input v-model="roleForm.displayName" class="text-input" autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminInitialPermissionLabel }}</span>
            <select v-model="roleForm.permissions" class="text-input" multiple required>
              <option v-for="permission in PERMISSION_KEYS" :key="permission" :value="permission">
                {{ permissionLabel(permission) }}
              </option>
            </select>
          </label>
          <button class="primary-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
            {{ dictionary.adminCreateRole }}
          </button>
        </form>

        <div v-if="admin.roles.value.length === 0" class="admin-empty-card">
          <p>{{ dictionary.adminRolesEmpty }}</p>
        </div>
        <div v-else class="admin-master-detail">
          <div class="admin-list">
            <button
              v-for="role in admin.roles.value"
              :key="role.id"
              :class="['admin-list-item', { active: String(selectedRoleId) === String(role.id) }]"
              type="button"
              @click="selectRole(role)"
            >
              <span>
                <strong>{{ role.displayName }}</strong>
                <small>{{ role.roleKey }}</small>
              </span>
              <span v-if="role.builtIn" class="admin-status-badge">{{ dictionary.adminBuiltInRole }}</span>
            </button>
          </div>
          <div v-if="selectedRole" class="admin-detail-card">
            <div class="admin-detail-heading">
              <div>
                <h4>{{ selectedRole.displayName }}</h4>
                <p>{{ selectedRole.roleKey }}</p>
              </div>
              <span v-if="selectedRole.builtIn" class="admin-status-badge">{{ dictionary.adminProtectedRole }}</span>
            </div>
            <fieldset class="admin-permission-matrix" :disabled="selectedRole.builtIn || !admin.canManageRoles.value">
              <legend>{{ dictionary.adminPermissionMatrix }}</legend>
              <label v-for="permission in PERMISSION_KEYS" :key="permission">
                <input v-model="rolePermissionDraft" type="checkbox" :value="permission" />
                <span>
                  <strong>{{ permissionLabel(permission) }}</strong>
                  <small>{{ permission }}</small>
                </span>
              </label>
            </fieldset>
            <button
              v-if="admin.canManageRoles.value"
              class="primary-button"
              type="button"
              :disabled="selectedRole.builtIn || Boolean(admin.pendingAction.value)"
              @click="handleSaveRolePermissions"
            >
              {{ dictionary.adminSavePermissions }}
            </button>
          </div>
        </div>
      </section>

      <section v-else-if="activeSection === 'organization'" class="admin-panel" aria-labelledby="admin-organization-heading">
        <div class="admin-panel-heading">
          <div>
            <h3 id="admin-organization-heading">{{ dictionary.adminOrganizationTitle }}</h3>
            <p>{{ dictionary.adminOrganizationBody }}</p>
          </div>
        </div>

        <form v-if="admin.canManageOrganization.value" class="admin-inline-form" @submit.prevent="handleCreateNode">
          <label>
            <span>{{ dictionary.adminNodeKeyLabel }}</span>
            <input v-model="nodeForm.nodeKey" class="text-input" required autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminDisplayNameLabel }}</span>
            <input v-model="nodeForm.displayName" class="text-input" required autocomplete="off" />
          </label>
          <label>
            <span>{{ dictionary.adminNodeTypeLabel }}</span>
            <select v-model="nodeForm.nodeType" class="text-input">
              <option value="GROUP">GROUP</option>
              <option value="REGION">REGION</option>
              <option value="CITY">CITY</option>
              <option value="DEALER">DEALER</option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.adminParentNodeLabel }}</span>
            <select v-model="nodeForm.parentId" class="text-input">
              <option value="">{{ dictionary.adminRootNode }}</option>
              <option v-for="node in flattenedNodes" :key="node.id" :value="node.id">{{ nodeLabel(node) }}</option>
            </select>
          </label>
          <label class="admin-toggle-label">
            <input v-model="nodeForm.enabled" type="checkbox" />
            <span>{{ dictionary.adminEnabled }}</span>
          </label>
          <button class="primary-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
            {{ dictionary.adminCreateNode }}
          </button>
        </form>

        <div v-if="flattenedNodes.length === 0" class="admin-empty-card">
          <p>{{ dictionary.adminOrganizationEmpty }}</p>
        </div>
        <div v-else class="admin-master-detail">
          <div class="admin-tree-list">
            <button
              v-for="node in flattenedNodes"
              :key="node.id"
              :class="['admin-tree-item', { active: String(selectedNodeId) === String(node.id) }]"
              type="button"
              :style="{ '--admin-tree-depth': node.depth }"
              @click="selectNode(node)"
            >
              <span class="material-icons" aria-hidden="true">{{ node.nodeType === 'DEALER' ? 'storefront' : 'account_tree' }}</span>
              <span>
                <strong>{{ node.displayName }}</strong>
                <small>{{ node.nodeKey }} · {{ node.nodeType }}</small>
              </span>
            </button>
          </div>
          <form v-if="selectedNode" class="admin-detail-card" @submit.prevent="handleUpdateNode">
            <h4>{{ dictionary.adminEditNode }}</h4>
            <label>
              <span>{{ dictionary.adminDisplayNameLabel }}</span>
              <input v-model="nodeEditForm.displayName" class="text-input" required :disabled="!admin.canManageOrganization.value" />
            </label>
            <label>
              <span>{{ dictionary.adminNodeTypeLabel }}</span>
              <select v-model="nodeEditForm.nodeType" class="text-input" :disabled="!admin.canManageOrganization.value">
                <option value="GROUP">GROUP</option>
                <option value="REGION">REGION</option>
                <option value="CITY">CITY</option>
                <option value="DEALER">DEALER</option>
              </select>
            </label>
            <label>
              <span>{{ dictionary.adminParentNodeLabel }}</span>
              <select v-model="nodeEditForm.parentId" class="text-input" :disabled="!admin.canManageOrganization.value">
                <option value="">{{ dictionary.adminRootNode }}</option>
                <option v-for="node in flattenedNodes.filter((item) => item.id !== selectedNode.id)" :key="node.id" :value="node.id">
                  {{ nodeLabel(node) }}
                </option>
              </select>
            </label>
            <label class="admin-toggle-label">
              <input v-model="nodeEditForm.enabled" type="checkbox" :disabled="!admin.canManageOrganization.value" />
              <span>{{ dictionary.adminEnabled }}</span>
            </label>
            <button v-if="admin.canManageOrganization.value" class="primary-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
              {{ dictionary.adminSaveNode }}
            </button>
          </form>
        </div>

        <div class="admin-subpanel">
          <div class="admin-subsection-heading">
            <div>
              <h4>{{ dictionary.adminDealerMappingsTitle }}</h4>
              <p>{{ dictionary.adminDealerMappingsBody }}</p>
            </div>
          </div>
          <form v-if="admin.canManageOrganization.value" class="admin-inline-form" @submit.prevent="handleMapDealer">
            <label>
              <span>{{ dictionary.adminDealerCodeLabel }}</span>
              <input v-model="dealerForm.dealerCode" class="text-input" required autocomplete="off" />
            </label>
            <label>
              <span>{{ dictionary.adminDealerNodeLabel }}</span>
              <select v-model="dealerForm.organizationNodeId" class="text-input" required>
                <option value="" disabled>{{ dictionary.adminSelectOption }}</option>
                <option v-for="node in dealerNodes" :key="node.id" :value="node.id">{{ node.displayName }}</option>
              </select>
            </label>
            <button class="primary-button" type="submit" :disabled="Boolean(admin.pendingAction.value)">
              {{ dictionary.adminMapDealer }}
            </button>
          </form>
          <p v-if="admin.dealerMappings.value.length === 0" class="admin-empty-line">{{ dictionary.adminDealerMappingsEmpty }}</p>
          <div v-else class="admin-chip-list">
            <span v-for="mapping in admin.dealerMappings.value" :key="mapping.id" class="admin-data-chip">
              {{ mapping.dealerCode }} → {{ admin.organizationNodes.value.find((node) => node.id === mapping.organizationNodeId)?.displayName ?? mapping.organizationNodeId }}
            </span>
          </div>
        </div>
      </section>

      <section v-else-if="activeSection === 'grants'" class="admin-panel" aria-labelledby="admin-grants-heading">
        <div class="admin-panel-heading">
          <div>
            <h3 id="admin-grants-heading">{{ dictionary.adminGrantsTitle }}</h3>
            <p>{{ dictionary.adminGrantsBody }}</p>
          </div>
        </div>
        <div class="admin-grant-toolbar">
          <label>
            <span>{{ dictionary.adminGrantSubjectType }}</span>
            <select v-model="grantSubjectType" class="text-input" @change="handleGrantTypeChange">
              <option value="user">{{ dictionary.adminGrantUser }}</option>
              <option value="role">{{ dictionary.adminGrantRole }}</option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.adminGrantSubject }}</span>
            <select v-model="grantSubjectId" class="text-input" @change="handleGrantSubjectChange">
              <option value="">{{ dictionary.adminSelectOption }}</option>
              <option v-for="subject in grantSubjects" :key="subject.id" :value="subject.id">{{ subjectLabel(subject) }}</option>
            </select>
          </label>
        </div>
        <div v-if="!grantSubjectId" class="admin-empty-card">
          <p>{{ dictionary.adminGrantSelectPrompt }}</p>
        </div>
        <div v-else class="admin-grant-list">
          <div v-for="node in flattenedNodes" :key="node.id" class="admin-grant-row">
            <label>
              <input
                type="checkbox"
                :checked="Boolean(grantDraft[String(node.id)])"
                @change="toggleGrant(node.id, $event.target.checked)"
              />
              <span>{{ nodeLabel(node) }}</span>
            </label>
            <label>
              <input
                type="checkbox"
                :disabled="!grantDraft[String(node.id)]"
                :checked="grantDraft[String(node.id)]?.includeDescendants === true"
                @change="toggleGrantDescendants(node.id, $event.target.checked)"
              />
              <span>{{ dictionary.adminIncludeDescendants }}</span>
            </label>
          </div>
          <button class="primary-button" type="button" :disabled="Boolean(admin.pendingAction.value)" @click="handleSaveGrants">
            {{ dictionary.adminSaveGrants }}
          </button>
        </div>
      </section>

      <section v-else-if="activeSection === 'audit'" class="admin-panel" aria-labelledby="admin-audit-heading">
        <div class="admin-panel-heading">
          <div>
            <h3 id="admin-audit-heading">{{ dictionary.adminAuditTitle }}</h3>
            <p>{{ dictionary.adminAuditBody }}</p>
          </div>
          <button class="ghost-button" type="button" @click="admin.refreshAuditEvents">{{ dictionary.adminRefresh }}</button>
        </div>
        <div v-if="admin.auditEvents.value.length === 0" class="admin-empty-card">
          <p>{{ dictionary.adminAuditEmpty }}</p>
        </div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>{{ dictionary.adminAuditTime }}</th>
                <th>{{ dictionary.adminAuditAction }}</th>
                <th>{{ dictionary.adminAuditTarget }}</th>
                <th>{{ dictionary.adminAuditOutcome }}</th>
                <th>{{ dictionary.adminAuditTrace }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="event in admin.auditEvents.value" :key="event.id">
                <td>{{ formatDate(event.createdAt) }}</td>
                <td>{{ event.action }}</td>
                <td>{{ event.targetType }} · {{ event.targetId ?? dictionary.adminNotAvailable }}</td>
                <td>{{ event.outcome }}</td>
                <td><code>{{ event.traceId }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="admin.oneTimePassword.value" class="admin-secret-backdrop" role="presentation">
        <section class="admin-secret-dialog" role="dialog" aria-modal="true" :aria-label="dictionary.adminTemporaryPasswordTitle">
          <span class="material-icons" aria-hidden="true">key</span>
          <h3>{{ dictionary.adminTemporaryPasswordTitle }}</h3>
          <p>{{ dictionary.adminTemporaryPasswordBody }}</p>
          <strong>{{ admin.oneTimePassword.value.label }}</strong>
          <code class="admin-secret-value">{{ admin.oneTimePassword.value.password }}</code>
          <p class="admin-secret-warning">{{ dictionary.adminTemporaryPasswordWarning }}</p>
          <button class="primary-button" type="button" @click="admin.dismissOneTimePassword">
            {{ dictionary.adminTemporaryPasswordDismiss }}
          </button>
        </section>
      </div>
    </template>
  </section>
</template>
