package com.brand.agentpoc.service.analytics;

import java.util.List;

public class AnalyticsReportComposer {

    private final ReportRenderer reportRenderer;

    public AnalyticsReportComposer(ReportRenderer reportRenderer) {
        this.reportRenderer = reportRenderer;
    }

    public record SummaryContext(
            String scenarioLabel,
            int totalUnits,
            String primaryMetricLabel,
            String primaryValue,
            String primaryBenchmark,
            String bestUnitLabel,
            String bestUnitValue,
            String worstUnitLabel,
            String worstUnitValue
    ) {}

    public String enrichedReply(
            String language,
            String conclusion,
            List<String[]> dataRows,
            SummaryContext summaryContext,
            String mermaid,
            String fallback,
            List<String> attributions,
            List<String> recommendations,
            List<String> followUps
    ) {
        boolean isZh = "zh".equals(language);

        StringBuilder body = new StringBuilder();

        body.append(isZh ? "## 核心结论\n\n" : "## Conclusion\n\n");
        body.append(reportRenderer.escapeHtml(conclusion)).append("\n");

        body.append(isZh ? "## 数据支撑\n\n" : "## Data Support\n\n");
        body.append(reportRenderer.htmlTable(dataRows, language)).append("\n");
        if (mermaid != null && !mermaid.isBlank()) {
            body.append("\n").append(mermaid).append("\n");
        }
        if (fallback != null && !fallback.isBlank()) {
            body.append("\n").append(fallback).append("\n");
        }

        body.append(isZh ? "## 经营分析\n\n" : "## Short Analysis\n\n");

        if (!attributions.isEmpty()) {
            body.append(isZh ? "**数据归因：**\n\n" : "**Data Attribution:**\n\n");
            for (String attr : attributions) {
                body.append("- ").append(reportRenderer.escapeHtml(attr)).append("\n");
            }
            body.append("\n");
        }
        if (!recommendations.isEmpty()) {
            body.append(isZh ? "**可执行建议：**\n\n" : "**Recommendations:**\n\n");
            for (String rec : recommendations) {
                body.append("- ").append(reportRenderer.escapeHtml(rec)).append("\n");
            }
            body.append("\n");
        }

        body.append(isZh ? "## 问题诊断与解决\n\n" : "## Problem Diagnosis\n\n");
        int totalUnits = summaryContext != null ? summaryContext.totalUnits() : 0;
        String worstLabel = summaryContext != null ? summaryContext.worstUnitLabel() : null;
        String worstValue = summaryContext != null ? summaryContext.worstUnitValue() : null;
        String primaryMetric = summaryContext != null ? summaryContext.primaryMetricLabel() : "";
        double primaryRate = summaryContext != null ? parsePrimaryRate(summaryContext.primaryValue()) : 0;

        if (isZh) {
            if (totalUnits == 0) {
                body.append("- 当前范围内无匹配数据，无法进行诊断分析。建议扩大查询范围或检查数据导入状态。\n\n");
            } else if (primaryRate > 0 && worstLabel != null && !worstLabel.isEmpty()) {
                body.append(String.format("- **主要差距**：%s 的%s仅为 **%s**，低于 80%% 基准线。\n",
                        worstLabel, primaryMetric, worstValue));
                body.append("  - 根因分析：该指标未达标可能与线索质量、跟进效率或市场环境有关。\n");
                body.append(String.format("  - 解决动作：建议对 %s 进行专项复盘，优化资源配置，并在本月底前完成一轮辅导。\n\n", worstLabel));
            } else if (primaryRate >= 80.0) {
                body.append(String.format("- 当前%s表现良好（%.1f%%），整体稳定。下一阶段应关注相对薄弱的维度，防范新的短板出现。\n\n",
                        primaryMetric, primaryRate));
            } else {
                body.append("- 当前数据量不足以进行细粒度诊断，建议扩大查询范围。\n\n");
            }
        } else {
            if (totalUnits == 0) {
                body.append("- No matching data in the current scope; unable to perform diagnostic analysis. Consider broadening the scope or checking data import status.\n\n");
            } else if (primaryRate > 0 && worstLabel != null && !worstLabel.isEmpty()) {
                body.append(String.format("- **Main gap**: %s has %s of only **%s**, below the 80%% baseline.\n",
                        worstLabel, primaryMetric, worstValue));
                body.append("  - Root cause: This underperformance may relate to lead quality, follow-up efficiency, or market conditions.\n");
                body.append(String.format("  - Action: Conduct a targeted review for %s, optimize resource allocation, and complete a coaching round by month-end.\n\n", worstLabel));
            } else if (primaryRate >= 80.0) {
                body.append(String.format("- Current %s is performing well (%.1f%%), overall stable. Focus on relatively weaker dimensions to prevent new gaps.\n\n",
                        primaryMetric, primaryRate));
            } else {
                body.append("- Insufficient data for granular diagnosis; consider broadening the scope.\n\n");
            }
        }

        body.append(isZh ? "## 改进建议\n\n" : "## Improvement Suggestions\n\n");
        if (isZh) {
            if (primaryRate >= 80.0) {
                body.append("- **扩大优势**：总结表现最佳的门店/活动的成功经验，形成标准化操作手册。\n");
                body.append("- **横向复制**：将高效做法推广至其他门店/车型，下季度在集团内组织经验分享会。\n\n");
            } else if (totalUnits > 0) {
                body.append("- **本月目标**：针对达成率最低的单位制定每周跟进计划，月底前提升 10-15 个百分点。\n");
                body.append("- **下季度目标**：建立月度复盘机制，每单位每月至少一次经营分析会。\n");
                body.append("- **年度目标**：集团整体达成率 ≥ 85%，对连续两季度不达标的单位启动专项帮扶。\n\n");
            } else {
                body.append("- 当前数据不足，无法生成有针对性的改进建议。\n\n");
            }
        } else {
            if (primaryRate >= 80.0) {
                body.append("- **Expand strengths**: Document the best-performing unit/campaign success patterns into a standardized playbook.\n");
                body.append("- **Lateral replication**: Promote effective practices to other stores/models, organize quarterly experience-sharing sessions.\n\n");
            } else if (totalUnits > 0) {
                body.append("- **This month**: Create a weekly follow-up plan for the lowest-performing unit, targeting a 10-15 pp improvement by month-end.\n");
                body.append("- **Next quarter**: Establish a monthly review cadence with at least one operations review per unit per month.\n");
                body.append("- **Annual**: Overall group achievement rate >= 85%, launch targeted support for units underperforming for two consecutive quarters.\n\n");
            } else {
                body.append("- Insufficient data to generate targeted improvement suggestions.\n\n");
            }
        }

        body.append(isZh ? "追问：\n\n" : "FOLLOW_UP_QUESTIONS:\n\n");
        for (int i = 0; i < followUps.size(); i++) {
            body.append(i + 1).append(". ").append(reportRenderer.escapeHtml(followUps.get(i))).append("\n");
        }

        return body.toString();
    }

    private static double parsePrimaryRate(String primaryValue) {
        if (primaryValue == null || primaryValue.isBlank()) {
            return 0;
        }
        try {
            String cleaned = primaryValue.replace("%", "").replace(",", ".").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
