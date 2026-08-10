package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.reporting.domain.ReportType;
import java.util.List;

public class ReportMarkdownRenderer {

    public String render(
            ReportType reportType,
            String language,
            DashboardSummary summary,
            String topic,
            String batchId
    ) {
        boolean zh = "zh".equals(language);
        DashboardSummary.Overview overview = summary.overview();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(renderTitle(reportType, language)).append("\n\n");
        if (reportType == ReportType.TOPIC && topic != null && !topic.isBlank()) {
            markdown.append(zh ? "\u4e3b\u9898\uff1a" : "Topic: ").append(topic.trim()).append("\n\n");
        }

        markdown.append(zh ? "## \u62a5\u544a\u8303\u56f4\n" : "## Scope\n")
                .append(zh ? "- \u6570\u636e\u6279\u6b21\uff1a" : "- Import batch: ").append(batchId).append("\n")
                .append(zh ? "- \u6570\u636e\u72b6\u6001\uff1a" : "- Data status: ")
                .append(dataStatus(summary.dataStatus(), zh)).append("\n\n");

        markdown.append(zh ? "## \u6838\u5fc3\u7ed3\u8bba\n" : "## Key Findings\n")
                .append(bullet(zh, "\u76ee\u6807\u8fbe\u6210\u7387", "Target achievement", formatPercent(overview.targetAchievementRate())))
                .append(bullet(zh, "\u5546\u673a\u8d62\u5355\u7387", "Opportunity win rate", formatPercent(overview.opportunityWinRate())))
                .append(bullet(zh, "\u7ebf\u7d22\u8f6c\u5316\u7387", "Lead conversion rate", formatPercent(overview.leadConversionRate())))
                .append(bullet(zh, "\u903e\u671f\u8ddf\u8fdb\u4efb\u52a1", "Overdue follow-up tasks", String.valueOf(overview.overdueTasks())))
                .append("\n");

        markdown.append(zh ? "## \u6570\u636e\u652f\u6491\n" : "## Data Support\n")
                .append(zh ? "| \u6307\u6807 | \u6570\u503c |\n| --- | ---: |\n" : "| Metric | Value |\n| --- | ---: |\n")
                .append(row(zh, "\u7ecf\u9500\u5546\u6570", "Dealers", overview.dealerCount()))
                .append(row(zh, "\u76ee\u6807\u6570", "Targets", overview.totalTarget()))
                .append(row(zh, "\u8d62\u5355\u6570", "Won opportunities", overview.wonOpportunities()))
                .append(row(zh, "\u7ebf\u7d22\u6570", "Leads", overview.totalLeads()))
                .append(row(zh, "\u4efb\u52a1\u6570", "Tasks", overview.totalTasks()))
                .append(row(zh, "\u6d3b\u52a8\u6570", "Campaigns", overview.totalCampaigns()))
                .append("\n");

        markdown.append(zh ? "## \u98ce\u9669\u4e0e\u884c\u52a8\n" : "## Risks and Actions\n");
        appendRisks(markdown, summary, zh);
        markdown.append("\n");
        markdown.append(zh ? "## \u6570\u636e\u9650\u5236\n" : "## Data Limitations\n")
                .append(zh
                        ? "- \u672c\u62a5\u544a\u4ec5\u4f7f\u7528\u5f53\u524d active batch \u7684\u7ed3\u6784\u5316\u6307\u6807\u5feb\u7167\uff1b\u672a\u63d0\u4f9b\u7684\u8d8b\u52bf\u3001\u539f\u56e0\u6216\u7ec4\u7ec7\u6743\u9650\u4e0d\u4f1a\u88ab\u63a8\u65ad\u3002\n"
                        : "- This draft uses only the current active-batch structured metric snapshot; missing trends, causes, and organization scopes are not inferred.\n")
                .append(zh
                        ? "- \u62a5\u544a\u4e3a\u786e\u5b9a\u6027 Markdown \u8349\u7a3f\uff0c\u6a21\u578b\u6da6\u8272\u6216 PDF/Word \u5bfc\u51fa\u4e0d\u5728\u672c\u7248\u672c\u8303\u56f4\u5185\u3002\n"
                        : "- This is a deterministic Markdown draft; model rewriting and PDF/Word export are outside this version.\n");
        return markdown.toString().trim();
    }

