package com.brand.agentpoc.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class DirectQuestionMatcherTest {

    @Test
    void detectsDirectTargetQuestionsFromSalesAndRateWording() {
        assertThat(DirectQuestionMatcher.isDirectTargetQuestion(normalize("全国范围内哪个车型卖得最好？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.isDirectTargetQuestion(normalize("2026年目标达成率最低的经销商有哪些？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.mentionsProductDimension(normalize("哪个车型销量最高？")))
                .isTrue();
    }

    @Test
    void detectsOpportunityBreakdownAndWinRateQuestions() {
        assertThat(DirectQuestionMatcher.isDirectOpportunityQuestion(normalize("商机按阶段分别有多少？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.asksOpportunitySourceBreakdown(normalize("商机来源赢单率怎么分布？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.asksWinRate(normalize("Which source has the highest win rate?")))
                .isTrue();
    }

    @Test
    void detectsLeadTaskAndCampaignDirectQuestions() {
        assertThat(DirectQuestionMatcher.isDirectLeadQuestion(
                normalize("\u54ea\u79cd\u6e20\u9053\u5e26\u6765\u7684\u7ebf\u7d22\u8f6c\u5316\u7387\u6700\u9ad8\uff1f")))
                .isTrue();
        assertThat(DirectQuestionMatcher.isDirectLeadQuestion(normalize("线索状态分布如何？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.isDirectTaskQuestion(normalize("任务类型前三是什么？")))
                .isTrue();
        assertThat(DirectQuestionMatcher.isDirectCampaignQuestion(normalize("市场活动一共有多少个？")))
                .isTrue();
    }

    @Test
    void resolvesRequestedTopLimitFromChineseAndEnglishPhrases() {
        assertThat(DirectQuestionMatcher.requestedTopLimit(normalize("任务类型前三是什么？"), 5))
                .isEqualTo(3);
        assertThat(DirectQuestionMatcher.requestedTopLimit(normalize("show top 5 task subjects"), 3))
                .isEqualTo(5);
        assertThat(DirectQuestionMatcher.requestedTopLimit(normalize("show task subjects"), 7))
                .isEqualTo(7);
    }

    @Test
    void avoidsClassifyingUnrelatedMessagesAsDirectQuestions() {
        String normalized = normalize("hello, who are you?");

        assertThat(DirectQuestionMatcher.isDirectTargetQuestion(normalized)).isFalse();
        assertThat(DirectQuestionMatcher.isDirectOpportunityQuestion(normalized)).isFalse();
        assertThat(DirectQuestionMatcher.isDirectLeadQuestion(normalized)).isFalse();
        assertThat(DirectQuestionMatcher.isDirectTaskQuestion(normalized)).isFalse();
        assertThat(DirectQuestionMatcher.isDirectCampaignQuestion(normalized)).isFalse();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
