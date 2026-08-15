import { computed, ref } from "vue";
import {
  addReportCollaborationComment,
  changeReportCollaborationAssignee,
  changeReportCollaborationStatus,
  getReportCollaboration,
  listReportCollaborationAssignees,
  listReportCollaborations
} from "../api/reportCollaboration";

export function useReportCollaboration({ currentUser, dictionary, onAuthExpired }) {
  const reports = ref([]);
  const selected = ref(null);
  const assignees = ref([]);
  const filters = ref(emptyFilters());
  const loading = ref(false);
  const detailLoading = ref(false);
  const loadError = ref("");
  const operationError = ref("");
  const successMessage = ref("");
  const pendingAction = ref("");
  const conflictVersion = ref(null);

  const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
  const canCollaborate = computed(() => permissions.value.has("REPORT_COLLABORATE"));
  const hasConflict = computed(() => conflictVersion.value !== null);

  async function initialize() {
    loading.value = true;
    loadError.value = "";
    try {
      await loadList({ preserveSelection: true });
    } catch (error) {
      loadError.value = errorMessage(error);
      handleExpiredSession(error);
    } finally {
      loading.value = false;
    }
  }

  async function applyFilters() {
    loading.value = true;
    loadError.value = "";
    try {
      await loadList({ preserveSelection: false });
    } catch (error) {
      loadError.value = errorMessage(error);
      handleExpiredSession(error);
    } finally {
      loading.value = false;
    }
  }

  async function clearFilters() {
    filters.value = emptyFilters();
    await applyFilters();
  }

  async function loadList({ preserveSelection }) {
    const previousId = preserveSelection ? selected.value?.report?.reportId : null;
    reports.value = normalizeList(await listReportCollaborations(apiFilters(filters.value)));
    const next = reports.value.find((report) => report.reportId === previousId) ?? reports.value[0] ?? null;
    if (next) {
      await selectReport(next.reportId);
    } else {
      selected.value = null;
      assignees.value = [];
    }
  }

  async function selectReport(reportId) {
    if (!reportId || detailLoading.value) {
      return;
    }
    detailLoading.value = true;
    operationError.value = "";
    successMessage.value = "";
    try {
      const [detail, candidates] = await Promise.all([
        getReportCollaboration(reportId),
        listReportCollaborationAssignees(reportId)
      ]);
      selected.value = normalizeDetail(detail);
      assignees.value = normalizeList(candidates);
      conflictVersion.value = null;
      replaceSummary(selected.value?.report);
    } catch (error) {
      operationError.value = errorMessage(error);
      handleExpiredSession(error);
    } finally {
      detailLoading.value = false;
    }
  }

  async function changeStatus(status) {
    return perform("status", () => changeReportCollaborationStatus(
      selected.value.report.reportId,
      status,
      selected.value.report.version
    ), dictionary.value.collaborationStatusUpdated);
  }

  async function changeAssignee(assigneeUserId) {
    return perform("assignee", () => changeReportCollaborationAssignee(
      selected.value.report.reportId,
      assigneeUserId || null,
      selected.value.report.version
    ), dictionary.value.collaborationAssigneeUpdated);
  }

  async function addComment(body) {
    return perform("comment", () => addReportCollaborationComment(
      selected.value.report.reportId,
      body,
      selected.value.report.version
    ), dictionary.value.collaborationCommentAdded);
  }

  async function reloadConflict() {
    const reportId = selected.value?.report?.reportId;
    if (reportId) {
      await selectReport(reportId);
    } else {
      await initialize();
    }
  }

  async function perform(action, operation, message) {
    if (!canCollaborate.value || !selected.value?.report || pendingAction.value) {
      return null;
    }
    pendingAction.value = action;
    operationError.value = "";
    successMessage.value = "";
    conflictVersion.value = null;
    try {
      const detail = normalizeDetail(await operation());
      if (!detail) {
        return null;
      }
      selected.value = detail;
      replaceSummary(detail.report);
      successMessage.value = message;
      return detail;
    } catch (error) {
      const currentVersion = readConflictVersion(error?.body);
      if (error?.status === 409 && currentVersion !== null) {
        conflictVersion.value = currentVersion;
      }
      operationError.value = errorMessage(error);
      handleExpiredSession(error);
      return null;
    } finally {
      pendingAction.value = "";
    }
  }

  function replaceSummary(summary) {
    if (!summary?.reportId) {
      return;
    }
    reports.value = reports.value.map((report) =>
      report.reportId === summary.reportId ? summary : report);
  }

  function handleExpiredSession(error) {
    if (error?.status === 401) {
      onAuthExpired?.();
    }
  }

  function errorMessage(error) {
    if (error?.status === 403) {
      return dictionary.value.collaborationForbiddenError;
    }
    if (error?.status === 409) {
      return readConflictVersion(error?.body) !== null
        ? dictionary.value.collaborationConflictError
        : dictionary.value.collaborationStateConflictError;
    }
    if (error?.status === 400) {
      return dictionary.value.collaborationValidationError;
    }
    return dictionary.value.collaborationRequestError;
  }

  return {
    addComment,
    applyFilters,
    assignees,
    canCollaborate,
    changeAssignee,
    changeStatus,
    clearFilters,
    conflictVersion,
    detailLoading,
    filters,
    hasConflict,
    initialize,
    loadError,
    loading,
    operationError,
    pendingAction,
    reloadConflict,
    reports,
    selectReport,
    selected,
    successMessage
  };
}

function emptyFilters() {
  return {
    status: "",
    assigneeUserId: "",
    organizationId: "",
    generatedFrom: "",
    generatedTo: ""
  };
}

function apiFilters(filters) {
  return {
    status: filters.status,
    assigneeUserId: positiveNumber(filters.assigneeUserId),
    organizationId: positiveNumber(filters.organizationId),
    generatedFrom: dateBoundary(filters.generatedFrom, false),
    generatedTo: dateBoundary(filters.generatedTo, true)
  };
}

function positiveNumber(value) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : "";
}

function dateBoundary(value, endOfDay) {
  if (!value) {
    return "";
  }
  return `${value}T${endOfDay ? "23:59:59.999" : "00:00:00.000"}Z`;
}

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeDetail(value) {
  if (!value || typeof value !== "object" || Array.isArray(value) || !value.report?.reportId) {
    return null;
  }
  return {
    ...value,
    timeline: normalizeList(value.timeline)
  };
}

function readConflictVersion(body) {
  try {
    const parsed = typeof body === "string" ? JSON.parse(body) : body;
    const version = Number(parsed?.data?.currentVersion);
    return Number.isInteger(version) && version >= 0 ? version : null;
  } catch {
    return null;
  }
}
