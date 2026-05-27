package com.brand.agentpoc.dto.metrics;

public record TaskMetrics(
        int totalTasks,
        int completedCount,
        int openCount,
        int overdueCount,
        double completionRate,
        BacklogDealer highestBacklogDealer
) {
    public record BacklogDealer(String dealerCode, String dealerName, int openCount, int overdueCount) {}
}
