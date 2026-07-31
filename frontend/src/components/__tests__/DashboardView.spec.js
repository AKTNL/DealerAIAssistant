import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DashboardView from "../dashboard/DashboardView.vue";
import { messages } from "../../i18n/messages";

function summary() {
  return {
    dataStatus: {
      source: "configured-workbook",
      simulatedData: true,
      lowConfidence: true,
      batch: { id: "batch-1" },
      importedRows: 10,
      skippedRows: 1,
      issueCount: 1
    },
    overview: {
      comparableWon: 75,
      totalTarget: 100,
      targetAchievementRate: 75,
      totalOpportunities: 12,
      wonOpportunities: 4,
      totalLeads: 8,
      leadConversionRate: 25,
      totalTasks: 7,
      taskOverdueRate: 14.3,
      totalCampaigns: 3,
      campaignAttainmentRate: 60
    },
    targetAchievement: {
      lowDealers: [
        { dealerCode: "D001", dealerName: "Store A", region: "Beijing", achievementRate: 35, wonCount: 35, targetCount: 100 }
      ],
      regions: [
        { region: "Beijing", achievementRate: 45, wonCount: 45, targetCount: 100 }
      ]
    },
    opportunityFunnel: {
      stages: [
        { label: "Closed Won", count: 4, shareRate: 33.3 }
      ]
    },
    leadSources: {
      sources: [
        { source: "Website", convertedCount: 2, totalCount: 8, conversionRate: 25 }
      ]
    },
    followUpTasks: {
      backlogDealers: [
        { dealerCode: "D001", dealerName: "Store A", openCount: 2, overdueCount: 1, totalBacklog: 3 }
      ]
    },
    campaignEffect: {
      lowPerformingCampaigns: [
        { campaignId: "C001", campaignName: "Launch", dealerName: "Store A", attainmentRate: 20, actualOpportunities: 2, targetOpportunities: 10 }
      ]
    }
  };
}

function mountDashboard(props = {}) {
  return mount(DashboardView, {
    props: {
      dashboard: summary(),
      dictionary: messages.en,
      error: "",
      loading: false,
      locale: "en",
      ...props
    }
  });
}

describe("DashboardView", () => {
  it("renders KPI cards and emits analysis prompts", async () => {
    const wrapper = mountDashboard();

    expect(wrapper.text()).toContain("Operations Dashboard");
    expect(wrapper.findAll(".dashboard-kpi-card")).toHaveLength(5);
    expect(wrapper.text()).toContain("75.0%");
    expect(wrapper.text()).toContain("batch-1");

    await wrapper.find(".dashboard-card-action").trigger("click");

    expect(wrapper.emitted("analyze")?.[0]).toEqual([
      "Which dealers have the lowest target achievement rate?"
    ]);
  });

  it("renders loading and error states", () => {
    expect(mountDashboard({ loading: true }).findAll(".dashboard-skeleton")).toHaveLength(6);

    const errorWrapper = mountDashboard({ dashboard: null, error: "Backend unavailable" });
    expect(errorWrapper.text()).toContain("Unable to load dashboard");
    expect(errorWrapper.text()).toContain("Backend unavailable");
  });

  it("uses localized compact metric separators", () => {
    const wrapper = mountDashboard({
      dictionary: {
        ...messages.en,
        dashboardMetricSeparator: "|",
        dashboardRatioSeparator: "of"
      }
    });

    expect(wrapper.text()).toContain("Beijing | 35 of 100");
    expect(wrapper.text()).toContain("2 of 8");
    expect(wrapper.text()).toContain("Open 2 | Overdue 1");
    expect(wrapper.text()).toContain("Store A | 2 of 10");
  });
});
