import { onMounted, ref } from "vue";
import { getDashboardSummary } from "../api/dashboard";

export function useDashboard({ onAuthExpired, enabled } = {}) {
  const dashboard = ref(null);
  const dashboardError = ref("");
  const dashboardLoading = ref(false);

  onMounted(() => {
    if (!enabled || enabled.value !== false) {
      loadDashboard();
    }
  });

  async function loadDashboard() {
    dashboardError.value = "";
    dashboardLoading.value = true;

    try {
      dashboard.value = await getDashboardSummary();
    } catch (error) {
      if (isAuthExpiredError(error)) {
        onAuthExpired?.();
        return;
      }

      dashboard.value = null;
      dashboardError.value = error?.message ?? "";
    } finally {
      dashboardLoading.value = false;
    }
  }

  return {
    dashboard,
    dashboardError,
    dashboardLoading,
    loadDashboard
  };
}

function isAuthExpiredError(error) {
  return error?.status === 401 || String(error?.message ?? "").toLowerCase().includes("login session expired");
}
