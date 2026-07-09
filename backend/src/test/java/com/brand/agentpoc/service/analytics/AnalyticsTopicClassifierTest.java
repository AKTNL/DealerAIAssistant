package com.brand.agentpoc.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.service.analytics.AnalyticsTopicClassifier.AnalysisTopic;
import org.junit.jupiter.api.Test;

class AnalyticsTopicClassifierTest {

    private final AnalyticsTopicClassifier classifier = new AnalyticsTopicClassifier();

    @Test
    void routesMultiEntityCountToDataOverviewBeforeCampaign() {
        assertThat(classifier.detect(
                "\u6837\u672c\u6570\u636e\u91cc\u4e00\u5171\u6709\u591a\u5c11\u6761\u5546\u673a\u3001\u7ebf\u7d22\u3001\u4efb\u52a1\u548c\u5e02\u573a\u6d3b\u52a8\uff1f"
        )).isEqualTo(AnalysisTopic.DATA_OVERVIEW);
    }

    @Test
    void routesOpportunitySourceQuestionsBeforeLeadSource() {
        assertThat(classifier.detect("\u5546\u673a\u6765\u6e90\u7684\u8f6c\u5316\u7387\u600e\u4e48\u6837\uff1f"))
                .isEqualTo(AnalysisTopic.OPPORTUNITY_FUNNEL);
    }

    @Test
    void routesTopSalesVolumeQuestionsToTargetAchievement() {
        assertThat(classifier.detect("which dealer has the most sold model"))
                .isEqualTo(AnalysisTopic.TARGET_ACHIEVEMENT);
    }

    @Test
    void routesBusinessActivityBeforeGenericFollowUpActivity() {
        assertThat(classifier.detect("business activity ranking for dormant dealer"))
                .isEqualTo(AnalysisTopic.DEALER_BUSINESS_ACTIVITY);
    }
}
