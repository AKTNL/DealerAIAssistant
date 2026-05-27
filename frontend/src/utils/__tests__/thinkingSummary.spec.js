import { describe, expect, test } from "vitest";
import { buildThinkingSummary } from "../thinkingSummary";

describe("buildThinkingSummary", () => {
  test("builds a compact evidence trail from structured step metadata", () => {
    const summary = buildThinkingSummary([
      {
        type: "data_load",
        label: "Load targets",
        detail: "Loaded target records",
        meta: {
          source_type: "excel",
          file_name: "performance_2026.xlsx",
          sheet: "Target",
          recordCount: 248
        }
      },
      {
        type: "filter",
        label: "Filter by scope",
        detail: "Filtered by city and month",
        meta: {
          city: "Beijing",
          month: "2026-05",
          inputCount: 248,
          outputCount: 24
        }
      },
      {
        type: "calculation",
        label: "Aggregate targets",
        detail: "Computed dealer achievement rates",
        meta: {
          formula: "achievementRate = wonCount / targetValue",
          totalWonCount: 13,
          totalTargetValue: 20,
          sampleRows: [{ dealer: "D001" }, { dealer: "D005" }]
        }
      },
      {
        type: "insight",
        label: "Analysis insight",
        detail: "D005 has the lowest achievement rate at 68.0%."
      }
    ], "en");

    expect(summary.map(card => card.label)).toEqual([
      "Data source",
      "Scope",
      "Calculation",
      "Conclusion"
    ]);
    expect(summary[0]).toMatchObject({
      value: "248 records"
    });
    expect(summary[0].detail).toContain("performance_2026.xlsx");
    expect(summary[0].detail).toContain("Target");
    expect(summary[1]).toMatchObject({
      value: "Beijing · 2026-05",
      detail: "248 -> 24 matched"
    });
    expect(summary[2]).toMatchObject({
      value: "achievementRate = wonCount / targetValue"
    });
    expect(summary[2].detail).toContain("2 sample rows");
    expect(summary[2].detail).toContain("totalWonCount=13");
    expect(summary[2].detail).toContain("totalTargetValue=20");
    expect(summary[3]).toMatchObject({
      value: "D005 has the lowest achievement rate at 68.0%."
    });
  });

  test("falls back to model reasoning when no data audit steps exist", () => {
    const summary = buildThinkingSummary([
      {
        type: "model_thought",
        label: "Reasoning",
        detail: "I checked the user's scope, compared the available rows, and then prepared the answer."
      }
    ], "en");

    expect(summary).toHaveLength(1);
    expect(summary[0]).toMatchObject({
      label: "Reasoning",
      value: "I checked the user's scope, compared the available rows, and then prepared the answer."
    });
  });

  test("summarizes campaign and lead business metrics from calculation metadata", () => {
    const campaignSummary = buildThinkingSummary([
      {
        type: "calculation",
        label: "Group by campaign type",
        detail: "2 campaigns",
        meta: {
          formula: "attainmentRate = actualOpportunityCount / totalNewCustomerTarget",
          campaignCount: 2,
          totalActualOpportunityCount: 13,
          totalNewCustomerTarget: 20,
          sampleRows: [{ campaignId: "C1" }, { campaignId: "C2" }]
        }
      }
    ], "en");

    expect(campaignSummary).toHaveLength(1);
    expect(campaignSummary[0]).toMatchObject({
      label: "Calculation",
      value: "attainmentRate = actualOpportunityCount / totalNewCustomerTarget"
    });
    expect(campaignSummary[0].detail).toContain("2 sample rows");
    expect(campaignSummary[0].detail).toContain("campaignCount=2");
    expect(campaignSummary[0].detail).toContain("totalActualOpportunityCount=13");
    expect(campaignSummary[0].detail).toContain("totalNewCustomerTarget=20");

    const leadSummary = buildThinkingSummary([
      {
        type: "calculation",
        label: "Group by lead source",
        detail: "3 sources, 4 leads",
        meta: {
          formula: "conversionRate = convertedCount / leadCount",
          sourceCount: 3,
          leadCount: 4,
          convertedCount: 2,
          sampleRows: [{ source: "Website" }, { source: "Referral" }]
        }
      }
    ], "en");

    expect(leadSummary).toHaveLength(1);
    expect(leadSummary[0]).toMatchObject({
      label: "Calculation",
      value: "conversionRate = convertedCount / leadCount"
    });
    expect(leadSummary[0].detail).toContain("2 sample rows");
    expect(leadSummary[0].detail).toContain("sourceCount=3");
    expect(leadSummary[0].detail).toContain("leadCount=4");
    expect(leadSummary[0].detail).toContain("convertedCount=2");
  });
});
