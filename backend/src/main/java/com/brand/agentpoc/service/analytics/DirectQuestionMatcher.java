package com.brand.agentpoc.service.analytics;

import java.util.stream.Stream;

public final class DirectQuestionMatcher {

    private DirectQuestionMatcher() {
    }

    public static boolean isDirectTargetQuestion(String normalized) {
        return containsAny(normalized,
                "整体新车目标达成",
                "全量目标达成情况",
                "全量数据中赢单数最多",
                "全量数据中目标达成率最高",
                "全量数据中目标达成率最低",
                "全量数据中哪个车型",
                "目标达成率较低的经销商",
                "目标达成率最低的经销商有哪些",
                "目标达成率最高的经销商是谁")
                || asksTopSalesVolume(normalized)
                || asksHighestRate(normalized)
                || asksLowestRate(normalized)
                || (containsAny(normalized, "2026年") && containsAny(normalized, "整体目标达成", "目标达成率最高", "目标完成率最高", "目标达成率最低", "目标完成率最低"));
    }

    public static boolean isDirectOpportunityQuestion(String normalized) {
        return containsAny(normalized,
                "当前商机一共有多少",
                "阶段分别有多少",
                "商机最多的经销商",
                "赢单商机最多",
                "战败商机最多",
                "高概率商机主要集中",
                "商机主要来自哪些线索来源",
                "商机主要来源于哪些线索来源",
                "线索来源的商机赢单率最高",
                "哪个车型商机数量最多",
                "哪个车型赢单率最高",
                "的商机漏斗如何",
                "购买周期最多集中",
                "购车周期年龄",
                "购车周期")
                || asksOpportunityStageBreakdown(normalized)
                || asksOpportunitySourceBreakdown(normalized)
                || (mentionsProductDimension(normalized) && (asksWinRate(normalized) || asksBreakdown(normalized)))
                || (containsAny(normalized, "商机") && asksTopSalesVolume(normalized))
                || (containsAny(normalized, "年龄", "购车周期") && containsAny(normalized, "区间", "集中"));
    }

    public static boolean isDirectLeadQuestion(String normalized) {
        return containsAny(normalized,
                "线索一共有多少",
                "状态分布如何",
                "线索来源最多的是哪个渠道",
                "哪个线索来源转化率最高",
                "线索的转化情况怎么样",
                "线索意向车型最多",
                "线索分配最多",
                "转化线索最多",
                "线索的转化情况怎么样")
                || (containsAny(normalized, "线索") && (asksStatusBreakdown(normalized) || asksBreakdown(normalized)))
                || (containsAny(normalized, "线索") && containsAny(normalized, "转化情况", "转化怎么样"))
                || (containsAny(normalized, "来源", "XY") && containsAny(normalized, "线索", "转化情况"))
                || (containsAny(normalized, "\u7ebf\u7d22")
                        && containsAny(normalized, "\u6e20\u9053")
                        && containsAny(normalized, "\u8f6c\u5316\u7387")
                        && containsAny(normalized, "\u6700\u9ad8"));
    }

    public static boolean isDirectTaskQuestion(String normalized) {
        return containsAny(normalized,
                "任务总体完成情况",
                "任务类型最多",
                "哪个经销商关联任务最多",
                "哪个经销商计划中任务最多",
                "任务完成率最低",
                "任务完成情况怎么样")
                || asksTaskSubjectBreakdown(normalized);
    }

    public static boolean isDirectCampaignQuestion(String normalized) {
        return containsAny(normalized,
                "市场活动一共有多少个",
                "活动最多的经销商",
                "市场活动总体商机目标完成情况",
                "活动产生商机最多的单个活动",
                "哪个经销商活动商机产出最多",
                "活动效果怎么样",
                "活动目标商机完成率为0");
    }

    public static boolean asksTopSalesVolume(String normalized) {
        return containsAny(normalized,
                "赢单数最多",
                "赢单数量最多",
                "赢单商机最多",
                "赢单最多",
                "成交商机最多",
                "成交最多",
                "成交量最多",
                "成交数量最多",
                "卖得最好",
                "卖得最多",
                "销量最高",
                "销量最好",
                "销量最多",
                "销售最好",
                "销售最多",
                "售出最多",
                "最畅销",
                "畅销",
                "won most",
                "most won",
                "best-selling",
                "best selling",
                "most sold");
    }

    public static boolean asksHighestRate(String normalized) {
        return containsAny(normalized,
                "达成率最高",
                "完成率最高",
                "目标达成率最高",
                "目标完成率最高",
                "最高达成率",
                "最高完成率",
                "achievement rate highest",
                "highest achievement",
                "highest completion");
    }

    public static boolean asksLowestRate(String normalized) {
        return containsAny(normalized,
                "达成率最低",
                "完成率最低",
                "目标达成率最低",
                "目标完成率最低",
                "较低",
                "最低",
                "lowest achievement",
                "lowest completion");
    }

    public static boolean asksWinRate(String normalized) {
        return containsAny(normalized,
                "赢单率",
                "成交率",
                "成交转化率",
                "win rate",
                "conversion rate");
    }

    public static boolean mentionsProductDimension(String normalized) {
        return containsAny(normalized,
                "车型",
                "车款",
                "哪款车",
                "哪种车",
                "product model",
                "model");
    }

    public static boolean mentionsDealerDimension(String normalized) {
        return containsAny(normalized,
                "经销商",
                "门店",
                "店",
                "dealer",
                "store");
    }

    public static boolean asksBreakdown(String normalized) {
        return containsAny(normalized,
                "分布",
                "分别",
                "分别多少",
                "分别有多少",
                "多少",
                "怎么分",
                "按",
                "占比",
                "结构",
                "构成",
                "breakdown",
                "distribution");
    }

    public static boolean asksStatusBreakdown(String normalized) {
        return containsAny(normalized, "状态", "status") && asksBreakdown(normalized);
    }

    public static boolean asksOpportunityStageBreakdown(String normalized) {
        return containsAny(normalized, "阶段", "stage") && asksBreakdown(normalized);
    }

    public static boolean asksOpportunitySourceBreakdown(String normalized) {
        return containsAny(normalized, "来源", "渠道", "source")
                && (containsAny(normalized, "商机", "opportunity") || asksBreakdown(normalized) || asksWinRate(normalized));
    }

    public static boolean asksTaskSubjectBreakdown(String normalized) {
        return containsAny(normalized, "任务类型", "任务类别", "subject")
                || (containsAny(normalized, "任务", "task") && containsAny(normalized, "类型", "类别"));
    }

    public static int requestedTopLimit(String normalized, int defaultLimit) {
        if (containsAny(normalized, "前三", "top3", "top 3")) {
            return 3;
        }
        if (containsAny(normalized, "前五", "top5", "top 5")) {
            return 5;
        }
        return defaultLimit;
    }

    private static boolean containsAny(String source, String... keywords) {
        return Stream.of(keywords).anyMatch(source::contains);
    }
}
