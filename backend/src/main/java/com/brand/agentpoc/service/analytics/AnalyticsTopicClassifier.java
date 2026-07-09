package com.brand.agentpoc.service.analytics;

import java.util.Locale;
import java.util.stream.Stream;

public final class AnalyticsTopicClassifier {

    public AnalysisTopic detect(String message) {
        String normalized = normalize(message);

        if (containsAny(normalized, "\u6570\u636e\u6982\u51b5")
                || (containsAny(normalized, "\u5f53\u524d\u7cfb\u7edf") && containsAny(normalized, "\u4e00\u5171\u6709\u591a\u5c11"))
                || (containsAny(normalized, "\u4e00\u5171\u6709\u591a\u5c11", "\u591a\u5c11\u6761", "\u5206\u522b\u6709\u591a\u5c11", "\u6709\u591a\u5c11")
                        && countMentionedOverviewEntities(normalized) >= 2)) {
            return AnalysisTopic.DATA_OVERVIEW;
        }
        if (containsAny(normalized, "\u6d3b\u52a8", "campaign", "marketing", "event")) {
            return AnalysisTopic.CAMPAIGN_PERFORMANCE;
        }
        if (containsAny(normalized, "\u7ecf\u8425\u6d3b\u8dc3\u5ea6", "\u95e8\u5e97\u6d3b\u8dc3", "\u4f11\u7720\u95e8\u5e97",
                "\u7ecf\u8425\u6d3b\u8dc3", "\u6d3b\u8dc3\u5ea6\u6392\u540d", "\u4f11\u7720", "dormant",
                "business activity", "dormant dealer")) {
            return AnalysisTopic.DEALER_BUSINESS_ACTIVITY;
        }
        if (containsAny(normalized, "\u8ddf\u8fdb", "\u4efb\u52a1", "\u6d3b\u8dc3\u5ea6", "task", "follow-up", "follow up")) {
            return AnalysisTopic.SALES_FOLLOW_UP;
        }
        if (containsAny(normalized, "\u5546\u673a", "opportunity")
                && containsAny(normalized, "\u6765\u6e90", "\u8d62\u5355\u7387", "\u8d62\u5355", "\u6210\u4ea4\u7387", "\u6210\u4ea4", "\u6f0f\u6597", "\u8f6c\u5316", "\u5e74\u9f84", "\u8d2d\u8f66\u5468\u671f", "\u533a\u95f4", "\u9636\u6bb5")) {
            return AnalysisTopic.OPPORTUNITY_FUNNEL;
        }
        if (containsAny(normalized, "\u7ebf\u7d22", "\u6d41\u91cf", "lead", "source", "organic", "trend")) {
            return AnalysisTopic.LEAD_SOURCE;
        }
        if (DirectQuestionMatcher.asksTopSalesVolume(normalized)
                && (DirectQuestionMatcher.mentionsProductDimension(normalized) || DirectQuestionMatcher.mentionsDealerDimension(normalized)
                        || containsAny(normalized, "\u8c01", "\u54ea\u4e2a", "\u54ea\u5bb6"))) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (DirectQuestionMatcher.asksTopSalesVolume(normalized)) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (containsAny(normalized, "\u5bf9\u6807", "\u6bd4\u8f83", "\u8868\u73b0\u6700\u597d", "outperform", "benchmark", "compare", "best")) {
            return AnalysisTopic.DEALER_BENCHMARK;
        }
        if (containsAny(normalized, "\u8d62\u5355\u6570\u91cf", "\u8d62\u5355\u6700\u591a", "\u8d62\u5355\u6570")) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (containsAny(normalized, "\u76ee\u6807", "\u8fbe\u6210", "\u5b8c\u6210\u7387", "achievement", "target")) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (containsAny(normalized, "\u6f0f\u6597", "\u5546\u673a", "\u8f6c\u5316", "\u8d62\u5355\u7387", "\u6210\u4ea4\u7387", "\u8d2d\u8f66\u5468\u671f", "\u5e74\u9f84", "\u533a\u95f4", "opportunity", "funnel", "conversion", "stage")) {
            return AnalysisTopic.OPPORTUNITY_FUNNEL;
        }
        return AnalysisTopic.DEALER_BENCHMARK;
    }

    private int countMentionedOverviewEntities(String normalized) {
        int count = 0;
        if (containsAny(normalized, "\u5546\u673a", "opportunity", "opportunities")) {
            count++;
        }
        if (containsAny(normalized, "\u7ebf\u7d22", "lead", "leads")) {
            count++;
        }
        if (containsAny(normalized, "\u4efb\u52a1", "task", "tasks")) {
            count++;
        }
        if (containsAny(normalized, "\u5e02\u573a\u6d3b\u52a8", "\u6d3b\u52a8", "campaign", "campaigns")) {
            count++;
        }
        return count;
    }

    private boolean containsAny(String source, String... keywords) {
        return Stream.of(keywords).anyMatch(source::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public enum AnalysisTopic {
        TARGET_ACHIEVEMENT,
        OPPORTUNITY_FUNNEL,
        SALES_FOLLOW_UP,
        CAMPAIGN_PERFORMANCE,
        LEAD_SOURCE,
        DEALER_BENCHMARK,
        DEALER_BUSINESS_ACTIVITY,
        DATA_OVERVIEW
    }
}
