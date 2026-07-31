package com.brand.agentpoc.dto.response;

import java.util.List;

public record DashboardSummary(
        DataStatus dataStatus,
        Overview overview,
        TargetAchievement targetAchievement,
        OpportunityFunnel opportunityFunnel,
        LeadSources leadSources,
        FollowUpTasks followUpTasks,
        CampaignEffect campaignEffect
) {

    public record DataStatus(
            String source,
            boolean fallbackActive,
            boolean simulatedData,
            boolean lowConfidence,
            String message,
            ImportDataStatus.Batch batch,
            int processedRows,
            int importedRows,
            int skippedRows,
            int issueCount,
            List<String> issueSummaries
    ) {
    }

    public record Overview(
            int dealerCount,
            int totalTarget,
            int totalWon,
            int comparableWon,
            int totalOpportunities,
            int wonOpportunities,
            int totalLeads,
            int convertedLeads,
            int totalTasks,
            int overdueTasks,
            int totalCampaigns,
            int campaignActualOpportunities,
            double targetAchievementRate,
            double opportunityWinRate,
            double leadConversionRate,
            double taskCompletionRate,
            double taskOverdueRate,
            double campaignAttainmentRate
    ) {
    }

    public record TargetAchievement(
            int comparableTarget,
            int comparableWon,
            List<DealerAchievement> lowDealers,
            List<RegionAchievement> regions
    ) {
    }

    public record DealerAchievement(
            String dealerCode,
            String dealerName,
            String region,
            double achievementRate,
            int wonCount,
            int targetCount
    ) {
    }

    public record RegionAchievement(
            String region,
            double achievementRate,
            int wonCount,
            int targetCount
    ) {
    }

    public record OpportunityFunnel(
            int totalOpportunities,
            int wonCount,
            int lostCount,
            int openCount,
            double winRate,
            List<DistributionMetric> stages
    ) {
    }

    public record DistributionMetric(
            String label,
            long count,
            double shareRate
    ) {
    }

    public record LeadSources(
            int totalLeads,
            int convertedCount,
            double conversionRate,
            List<LeadSourceMetric> sources
    ) {
    }

    public record LeadSourceMetric(
            String source,
            long totalCount,
            long convertedCount,
            double conversionRate,
            double shareRate
    ) {
    }

    public record FollowUpTasks(
            int totalTasks,
            int completedCount,
            int openCount,
            int overdueCount,
            double completionRate,
            double overdueRate,
            List<TaskBacklog> backlogDealers
    ) {
    }

    public record TaskBacklog(
            String dealerCode,
            String dealerName,
            int openCount,
            int overdueCount,
            int totalBacklog
    ) {
    }

    public record CampaignEffect(
            int totalCampaigns,
            int actualOpportunities,
            int targetOpportunities,
            int comparableActualOpportunities,
            int comparableTargetOpportunities,
            double attainmentRate,
            List<CampaignOutcome> lowPerformingCampaigns
    ) {
    }

    public record CampaignOutcome(
            String campaignId,
            String campaignName,
            String dealerName,
            double attainmentRate,
            int actualOpportunities,
            int targetOpportunities
    ) {
    }
}