    public String renderTitle(ReportType type, String language) {
        if ("zh".equals(language)) {
            return switch (type) {
                case DAILY -> "\u7ecf\u8425\u65e5\u62a5";
                case WEEKLY -> "\u7ecf\u8425\u5468\u62a5";
                case MONTHLY -> "\u7ecf\u8425\u6708\u62a5";
                case TOPIC -> "\u7ecf\u8425\u4e13\u9898\u62a5\u544a";
            };
        }
        return switch (type) {
            case DAILY -> "Dealer Operations Daily Report";
            case WEEKLY -> "Dealer Operations Weekly Report";
            case MONTHLY -> "Dealer Operations Monthly Report";
            case TOPIC -> "Dealer Operations Topic Report";
        };
    }

    private String dataStatus(DashboardSummary.DataStatus status, boolean zh) {
        if (status == null) {
            return zh ? "\u4e0d\u53ef\u7528" : "unavailable";
        }
        if (status.fallbackActive()) {
            return zh ? "\u6837\u4f8b\u6570\u636e\u56de\u9000" : "sample-data fallback";
        }
        return zh ? "\u5df2\u5bfc\u5165\u6570\u636e" : "imported data";
    }

    private String bullet(boolean zh, String zhLabel, String enLabel, String value) {
        return "- " + (zh ? zhLabel : enLabel) + ": " + value + "\n";
    }

    private String row(boolean zh, String zhLabel, String enLabel, int value) {
        return "| " + (zh ? zhLabel : enLabel) + " | " + value + " |\n";
    }

    private void appendRisks(StringBuilder markdown, DashboardSummary summary, boolean zh) {
        boolean hasRisk = false;
        List<DashboardSummary.DealerAchievement> lowDealers = summary.targetAchievement().lowDealers();
        if (lowDealers != null && !lowDealers.isEmpty()) {
            DashboardSummary.DealerAchievement lowest = lowDealers.getFirst();
            markdown.append(zh
                    ? "- \u4f18\u5148\u5173\u6ce8\u76ee\u6807\u8fbe\u6210\u7387\u6700\u4f4e\u7684\u95e8\u5e97\uff1a"
                    : "- Prioritize the lowest target-achievement dealer: ")
                    .append(lowest.dealerName()).append(" (").append(formatPercent(lowest.achievementRate())).append(")\n");
            hasRisk = true;
        }
        if (summary.followUpTasks().overdueCount() > 0) {
            markdown.append(zh ? "- \u6e05\u7406\u903e\u671f\u8ddf\u8fdb\u4efb\u52a1\uff1a" : "- Clear overdue follow-up tasks: ")
                    .append(summary.followUpTasks().overdueCount()).append("\n");
            hasRisk = true;
        }
        if (summary.campaignEffect().lowPerformingCampaigns() != null
                && !summary.campaignEffect().lowPerformingCampaigns().isEmpty()) {
            markdown.append(zh ? "- \u590d\u76d8\u4f4e\u4ea7\u51fa\u6d3b\u52a8\uff1a" : "- Review underperforming campaigns: ")
                    .append(summary.campaignEffect().lowPerformingCampaigns().getFirst().campaignName())
                    .append("\n");
            hasRisk = true;
        }
        if (!hasRisk) {
            markdown.append(zh
                    ? "- \u5f53\u524d\u6458\u8981\u672a\u53d1\u73b0\u9700\u8981\u5347\u7ea7\u7684\u660e\u786e\u5f02\u5e38\u3002\n"
                    : "- No explicit exception requires escalation in this snapshot.\n");
        }
    }

    private String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }
}
