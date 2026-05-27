package com.brand.agentpoc.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnalyticsScenarioCatalog {

    private static final List<String> WORKFLOW_STEPS = List.of(
            "User Query",
            "Context Assembly",
            "Intent Recognition",
            "Tool Selection",
            "Data Processing",
            "Report Generation",
            "Streaming"
    );

    private static final List<ScenarioWorkflow> SCENARIOS = List.of(
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                    "目标达成分析",
                    "Target Achievement Analysis",
                    List.of(
                            "本月哪些经销商目标达成率最低？",
                            "华东区域各门店本月的目标达成对比如何？"
                    ),
                    List.of(
                            "Which dealers have the lowest target achievement this month?",
                            "How does the target achievement compare among East China dealers?"
                    ),
                    List.of("getCurrentDate()", "searchDealers()", "queryTargets()"),
                    "对比下达目标销量 asKTarget 与实际赢单数 opportunityWonCount，计算目标达成率。",
                    "Compare asKTarget with opportunityWonCount to compute the target achievement rate."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL,
                    "商机漏斗与转化分析",
                    "Opportunity Funnel & Conversion Analysis",
                    List.of(
                            "Vega GT 和 Terra XL 在不同购车周期下的商机阶段分布如何？",
                            "最近已战败商机主要集中在哪些车型，核心战败原因是什么？"
                    ),
                    List.of(
                            "How are Vega GT and Terra XL opportunities distributed by purchase horizon and stage?",
                            "Which models dominate recent Closed Lost opportunities, and what are the key lost reasons?"
                    ),
                    List.of("getCurrentDate()", "searchDealers()", "queryOpportunities()"),
                    "提取商机阶段 stageName，按线索来源 LeadSource、购买意向 PurchaseHorizon、车型 Model 维度分组，统计各阶段数量、流失率及战败原因 ClosedReason。",
                    "Extract stageName, group by LeadSource, PurchaseHorizon, and Model to count each stage, compute drop-off rates, and analyze ClosedLost reasons."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.SALES_FOLLOW_UP,
                    "销售跟进分析",
                    "Sales Follow-up Analysis",
                    List.of(
                            "哪些门店销售跟进活跃度偏低？",
                            "目前有哪些超期未处理的销售跟进任务？"
                    ),
                    List.of(
                            "Which dealers show low sales follow-up activity?",
                            "What are the current overdue sales follow-up tasks?"
                    ),
                    List.of("getCurrentDate()", "queryTasks(status=\"overdue\")", "searchDealers()"),
                    "过滤超期任务并结合门店维度评估销售顾问的跟进效率和积压情况。",
                    "Filter overdue tasks and evaluate each dealer's follow-up efficiency and backlog."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.CAMPAIGN_PERFORMANCE,
                    "市场活动规划与效果分析",
                    "Campaign Planning & Performance Analysis",
                    List.of(
                            "最近市场活动带来的商机效果怎么样？",
                            "转化率最高的市场活动类型是哪种？"
                    ),
                    List.of(
                            "How effective are recent campaigns in generating opportunities?",
                            "Which campaign type has the highest conversion rate?"
                    ),
                    List.of("getCurrentDate()", "queryCampaigns()"),
                    "交叉提取活动规划目标 totalNewCustomerTarget 与实际产出 actualOpportunityCount，计算活动达成率。",
                    "Compare totalNewCustomerTarget with actualOpportunityCount to measure campaign attainment."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.DEALER_BENCHMARK,
                    "经营对标分析",
                    "Dealer Benchmark Analysis",
                    List.of(
                            "某区域内哪些经销商经营表现最好？",
                            "华东A店和华南B店的各项销售指标对比如何？"
                    ),
                    List.of(
                            "Which dealers are outperforming others in this region?",
                            "How do the sales metrics compare between Store A and Store B?"
                    ),
                    List.of("searchDealers() × 2", "queryTargets()", "queryOpportunities()"),
                    "串联多门店经营数据包，构建多维经营指标对比矩阵。",
                    "Assemble multi-dealer operating metrics and build a cross-store benchmark matrix."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.LEAD_SOURCE,
                    "线索来源与自然流量趋势分析",
                    "Lead Source & Organic Traffic Trend Analysis",
                    List.of(
                            "目前线索的主要来源渠道是什么？",
                            "官网自然流量近期的转化趋势如何？"
                    ),
                    List.of(
                            "What are the main lead sources and how is organic traffic trending?",
                            "How is organic traffic converting recently?"
                    ),
                    List.of("getCurrentDate()", "queryLeads(leadSource=\"官网自然流量\")"),
                    "按来源维度统计线索量与 isConverted=true 的转化比例，分析来源结构和近期趋势。",
                    "Measure lead volume and conversion by source, with emphasis on recent organic traffic trends."
            ),
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY,
                    "门店经营活跃度",
                    "Dealer Business Activity",
                    List.of(
                            "哪些门店经营活跃度最高？",
                            "哪些门店处于休眠状态？",
                            "华东区门店活跃度排名"
                    ),
                    List.of(
                            "Which dealers show the highest business activity?",
                            "Which dealers are dormant?",
                            "Dealer business activity ranking in East China"
                    ),
                    List.of("getCurrentDate()", "searchDealers()", "queryTargets()"),
                    "按最近12个业务月逐月判定有目标/有商机/有成交/有数据四个维度，汇总0-48分评出门店经营活跃度五级。",
                    "Score each dealer 0-48 across 4 dimensions (target/opportunity create/won/data presence) over the last 12 business months, classified into 5 activity tiers."
            )
    );

    private static final Map<AnalyticsPlan.Scenario, ScenarioWorkflow> SCENARIO_MAP = indexByScenario();

    private AnalyticsScenarioCatalog() {
    }

    public static String workflowSummary() {
        return String.join(" -> ", WORKFLOW_STEPS);
    }

    public static List<ScenarioWorkflow> all() {
        return SCENARIOS;
    }

    public static ScenarioWorkflow forScenario(AnalyticsPlan.Scenario scenario) {
        return SCENARIO_MAP.get(scenario);
    }

    private static Map<AnalyticsPlan.Scenario, ScenarioWorkflow> indexByScenario() {
        Map<AnalyticsPlan.Scenario, ScenarioWorkflow> workflows = new LinkedHashMap<>();
        for (ScenarioWorkflow workflow : SCENARIOS) {
            workflows.put(workflow.scenario(), workflow);
        }
        return Map.copyOf(workflows);
    }

    public record ScenarioWorkflow(
            AnalyticsPlan.Scenario scenario,
            String zhLabel,
            String enLabel,
            List<String> zhExamples,
            List<String> enExamples,
            List<String> toolChain,
            String zhLogicSummary,
            String enLogicSummary
    ) {
        public ScenarioWorkflow {
            zhExamples = List.copyOf(zhExamples);
            enExamples = List.copyOf(enExamples);
            toolChain = List.copyOf(toolChain);
        }

        public String label(String language) {
            return "zh".equals(language) ? zhLabel : enLabel;
        }

        public List<String> examples(String language) {
            return "zh".equals(language) ? zhExamples : enExamples;
        }

        public String logicSummary(String language) {
            return "zh".equals(language) ? zhLogicSummary : enLogicSummary;
        }

        public String toolChainSummary() {
            return String.join(" -> ", toolChain);
        }
    }
}
