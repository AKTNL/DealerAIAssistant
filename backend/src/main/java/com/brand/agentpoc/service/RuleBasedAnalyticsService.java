package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.CalcStep;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.entity.Task;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import com.brand.agentpoc.service.analytics.AnalyticsCalculator;
import com.brand.agentpoc.service.analytics.ReportRenderer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedAnalyticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedAnalyticsService.class);

    private static final DateTimeFormatter CALL_CHAIN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, List<String>> CITY_ALIASES = Map.of(
            "Beijing", List.of("beijing", "\u5317\u4eac"),
            "Shanghai", List.of("shanghai", "\u4e0a\u6d77"),
            "Hangzhou", List.of("hangzhou", "\u676d\u5dde"),
            "Guangzhou", List.of("guangzhou", "\u5e7f\u5dde"),
            "Chengdu", List.of("chengdu", "\u6210\u90fd")
    );

    private static final Map<String, String> CITY_ZH = Map.of(
            "Beijing", "\u5317\u4eac",
            "Shanghai", "\u4e0a\u6d77",
            "Hangzhou", "\u676d\u5dde",
            "Guangzhou", "\u5e7f\u5dde",
            "Chengdu", "\u6210\u90fd"
    );

    private final PromptFactory promptFactory;
    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;
    private final DataQueryService dataQueryService;
    private final Clock clock;
    private final ReportRenderer reportRenderer = new ReportRenderer();
    private final AnalyticsCalculator analyticsCalculator = new AnalyticsCalculator();
    private final ThreadLocal<AnalysisDataCache> analysisDataCache = new ThreadLocal<>();

    @Autowired
    public RuleBasedAnalyticsService(
            PromptFactory promptFactory,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository,
            DataQueryService dataQueryService,
            Clock clock
    ) {
        this.promptFactory = promptFactory;
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
        this.dataQueryService = dataQueryService;
        this.clock = clock;
    }

    RuleBasedAnalyticsService(
            PromptFactory promptFactory,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository,
            DataQueryService dataQueryService
    ) {
        this(
                promptFactory,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                dataQueryService,
                Clock.systemDefaultZone()
        );
    }

    public AnalyticsPlan plan(String message, String language) {
        analysisDataCache.set(new AnalysisDataCache());
        try {
            AnalysisTopic topic = detectTopic(message);
            AnalyticsPlan.Scenario scenario = mapScenario(topic);
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow = AnalyticsScenarioCatalog.forScenario(scenario);
            AnalysisScope scope = detectScope(message);
            String scopeSummary = scope.summary(language);
            List<String> progressMessages = buildProgressMessages(language, scenarioWorkflow);
            ScenarioResult result = switch (topic) {
                case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language, message);
                case OPPORTUNITY_FUNNEL -> analyzeOpportunityFunnel(scope, language, message);
                case SALES_FOLLOW_UP -> analyzeSalesFollowUp(scope, language, detectSalesFollowUpFocus(message), message);
                case CAMPAIGN_PERFORMANCE -> analyzeCampaignPerformance(scope, language, message);
                case LEAD_SOURCE -> analyzeLeadSource(scope, language, message);
                case DEALER_BUSINESS_ACTIVITY -> analyzeDealerBusinessActivity(scope, language, message);
                case DEALER_BENCHMARK -> analyzeDealerBenchmark(scope, language);
                case DATA_OVERVIEW -> analyzeDataOverview(scope, language);
            };
            String callChain = buildInterfaceCallChain(language, topic, scenarioWorkflow, scope);
            ScenarioResult resultWithCallChain = result.withReply(
                    prependInterfaceCallChain(language, callChain, result.reply())
            );
            String fallbackReply = resultWithCallChain.reply();
            logDataQuality(language, scope, resultWithCallChain.quality());

            String visibleThinking;
            if (!result.traceSteps().isEmpty()) {
                visibleThinking = promptFactory.buildVisibleThinking(language, result.traceSteps());
            } else {
                visibleThinking = promptFactory.buildVisibleThinking(language, scenarioWorkflow, scopeSummary);
            }

            return new AnalyticsPlan(
                    scenario,
                    scenarioWorkflow,
                    scopeSummary,
                    progressMessages,
                    visibleThinking,
                    buildGroundedReference(scenarioWorkflow, scopeSummary, language, fallbackReply, resultWithCallChain.quality()),
                    fallbackReply
            );
        } finally {
            analysisDataCache.remove();
        }
    }

    public AnalyticsPlan plan(String message, String language, String traceId, Consumer<StepEvent> onStep) {
        analysisDataCache.set(new AnalysisDataCache());
        AtomicInteger seq = new AtomicInteger(1);
        try {
            AnalysisTopic topic = detectTopic(message);
            AnalyticsPlan.Scenario scenario = mapScenario(topic);
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow = AnalyticsScenarioCatalog.forScenario(scenario);

            // Emit topic-identification step
            emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.model_thought,
                    isZh(language) ? "识别分析主题" : "Identify analysis topic",
                    isZh(language) ? "检测到主题: " + scenarioWorkflow.label(language) : "Detected topic: " + scenarioWorkflow.label(language),
                    Map.of("topic", topic.name(), "scenario", scenario.name())));

            AnalysisScope scope = detectScope(message);
            String scopeSummary = scope.summary(language);
            List<String> progressMessages = buildProgressMessages(language, scenarioWorkflow);
            ScenarioResult result = switch (topic) {
                case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language, message, traceId, seq, onStep);
                case OPPORTUNITY_FUNNEL -> analyzeOpportunityFunnel(scope, language, message, traceId, seq, onStep);
                case SALES_FOLLOW_UP -> analyzeSalesFollowUp(scope, language, detectSalesFollowUpFocus(message), message, traceId, seq, onStep);
                case CAMPAIGN_PERFORMANCE -> analyzeCampaignPerformance(scope, language, message, traceId, seq, onStep);
                case LEAD_SOURCE -> analyzeLeadSource(scope, language, message, traceId, seq, onStep);
                case DEALER_BUSINESS_ACTIVITY -> analyzeDealerBusinessActivity(scope, language, message, traceId, seq, onStep);
                case DEALER_BENCHMARK -> analyzeDealerBenchmark(scope, language, traceId, seq, onStep);
                case DATA_OVERVIEW -> analyzeDataOverview(scope, language, traceId, seq, onStep);
            };

            // Emit insight step based on the last CalcStep in the result
            if (!result.traceSteps().isEmpty()) {
                CalcStep lastStep = result.traceSteps().getLast();
                emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.insight,
                        isZh(language) ? "分析完成" : "Analysis complete",
                        lastStep.labelZh(),
                        Map.of("quality", result.quality().state().name())));
            } else {
                emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.insight,
                        isZh(language) ? "分析完成" : "Analysis complete",
                        isZh(language) ? "场景: " + scenarioWorkflow.label(language) : "Scenario: " + scenarioWorkflow.label(language),
                        Map.of("quality", result.quality().state().name())));
            }

            String callChain = buildInterfaceCallChain(language, topic, scenarioWorkflow, scope);
            ScenarioResult resultWithCallChain = result.withReply(
                    prependInterfaceCallChain(language, callChain, result.reply())
            );
            String fallbackReply = resultWithCallChain.reply();
            logDataQuality(language, scope, resultWithCallChain.quality());

            String visibleThinking;
            if (!result.traceSteps().isEmpty()) {
                visibleThinking = promptFactory.buildVisibleThinking(language, result.traceSteps());
            } else {
                visibleThinking = promptFactory.buildVisibleThinking(language, scenarioWorkflow, scopeSummary);
            }

            emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.model_thought,
                    isZh(language) ? "展开思考过程" : "Reasoning trace",
                    visibleThinking,
                    Map.of()));

            return new AnalyticsPlan(
                    scenario,
                    scenarioWorkflow,
                    scopeSummary,
                    progressMessages,
                    visibleThinking,
                    buildGroundedReference(scenarioWorkflow, scopeSummary, language, fallbackReply, resultWithCallChain.quality()),
                    fallbackReply
            );
        } finally {
            analysisDataCache.remove();
        }
    }

    private String prependInterfaceCallChain(String language, String callChain, String reply) {
        String heading = "zh".equals(language) ? "## 接口调用链" : "## Interface Call Chain";
        return heading + "\n\n" + callChain.trim() + "\n\n" + reply.trim();
    }

    private String buildInterfaceCallChain(
            String language,
            AnalysisTopic topic,
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow,
            AnalysisScope scope
    ) {
        boolean isZh = "zh".equals(language);
        String currentDate = formatCallChainDate();
        String scopeSummary = escapeHtml(scope.summary(language));
        String dealerMapping = scope.dealerCode() != null
                ? (isZh
                        ? "命中经销商 `%s`，使用该编码过滤相关数据。".formatted(scope.dealerCode())
                        : "Matched dealer `%s` and used that code to filter related data.".formatted(scope.dealerCode()))
                : (isZh
                        ? "未命中单一经销商，按当前范围汇总经销商主数据。"
                        : "No single dealer matched; aggregating dealer master data for the current scope.");
        String dataCategories = dataCategorySummary(topic);
        String aggregation = aggregationSummary(topic, language);

        if (isZh) {
            return """
                    1. 获取当前时间：%s，作为本月/近期等时间范围的计算基准。
                    2. 实体与意图解析：识别为%s，范围为 %s。
                    3. 主数据编码映射：%s
                    4. 数据调用：读取 %s，按当前范围过滤。
                    5. 聚合对比：%s
                    """.formatted(
                    currentDate,
                    scenarioWorkflow.label(language),
                    scopeSummary,
                    dealerMapping,
                    dataCategories,
                    aggregation
            );
        }

        return """
                1. Current date: %s, used as the baseline for month/recent time windows.
                2. Entity and intent parsing: identified %s, scope %s.
                3. Master data mapping: %s
                4. Data calls: read %s and filtered by the current scope.
                5. Aggregation and comparison: %s
                """.formatted(
                currentDate,
                scenarioWorkflow.label(language),
                scopeSummary,
                dealerMapping,
                dataCategories,
                aggregation
        );
    }

    private String formatCallChainDate() {
        return CALL_CHAIN_DATE_FORMATTER.format(clock.instant().atZone(clock.getZone()));
    }

    private String dataCategorySummary(AnalysisTopic topic) {
        return switch (topic) {
            case TARGET_ACHIEVEMENT -> "Target, Opportunity";
            case OPPORTUNITY_FUNNEL -> "Opportunity";
            case SALES_FOLLOW_UP -> "Task, Opportunity, Dealer";
            case CAMPAIGN_PERFORMANCE -> "Campaign";
            case LEAD_SOURCE -> "Lead";
            case DEALER_BUSINESS_ACTIVITY -> "Target, Opportunity";
            case DEALER_BENCHMARK -> "Dealer, Target, Opportunity";
            case DATA_OVERVIEW -> "Opportunity, Lead, Task, Campaign";
        };
    }

    private String aggregationSummary(AnalysisTopic topic, String language) {
        boolean isZh = "zh".equals(language);
        return switch (topic) {
            case TARGET_ACHIEVEMENT -> isZh
                    ? "对比 asKTarget 与 opportunityWonCount，计算目标达成率并排序。"
                    : "compare asKTarget with opportunityWonCount, compute achievement rates, and rank dealers.";
            case OPPORTUNITY_FUNNEL -> isZh
                    ? "按 stageName 聚合商机数量，计算漏斗分布、赢单与流失信号。"
                    : "group opportunities by stageName and compute funnel distribution, won, and lost signals.";
            case SALES_FOLLOW_UP -> isZh
                    ? "按任务状态和门店聚合，识别逾期、积压和跟进活跃度。"
                    : "aggregate by task status and dealer to identify overdue, backlog, and follow-up activity.";
            case CAMPAIGN_PERFORMANCE -> isZh
                    ? "对比 totalNewCustomerTarget 与 actualOpportunityCount，计算活动达成率。"
                    : "compare totalNewCustomerTarget with actualOpportunityCount to compute campaign attainment.";
            case LEAD_SOURCE -> isZh
                    ? "按 leadSource 聚合线索量和转化量，计算来源结构与转化率。"
                    : "group by leadSource, then compute lead volume, converted leads, and conversion rate.";
            case DEALER_BUSINESS_ACTIVITY -> isZh
                    ? "按最近 12 个业务月汇总目标、商机创建、成交和数据存在信号，计算活跃度得分。"
                    : "score target, opportunity creation, won opportunity, and data-presence signals across the latest 12 business months.";
            case DEALER_BENCHMARK -> isZh
                    ? "组合多门店目标与商机指标，构建跨门店经营对比矩阵。"
                    : "combine target and opportunity metrics into a cross-dealer benchmark matrix.";
            case DATA_OVERVIEW -> isZh
                    ? "加载全部实体表，分别统计商机、线索、任务和活动总量。"
                    : "load all entity tables and count total opportunities, leads, tasks, and campaigns.";
        };
    }


    private AnalysisTopic detectTopic(String message) {
        String normalized = normalize(message);

        if (containsAny(normalized, "\u6570\u636e\u6982\u51b5")
                || (containsAny(normalized, "\u5f53\u524d\u7cfb\u7edf") && containsAny(normalized, "\u4e00\u5171\u6709\u591a\u5c11"))
                || (containsAny(normalized, "\u4e00\u5171\u6709\u591a\u5c11", "\u591a\u5c11\u6761")
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
        if (asksTopSalesVolume(normalized)
                && (mentionsProductDimension(normalized) || mentionsDealerDimension(normalized)
                        || containsAny(normalized, "\u8c01", "\u54ea\u4e2a", "\u54ea\u5bb6"))) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (asksTopSalesVolume(normalized)) {
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

    private List<Dealer> cachedDealers() {
        return cache().dealers();
    }

    private List<Target> cachedTargets() {
        return cache().targets();
    }

    private List<Opportunity> cachedOpportunities() {
        return cache().opportunities();
    }

    private List<Campaign> cachedCampaigns() {
        return cache().campaigns();
    }

    private List<Task> cachedTasks() {
        return cache().tasks();
    }

    private List<Lead> cachedLeads() {
        return cache().leads();
    }

    private AnalysisDataCache cache() {
        AnalysisDataCache cache = analysisDataCache.get();
        return cache != null ? cache : new AnalysisDataCache();
    }

    private SalesFollowUpFocus detectSalesFollowUpFocus(String message) {
        String normalized = normalize(message);
        if (normalized == null) {
            return SalesFollowUpFocus.BACKLOG_RISK;
        }
        if (containsAny(normalized,
                "\u6d3b\u8dc3\u5ea6\u6bd4\u8f83\u9ad8",
                "\u6d3b\u8dc3\u5ea6\u8f83\u9ad8",
                "\u6d3b\u8dc3\u5ea6\u6700\u9ad8",
                "\u6d3b\u8dc3\u5ea6\u9ad8",
                "\u9ad8\u6d3b\u8dc3",
                "\u6bd4\u8f83\u6d3b\u8dc3",
                "\u66f4\u6d3b\u8dc3",
                "\u6700\u6d3b\u8dc3",
                "\u6d3b\u8dc3\u95e8\u5e97",
                "high activity",
                "higher activity",
                "strong activity",
                "strongest follow-up activity",
                "highly active",
                "most active",
                "most active dealers",
                "more active",
                "more active dealers",
                "top activity")) {
            return SalesFollowUpFocus.HIGH_ACTIVITY;
        }
        return SalesFollowUpFocus.BACKLOG_RISK;
    }

    private AnalysisScope detectScope(String message) {
        String normalizedMessage = normalize(message);
        List<Dealer> dealers = cachedDealers();
        AnalysisTimeRange timeRange = detectTimeRange(message);

        Dealer matchedDealer = dealers.stream()
                .filter(dealer -> dealerReferenceMatchScore(normalizedMessage, dealer) > 0)
                .max(Comparator.comparingInt(dealer -> dealerReferenceMatchScore(normalizedMessage, dealer)))
                .orElse(null);

        String city = detectCity(normalizedMessage, dealers, matchedDealer);

        String dealerCode = matchedDealer != null ? matchedDealer.getDealerCode() : null;
        String dealerName = matchedDealer != null ? matchedDealer.getDealerName() : null;

        String dealerGroupName = dealers.stream()
                .map(Dealer::getDealerGroupName)
                .filter(Objects::nonNull)
                .distinct()
                .filter(groupName -> contains(normalizedMessage, groupName))
                .findFirst()
                .orElse(matchedDealer != null ? matchedDealer.getDealerGroupName() : null);

        Set<String> productModels = new LinkedHashSet<>();
        productModels.addAll(cachedTargets().stream().map(Target::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(cachedOpportunities().stream().map(Opportunity::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(cachedCampaigns().stream().map(Campaign::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(cachedLeads().stream().map(Lead::getProductModel).collect(Collectors.toSet()));

        String productModel = productModels.stream()
                .filter(Objects::nonNull)
                .filter(model -> contains(normalizedMessage, model))
                .findFirst()
                .orElse(null);

        String leadSource = Stream.concat(
                        cachedLeads().stream().map(Lead::getLeadSource),
                        cachedOpportunities().stream().map(Opportunity::getLeadSource))
                .filter(Objects::nonNull)
                .filter(source -> !source.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(source -> contains(normalizedMessage, source))
                .findFirst()
                .orElse(null);

        return new AnalysisScope(timeRange, city, dealerCode, dealerName, dealerGroupName, productModel, leadSource);
    }

    private int dealerReferenceMatchScore(String normalizedMessage, Dealer dealer) {
        int score = 0;
        score = Math.max(score, referenceMatchScore(normalizedMessage, dealer.getDealerCode(), 1_000));
        score = Math.max(score, referenceMatchScore(normalizedMessage, removeParenthesizedSuffix(dealer.getDealerName()), 1_000));
        score = Math.max(score, referenceMatchScore(normalizedMessage, dealer.getDealerName(), 2_000));
        return score;
    }

    private int referenceMatchScore(String normalizedSource, String candidate, int baseScore) {
        String normalizedCandidate = normalize(candidate);
        if (normalizedSource == null || normalizedCandidate == null) {
            return 0;
        }
        if (!normalizedSource.contains(normalizedCandidate)) {
            return 0;
        }
        return baseScore + normalizedCandidate.length();
    }

    private String detectCity(String normalizedMessage, List<Dealer> dealers, Dealer matchedDealer) {
        String cityFromKnownDealerData = dealers.stream()
                .map(Dealer::getCity)
                .filter(Objects::nonNull)
                .distinct()
                .filter(city -> contains(normalizedMessage, city))
                .findFirst()
                .orElse(null);
        if (cityFromKnownDealerData != null) {
            return cityFromKnownDealerData;
        }

        String cityFromAliases = CITY_ALIASES.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalizedMessage::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (cityFromAliases != null) {
            return cityFromAliases;
        }

        return matchedDealer != null ? matchedDealer.getCity() : null;
    }

    private AnalysisTimeRange detectTimeRange(String message) {
        String normalized = normalize(message);
        LocalDate today = LocalDate.now(clock);

        if (containsAny(normalized, "本月", "这个月", "当月", "this month", "current month")) {
            return AnalysisTimeRange.month(today.getYear(), today.getMonthValue());
        }
        if (containsAny(normalized, "本季度", "本季", "this quarter", "current quarter")) {
            return AnalysisTimeRange.quarter(today.getYear(), quarterOf(today.getMonthValue()));
        }
        if (containsAny(normalized, "今年", "本年", "this year", "current year")) {
            return AnalysisTimeRange.year(today.getYear());
        }
        if (containsAny(normalized, "近期", "最近", "recent", "lately")) {
            return AnalysisTimeRange.recent(today.minusDays(29), today);
        }

        if (message != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s*月").matcher(message);
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                if (month >= 1 && month <= 12) {
                    return AnalysisTimeRange.month(today.getYear(), month);
                }
            }
        }

        return AnalysisTimeRange.none();
    }

    private int quarterOf(int month) {
        return ((month - 1) / 3) + 1;
    }

    private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language) {
        return analyzeTargetAchievement(scope, language, null, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language, String message) {
        return analyzeTargetAchievement(scope, language, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        return analyzeTargetAchievement(scope, language, null, traceId, seq, onStep);
    }

    private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language, String message,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        List<Target> allTargets = cachedTargets();
        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "从 Target 表加载全部目标数据",
                "Load all target records from the Target table",
                "共 " + allTargets.size() + " 条记录",
                allTargets.size() + " records total"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Target 表加载全部目标数据" : "Load all target records from Target table",
                isZh(language) ? "共 " + allTargets.size() + " 条记录" : allTargets.size() + " records total",
                Map.of("source_type", "database", "table", "Target", "recordCount", allTargets.size())));

        List<Target> filtered = allTargets.stream()
                .filter(target -> matchesScope(target.getDealerCode(), target.getDealerName(), target.getCity(),
                        target.getDealerGroupName(), target.getProductModel(), scope))
                .filter(target -> scope.timeRange().matchesTarget(target.getTargetYear(), target.getTargetMonth()))
                .toList();

        traceSteps.add(new CalcStep(
                "按分析范围过滤（城市/门店/时间）",
                "Filter by analysis scope (city/dealer/time)",
                "匹配 " + filtered.size() + " 条记录",
                filtered.size() + " matching records"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + filtered.size() + " 条记录" : filtered.size() + " matching records",
                filterAuditMeta(scope, language, allTargets.size(), filtered.size())));

        if (filtered.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "目标达成分析无可用数据" : "No data for target achievement analysis",
                    isZh(language) ? "过滤后无匹配记录" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "target achievement");
        }

        ScenarioResult directResult = tryAnswerTargetQuestion(filtered, scope, language, message, traceSteps);
        if (directResult != null) {
            return directResult;
        }

        List<DealerTargetMetric> allMetrics = buildDealerTargetMetrics(filtered);
        List<DealerTargetMetric> validMetrics = allMetrics.stream()
                .filter(metric -> metric.targetValue() > 0)
                .toList();
        int primaryNumerator = validMetrics.stream().mapToInt(DealerTargetMetric::wonCount).sum();
        int primaryDenominator = validMetrics.stream().mapToInt(DealerTargetMetric::targetValue).sum();
        int requiredUnits = scope.dealerCode() == null ? 2 : 1;
        DataQualityContext quality = classifyRateQuality(
                "target achievement",
                "Achievement rate",
                filtered.size(),
                validMetrics.size(),
                requiredUnits,
                primaryNumerator,
                primaryDenominator,
                allMetrics.size() - validMetrics.size(),
                validMetrics.size() < 2 ? 0.0 : validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(0.0)
                        - validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).min().orElse(0.0),
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "\u6570\u636e\u8d28\u91cf\u4e0d\u8db3\uff0c\u65e0\u6cd5\u751f\u6210\u5206\u6790" : "Insufficient data quality for analysis",
                    isZh(language) ? "\u72b6\u6001: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

        List<DealerTargetMetric> metrics = validMetrics.stream()
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                .toList();

        DealerTargetMetric lowest = metrics.getFirst();
        DealerTargetMetric highest = metrics.getLast();
        double averageRate = metrics.stream().mapToDouble(DealerTargetMetric::achievementRate).average().orElse(0.0);
        long belowWarningCount = metrics.stream().filter(m -> m.achievementRate() < 75.0).count();
        int lowestGapUnits = Math.max(lowest.targetValue() - lowest.wonCount(), 0);

        traceSteps.add(new CalcStep(
                "\u6309\u95e8\u5e97\u805a\u5408\u76ee\u6807\u4e0e\u8d62\u5355\u6570\u636e\uff0c\u8ba1\u7b97\u8fbe\u6210\u7387",
                "Aggregate targets and won deals per dealer, compute achievement rate",
                validMetrics.size() + " \u4e2a\u95e8\u5e97\uff0c\u8fbe\u6210\u7387 = opportunityWonCount / asKTarget",
                validMetrics.size() + " dealers, achievement rate = wonCount / targetValue"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "\u6309\u95e8\u5e97\u805a\u5408\u76ee\u6807\u4e0e\u8d62\u5355\u6570\u636e\uff0c\u8ba1\u7b97\u8fbe\u6210\u7387" : "Aggregate targets and won deals per dealer, compute achievement rate",
                isZh(language)
                        ? validMetrics.size() + " \u4e2a\u95e8\u5e97\uff0c\u8fbe\u6210\u7387 = opportunityWonCount / asKTarget"
                        : validMetrics.size() + " dealers, achievementRate = wonCount / targetValue",
                auditMap(
                        "formula", "achievementRate = wonCount / targetValue",
                        "inputRecordCount", filtered.size(),
                        "dealerCount", validMetrics.size(),
                        "totalWonCount", primaryNumerator,
                        "totalTargetValue", primaryDenominator,
                        "excludedZeroTargetCount", allMetrics.size() - validMetrics.size(),
                        "sampleRows", targetMetricRows(validMetrics, 5)
                )));

        traceSteps.add(new CalcStep(
                "\u6392\u5e8f\u627e\u51fa\u6700\u4f4e/\u6700\u9ad8\u8fbe\u6210\u7387\u95e8\u5e97",
                "Sort to find lowest/highest achievement dealers",
                "\u6700\u4f4e\uff1a" + lowest.dealerName() + "\uff08" + formatPercent(lowest.achievementRate()) + "\uff0c" + lowest.wonCount() + "/" + lowest.targetValue() + "\uff09\uff1b\u6700\u9ad8\uff1a" + highest.dealerName() + "\uff08" + formatPercent(highest.achievementRate()) + "\uff0c" + highest.wonCount() + "/" + highest.targetValue() + "\uff09",
                "Lowest: " + lowest.dealerName() + " (" + formatPercent(lowest.achievementRate()) + ", " + lowest.wonCount() + "/" + lowest.targetValue() + "); Highest: " + highest.dealerName() + " (" + formatPercent(highest.achievementRate()) + ", " + highest.wonCount() + "/" + highest.targetValue() + ")"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "\u6392\u5e8f\u627e\u51fa\u6700\u4f4e/\u6700\u9ad8\u8fbe\u6210\u7387\u95e8\u5e97" : "Sort to find lowest/highest achievement dealers",
                isZh(language) ? "\u6700\u4f4e: " + lowest.dealerName() + ", \u6700\u9ad8: " + highest.dealerName() : "Lowest: " + lowest.dealerName() + ", Highest: " + highest.dealerName(),
                auditMap(
                        "sortBy", "achievementRate ascending",
                        "lowestDealer", targetMetricRow(lowest),
                        "highestDealer", targetMetricRow(highest),
                        "averageRate", averageRate,
                        "belowWarningCount", belowWarningCount,
                        "lowestGapUnits", lowestGapUnits
                )));

        if ("zh".equals(language)) {
            String conclusion;
            if (lowest.achievementRate() >= 100.0) {
                conclusion = String.format(
                        "- **%s** 在当前范围内为最低达成门店，但达成率 **%s**，已超额完成目标（%d / %d）\n"
                        + "- 当前范围覆盖 %d 家门店，平均达成率 **%s**，%d 家低于 75%% 预警线\n"
                        + "- 建议复盘 %s 的成交来源和跟进节奏，作为同城或同车型门店的参考样本",
                        lowest.dealerName(), formatPercent(lowest.achievementRate()), lowest.wonCount(), lowest.targetValue(),
                        metrics.size(), formatPercent(averageRate), belowWarningCount,
                        lowest.dealerName()
                );
            } else if (lowest.achievementRate() < 75.0) {
                String averageComparison = averageRate > 0.0 && lowest.achievementRate() < averageRate / 2.0
                        ? "，不到平均水平的一半"
                        : "";
                conclusion = String.format(
                        "- **%s** 目标达成率仅 **%s**%s，低于 75%% 预警线，属于红色预警门店\n"
                        + "- 整体平均达成率 **%s**，%d 家中 %d 家低于 75%%，本月目标缺口压力较大\n"
                        + "- 优先关注 %s 的 %d 台目标差额，结合在途商机判断是否需要调整目标或加大促单力度",
                        lowest.dealerName(), formatPercent(lowest.achievementRate()), averageComparison,
                        formatPercent(averageRate),
                        metrics.size(), belowWarningCount,
                        lowest.dealerName(), lowestGapUnits
                );
            } else {
                conclusion = String.format(
                        "- **%s** 目标达成率 **%s**，尚未达标但高于 75%% 预警线\n"
                        + "- 整体平均达成率 **%s**，%d 家中 %d 家低于 75%%，需继续补齐成交缺口\n"
                        + "- 优先推进 %s 的 %d 台目标差额，结合高概率商机安排本月促单节奏",
                        lowest.dealerName(), formatPercent(lowest.achievementRate()),
                        formatPercent(averageRate),
                        metrics.size(), belowWarningCount,
                        lowest.dealerName(), lowestGapUnits
                );
            }

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Dealers covered", String.valueOf(metrics.size())});
            dataRows.add(new String[]{"Lowest achievement", "%s (%d / %d)".formatted(lowest.dealerName(), lowest.wonCount(), lowest.targetValue())});
            dataRows.add(new String[]{"Highest achievement", "%s (%d / %d)".formatted(highest.dealerName(), highest.wonCount(), highest.targetValue())});
            dataRows.add(new String[]{"Average achievement", formatPercent(averageRate)});
            dataRows.add(new String[]{"Achievement gap (high - low)", formatPercent(highest.achievementRate() - lowest.achievementRate())});

            List<DealerTargetMetric> sorted = metrics.stream()
                    .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                    .toList();
            List<DealerTargetMetric> topBottom = bottomTopDistinct(sorted, 3);

            String mermaid = buildMermaidXyChart(
                    "%s \u76ee\u6807\u8fbe\u6210\u7387".formatted(scope.summary(language)),
                    "\u95e8\u5e97", "\u8fbe\u6210\u7387 (%)",
                    ChartEntityType.DEALER,
                    topBottom.stream().map(DealerTargetMetric::dealerName).toList(),
                    topBottom.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                    averageRate
            );

            double maxRate = topBottom.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.DEALER,
                    topBottom.stream().map(DealerTargetMetric::dealerName).toList(),
                    topBottom.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            double deltaToAverage = averageRate - lowest.achievementRate();
            String lowestAttribution = lowest.achievementRate() >= 100.0
                    ? "%s 达成率 %s，%s，当前不构成风险门店".formatted(
                            lowest.dealerName(), formatPercent(lowest.achievementRate()),
                            describeDeltaToAverage(deltaToAverage))
                    : "%s 达成率 %s，%s，需结合在途商机转化节奏排查原因".formatted(
                            lowest.dealerName(), formatPercent(lowest.achievementRate()),
                            describeDeltaToAverage(deltaToAverage));
            String comparisonAttribution = metrics.size() < 2
                    ? "当前仅 1 家门店匹配，结果适合做单店复盘，不适合解读为门店间排名落后"
                    : "%s 达成率 %s，领先第二名 %.1f 个百分点，其活动节奏和商机跟进模式值得横向推广".formatted(
                            highest.dealerName(), formatPercent(highest.achievementRate()),
                            highest.achievementRate() - sorted.get(sorted.size() - 2).achievementRate());
            List<String> attributions = List.of(lowestAttribution, comparisonAttribution);

            String primaryRecommendation = lowestGapUnits > 0
                    ? "围绕 %s 的 %d 台目标差额，优先推进高概率商机成交，并复盘目标设置是否匹配当前线索池".formatted(
                            lowest.dealerName(), lowestGapUnits)
                    : "复盘 %s 的成交来源、邀约节奏和跟进动作，沉淀为可复制样板".formatted(lowest.dealerName());
            String secondaryRecommendation = metrics.size() < 2
                    ? "保持当前成交节奏，继续跟踪下月目标和新增商机储备，避免达成率回落"
                    : "复用 %s 的经营打法和活动节奏，优先复制到同城或同车型门店".formatted(highest.dealerName());
            List<String> recommendations = List.of(primaryRecommendation, secondaryRecommendation);

            List<String> followUps = List.of(
                    "\u8fd9\u4e2a\u8303\u56f4\u5185\u54ea\u4e9b\u95e8\u5e97\u7684\u9ad8\u6982\u7387\u5546\u673a\u6700\u591a\uff1f",
                    "\u76ee\u6807\u8fbe\u6210\u7387\u6700\u4f4e\u7684\u95e8\u5e97\u8fd1\u671f\u8ddf\u8fdb\u4efb\u52a1\u72b6\u6001\u5982\u4f55\uff1f"
            );

            return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("\u76ee\u6807\u8fbe\u6210\u5206\u6790", metrics.size(), "\u5e73\u5747\u8fbe\u6210\u7387", formatPercent(averageRate), "80%",
                            highest.dealerName(), formatPercent(highest.achievementRate()),
                            lowest.dealerName(), formatPercent(lowest.achievementRate())),
                    mermaid, fallback, attributions, recommendations, followUps),
                    quality, traceSteps);
        }
        String conclusion = String.format(
                "- **%s** has the lowest target achievement at **%s**, well below average \u2014 this is a red-flag store.\n"
                + "- The overall average is **%s**; %d out of %d dealers are below 75%%, indicating systemic target pressure.\n"
                + "- Prioritize %s's gap of %d units \u2014 evaluate whether to adjust targets or intensify closing efforts.",
                lowest.dealerName(), formatPercent(lowest.achievementRate()),
                formatPercent(averageRate),
                metrics.stream().filter(m -> m.achievementRate() < 75.0).count(),
                metrics.size(),
                lowest.dealerName(),
                Math.max(lowest.targetValue() - lowest.wonCount(), 0)
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Dealers covered", String.valueOf(metrics.size())});
        dataRows.add(new String[]{"Lowest achievement", "%s (%d / %d)".formatted(lowest.dealerName(), lowest.wonCount(), lowest.targetValue())});
        dataRows.add(new String[]{"Highest achievement", "%s (%d / %d)".formatted(highest.dealerName(), highest.wonCount(), highest.targetValue())});
        dataRows.add(new String[]{"Average achievement", formatPercent(averageRate)});
        dataRows.add(new String[]{"Achievement gap (high - low)", formatPercent(highest.achievementRate() - lowest.achievementRate())});

        List<DealerTargetMetric> sortedEn = metrics.stream()
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                .toList();
        List<DealerTargetMetric> topBottomEn = bottomTopDistinct(sortedEn, 3);

        String mermaid = buildMermaidXyChart(
                "Achievement Rate \u2014 %s".formatted(scope.summary(language)),
                "Dealer", "Attainment (%)",
                ChartEntityType.DEALER,
                topBottomEn.stream().map(DealerTargetMetric::dealerName).toList(),
                topBottomEn.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                averageRate
        );

        double maxRateEn = topBottomEn.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.DEALER,
                topBottomEn.stream().map(DealerTargetMetric::dealerName).toList(),
                topBottomEn.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s trails at %s (%.1f pp below average), likely due to prolonged opportunity conversion cycles.".formatted(
                        lowest.dealerName(), formatPercent(lowest.achievementRate()), averageRate - lowest.achievementRate()),
                "%s leads at %s, outperforming the runner-up \u2014 its campaign rhythm and follow-up model merit replication.".formatted(
                        highest.dealerName(), formatPercent(highest.achievementRate()))
        );

        List<String> recommendations = List.of(
                "Clear %d pending and overdue tasks for %s, targeting %.1f%% attainment within one week.".formatted(
                        Math.max(lowest.targetValue() - lowest.wonCount(), 0), lowest.dealerName(),
                        Math.min(lowest.achievementRate() + 15.0, 80.0)),
                "Replicate %s's operating playbook and campaign cadence in same-city or same-model stores.".formatted(highest.dealerName())
        );

        List<String> followUps = List.of(
                "Which dealers in this scope have the strongest high-probability opportunity pipeline?",
                "What is the recent follow-up task status for the lowest-achievement dealer?"
        );

        return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows,
                new SummaryContext("Target Achievement", metrics.size(), "Achievement Rate", formatPercent(averageRate), "80%",
                        highest.dealerName(), formatPercent(highest.achievementRate()),
                        lowest.dealerName(), formatPercent(lowest.achievementRate())),
                mermaid, fallback, attributions, recommendations, followUps),
                quality, traceSteps);
    }

    private ScenarioResult analyzeOpportunityFunnel(AnalysisScope scope, String language) {
        return analyzeOpportunityFunnel(scope, language, null, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeOpportunityFunnel(AnalysisScope scope, String language, String message) {
        return analyzeOpportunityFunnel(scope, language, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeOpportunityFunnel(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        return analyzeOpportunityFunnel(scope, language, null, traceId, seq, onStep);
    }

    private ScenarioResult analyzeOpportunityFunnel(AnalysisScope scope, String language, String message,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        ScenarioResult directResult = tryAnswerOpportunityQuestion(scope, language, message);
        if (directResult != null) {
            return directResult;
        }

        DataQueryResponse response = dataQueryService.query("opportunities", buildQueryFilters(scope, true));
        if (!response.items().isEmpty()) {
            return analyzeOpportunityFunnelFromQuery(response, scope, language);
        }

        List<Opportunity> allOpportunities = cachedOpportunities();
        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "从 Opportunity 表加载全部商机数据",
                "Load all opportunity records from the Opportunity table",
                "共 " + allOpportunities.size() + " 条记录",
                allOpportunities.size() + " records total"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Opportunity 表加载全部商机数据" : "Load all opportunity records from Opportunity table",
                isZh(language) ? "共 " + allOpportunities.size() + " 条记录" : allOpportunities.size() + " records total",
                Map.of("source_type", "database", "table", "Opportunity", "recordCount", allOpportunities.size())));

        List<Opportunity> filtered = allOpportunities.stream()
                .filter(opportunity -> matchesScope(opportunity.getDealerCode(), opportunity.getDealerName(), opportunity.getCity(),
                        opportunity.getDealerGroupName(), opportunity.getProductModel(), scope))
                .filter(opportunity -> scope.timeRange().matchesDate(opportunity.getCreatedDate()))
                .toList();

        traceSteps.add(new CalcStep(
                "按分析范围过滤（城市/门店/时间）",
                "Filter by analysis scope (city/dealer/time)",
                "匹配 " + filtered.size() + " 条记录",
                filtered.size() + " matching records"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + filtered.size() + " 条记录" : filtered.size() + " matching records",
                filterAuditMeta(scope, language, allOpportunities.size(), filtered.size())));

        if (filtered.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "商机漏斗分析无可用数据" : "No data for opportunity funnel analysis",
                    isZh(language) ? "过滤后无匹配记录" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "opportunity funnel");
        }

        Map<String, Long> stageCounts = filtered.stream()
                .collect(Collectors.groupingBy(Opportunity::getStageName, LinkedHashMap::new, Collectors.counting()));
        long wonCount = filtered.stream().filter(opportunity -> "Won".equalsIgnoreCase(opportunity.getStageName())).count();
        long lostCount = filtered.stream().filter(opportunity -> "Lost".equalsIgnoreCase(opportunity.getStageName())).count();
        long highProbabilityCount = filtered.stream()
                .filter(opportunity -> opportunity.getProbability() >= 60 && !"Won".equalsIgnoreCase(opportunity.getStageName()))
                .count();

        String topStage = stageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
        Map<String, Long> leadSourceCounts = filtered.stream()
                .collect(Collectors.groupingBy(Opportunity::getLeadSource, LinkedHashMap::new, Collectors.counting()));
        String topLeadSource = leadSourceCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");

        traceSteps.add(new CalcStep(
                "按 stageName 分组统计各阶段商机数量",
                "Group by stageName, count opportunities per stage",
                stageCounts.size() + " 个阶段，已赢单 " + wonCount + " 条，已丢单 " + lostCount + " 条，高概率（>=60%）在途 " + highProbabilityCount + " 条",
                stageCounts.size() + " stages, " + wonCount + " won, " + lostCount + " lost, " + highProbabilityCount + " high-probability open"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按阶段分组统计商机数量" : "Group by stage, count opportunities",
                isZh(language)
                        ? stageCounts.size() + " 个阶段, 赢单 " + wonCount + ", 丢单 " + lostCount + "；赢单率 = wonCount / totalOpportunityCount"
                        : stageCounts.size() + " stages, won " + wonCount + ", lost " + lostCount + "; winRate = wonCount / totalOpportunityCount",
                auditMap(
                        "formula", "stageCounts = count by stageName; winRate = wonCount / totalOpportunityCount",
                        "totalOpportunityCount", filtered.size(),
                        "stageCount", stageCounts.size(),
                        "wonCount", wonCount,
                        "lostCount", lostCount,
                        "highProbabilityCount", highProbabilityCount,
                        "stageCounts", stageCounts,
                        "leadSourceCounts", leadSourceCounts,
                        "sampleRows", opportunityRows(filtered, 5)
                )));

        traceSteps.add(new CalcStep(
                "识别最大阶段积压和主要线索来源",
                "Identify largest stage bottleneck and top lead source",
                "积压最多阶段：" + topStage + "（" + stageCounts.getOrDefault(topStage, 0L) + " 条）；主要线索来源：" + topLeadSource,
                "Largest stage: " + topStage + " (" + stageCounts.getOrDefault(topStage, 0L) + "); Top lead source: " + topLeadSource
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "识别阶段积压和线索来源" : "Identify stage bottleneck and lead source",
                isZh(language) ? "最大阶段: " + topStage + ", 主要来源: " + topLeadSource : "Largest stage: " + topStage + ", Top source: " + topLeadSource,
                auditMap(
                        "topStage", topStage,
                        "topStageCount", stageCounts.getOrDefault(topStage, 0L),
                        "topLeadSource", topLeadSource,
                        "topLeadSourceCount", leadSourceCounts.getOrDefault(topLeadSource, 0L),
                        "stageCounts", stageCounts,
                        "leadSourceCounts", leadSourceCounts
                )));

        DataQualityContext quality = classifyCountQuality(
                "opportunity funnel",
                "Opportunity count",
                filtered.size(),
                filtered.size() >= 3 ? 2 : 1,
                3,
                (int) wonCount,
                filtered.size(),
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

                if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- \u5f53\u524d\u5171\u6709 **%d** \u6761\u5546\u673a\uff0c\u4e3b\u529b\u9636\u6bb5\u96c6\u4e2d\u5728 **%s**\uff0c\u8d62\u5355\u7387 **%s**\n"
                    + "- \u4ecd\u6709 **%d** \u6761\u9ad8\u6982\u7387\u5546\u673a\uff08\u226560%%\uff09\u503c\u5f97\u91cd\u70b9\u63a8\u52a8\uff0c\u77ed\u671f\u8d62\u5355\u8f6c\u5316\u7a7a\u95f4\u660e\u786e\n"
                    + "- \u6f0f\u6597\u4e2d\u540e\u6bb5\u79ef\u538b\u660e\u663e\uff0c%s \u9636\u6bb5\u5360 %d%%\uff0c\u9700\u6392\u67e5\u63a8\u8fdb\u74f6\u9888",
                    filtered.size(), topStage,
                    formatPercent(percentage((int) wonCount, filtered.size())),
                    highProbabilityCount,
                    topStage,
                    (int) (stageCounts.getOrDefault(topStage, 0L) * 100 / filtered.size())
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total opportunities", String.valueOf(filtered.size())});
            dataRows.add(new String[]{"Won opportunities", String.valueOf(wonCount)});
            dataRows.add(new String[]{"Lost opportunities", String.valueOf(lostCount)});
            dataRows.add(new String[]{"High-probability open opportunities", String.valueOf(highProbabilityCount)});
            dataRows.add(new String[]{"Primary lead source", topLeadSource});
            dataRows.add(new String[]{"Win rate", formatPercent(percentage((int) wonCount, filtered.size()))});

            Map<String, Double> pieData = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : stageCounts.entrySet()) {
                pieData.put(entry.getKey(), (double) entry.getValue());
            }
            String mermaid = buildMermaidPie(
                    "%s \u5546\u673a\u9636\u6bb5\u5206\u5e03".formatted(scope.summary(language)),
                    ChartEntityType.STAGE,
                    pieData
            );
            String fallback = buildFallbackBars(
                    ChartEntityType.STAGE,
                    new ArrayList<>(pieData.keySet()),
                    pieData.values().stream().toList(),
                    pieData.values().stream().mapToDouble(Double::doubleValue).max().orElse(1)
            );

            List<String> attributions = List.of(
                    "\u5f53\u524d\u6f0f\u6597 %s \u9636\u6bb5\u79ef\u538b\u6700\u591a\uff08%d \u6761\uff09\uff0c\u8bf4\u660e\u8be5\u9636\u6bb5\u7684\u63a8\u8fdb\u6548\u7387\u5b58\u5728\u74f6\u9888".formatted(topStage, stageCounts.getOrDefault(topStage, 0L)),
                    "\u4e22\u5355 %d \u6761\uff0c\u4e3b\u8981\u6765\u81ea\u4e2d\u95f4\u9636\u6bb5\uff0c\u7ed3\u5408\u5546\u673a\u5361\u70b9\u5206\u6790\u53ef\u5b9a\u4f4d\u6d41\u5931\u539f\u56e0".formatted(lostCount),
                    "\u4e3b\u8981\u7ebf\u7d22\u6765\u6e90 %s \u8d21\u732e\u4e86\u6700\u5927\u5546\u673a\u91cf\uff0c\u4f46\u5176\u8f6c\u5316\u6548\u7387\u8fd8\u9700\u8ddf\u6700\u7ec8\u8d62\u5355\u7387\u5bf9\u7167\u8bc4\u4f30".formatted(topLeadSource)
            );

            List<String> recommendations = List.of(
                    "\u6309\u9636\u6bb5\u76d8\u70b9 %s \u7684\u5546\u673a\u5361\u70b9\uff0c\u4f18\u5148\u89e3\u51b3\u8be5\u9636\u6bb5\u7684\u63a8\u8fdb\u74f6\u9888\uff0c\u9884\u671f\u91ca\u653e\u79ef\u538b\u5546\u673a\u7684 30%%".formatted(topStage),
                    "\u8ffd\u8e2a\u6765\u81ea %s \u7684\u5546\u673a\u8f6c\u5316\u6548\u7387\uff0c\u82e5\u8d62\u5355\u7387\u6301\u7eed\u504f\u4f4e\u5219\u8c03\u6574\u6295\u653e\u7b56\u7565".formatted(topLeadSource),
                    "\u91cd\u70b9\u8ddf\u8fdb %d \u6761\u9ad8\u6982\u7387\u5546\u673a\uff0c\u9884\u8ba1 2 \u5468\u5185\u53ef\u8f6c\u5316\u4e3a %d \u6761\u65b0\u589e\u8d62\u5355".formatted(highProbabilityCount, (int)(highProbabilityCount * 0.3))
            );

            List<String> followUps = List.of(
                    "\u8fd9\u4e2a\u8303\u56f4\u5185\u9ad8\u6982\u7387\u5546\u673a\u4e3b\u8981\u96c6\u4e2d\u5728\u54ea\u4e9b\u95e8\u5e97\uff1f",
                    "\u5f53\u524d\u6f0f\u6597\u91cc\u4e22\u5355\u6700\u591a\u7684\u9636\u6bb5\u662f\u4ec0\u4e48\uff1f"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("\u5546\u673a\u6f0f\u6597\u5206\u6790", filtered.size(), "\u8d62\u5355\u7387",
                            formatPercent(percentage((int) wonCount, filtered.size())), "\u2014",
                            null, null, null, null),
                    mermaid, fallback, attributions, recommendations, followUps),
                    "opportunity funnel", "Opportunity count", traceSteps);
        }
        String conclusion = String.format(
                "- **%d** opportunities in scope, concentrated in the **%s** stage with a win rate of **%s**.\n"
                + "- **%d** high-probability opportunities (\u226560%%) are open \u2014 clear near-term conversion upside.\n"
                + "- %s stage holds %d%% of the funnel, suggesting a bottleneck worth investigating.",
                filtered.size(), topStage, formatPercent(percentage((int) wonCount, filtered.size())),
                highProbabilityCount,
                topStage, (int) (stageCounts.getOrDefault(topStage, 0L) * 100 / filtered.size())
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total opportunities", String.valueOf(filtered.size())});
        dataRows.add(new String[]{"Won opportunities", String.valueOf(wonCount)});
        dataRows.add(new String[]{"Lost opportunities", String.valueOf(lostCount)});
        dataRows.add(new String[]{"High-probability open opportunities", String.valueOf(highProbabilityCount)});
        dataRows.add(new String[]{"Primary lead source", topLeadSource});
        dataRows.add(new String[]{"Win rate", formatPercent(percentage((int) wonCount, filtered.size()))});

        Map<String, Double> pieData = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : stageCounts.entrySet()) {
            pieData.put(entry.getKey(), (double) entry.getValue());
        }
        String mermaid = buildMermaidPie(
                "Opportunity Stage \u2014 %s".formatted(scope.summary(language)),
                ChartEntityType.STAGE,
                pieData
        );
        String fallback = buildFallbackBars(
                ChartEntityType.STAGE,
                new ArrayList<>(pieData.keySet()),
                pieData.values().stream().toList(),
                pieData.values().stream().mapToDouble(Double::doubleValue).max().orElse(1)
        );

        List<String> attributions = List.of(
                "The %s stage holds %d opportunities \u2014 the largest accumulation, indicating a progression bottleneck.".formatted(topStage, stageCounts.getOrDefault(topStage, 0L)),
                "%d lost deals, mostly from mid-stages \u2014 review blocking points to identify churn causes.".formatted(lostCount),
                "%s is the dominant lead source by volume, but its win-rate should be benchmarked against cost.".formatted(topLeadSource)
        );

        List<String> recommendations = List.of(
                "Review blocking points in the %s stage \u2014 target releasing 30%% of the backlog.".formatted(topStage),
                "Track conversion efficiency of %s-sourced opportunities; adjust spend if win rates stay low.".formatted(topLeadSource),
                "Focus follow-up on the %d high-probability opportunities \u2014 expect %d additional wins in 2 weeks.".formatted(highProbabilityCount, (int)(highProbabilityCount * 0.3))
        );

        List<String> followUps = List.of(
                "Which dealers hold most of the high-probability pipeline in this scope?",
                "Which stage currently contributes the most lost opportunities?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows,
                new SummaryContext("Opportunity Funnel", filtered.size(), "Win Rate",
                        formatPercent(percentage((int) wonCount, filtered.size())), "—",
                        null, null, null, null),
                mermaid, fallback, attributions, recommendations, followUps),
                "opportunity funnel", "Opportunity count", traceSteps);
    }

    private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, SalesFollowUpFocus focus) {
        return analyzeSalesFollowUp(scope, language, focus, null, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, SalesFollowUpFocus focus,
            String message) {
        return analyzeSalesFollowUp(scope, language, focus, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, SalesFollowUpFocus focus,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        return analyzeSalesFollowUp(scope, language, focus, null, traceId, seq, onStep);
    }

    private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, SalesFollowUpFocus focus,
            String message, String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        ScenarioResult directResult = tryAnswerSalesFollowUpQuestion(scope, language, message);
        if (directResult != null) {
            return directResult;
        }

        DataQueryResponse response = dataQueryService.query("tasks", buildQueryFilters(scope, false));
        if (!response.items().isEmpty()) {
            return analyzeSalesFollowUpFromQuery(response, scope, language, focus);
        }

        List<Task> allTasks = cachedTasks();
        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "\u4ece Task \u8868\u52a0\u8f7d\u5168\u90e8\u4efb\u52a1\u6570\u636e",
                "Load all task records from the Task table",
                "\u5171 " + allTasks.size() + " \u6761\u8bb0\u5f55",
                allTasks.size() + " records total"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "\u4ece Task \u8868\u52a0\u8f7d\u5168\u90e8\u4efb\u52a1\u6570\u636e" : "Load all task records from Task table",
                isZh(language) ? "\u5171 " + allTasks.size() + " \u6761\u8bb0\u5f55" : allTasks.size() + " records total",
                Map.of("source_type", "database", "table", "Task", "recordCount", allTasks.size())));

        List<Task> filtered = allTasks.stream()
                .filter(task -> matchesScope(task.getDealerCode(), task.getDealerName(), task.getCity(),
                        task.getDealerGroupName(), null, scope))
                .filter(task -> scope.timeRange().matchesDate(task.getCreatedDate()))
                .toList();

        traceSteps.add(new CalcStep(
                "\u6309\u5206\u6790\u8303\u56f4\u8fc7\u6ee4\uff08\u57ce\u5e02/\u95e8\u5e97/\u65f6\u95f4\uff09",
                "Filter by analysis scope (city/dealer/time)",
                "\u5339\u914d " + filtered.size() + " \u6761\u8bb0\u5f55",
                filtered.size() + " matching records"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "\u6309\u5206\u6790\u8303\u56f4\u8fc7\u6ee4" : "Filter by analysis scope",
                isZh(language) ? "\u5339\u914d " + filtered.size() + " \u6761\u8bb0\u5f55" : filtered.size() + " matching records",
                filterAuditMeta(scope, language, allTasks.size(), filtered.size())));

        if (filtered.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "\u9500\u552e\u8ddf\u8fdb\u5206\u6790\u65e0\u53ef\u7528\u6570\u636e" : "No data for sales follow-up analysis",
                    isZh(language) ? "\u8fc7\u6ee4\u540e\u65e0\u5339\u914d\u8bb0\u5f55" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "sales follow-up");
        }

        long openTaskCount = filtered.stream().filter(task -> !"Completed".equalsIgnoreCase(task.getStatus())).count();
        long overdueCount = filtered.stream().filter(task -> "Overdue".equalsIgnoreCase(task.getStatus())).count();
        Map<String, Long> statusCounts = filtered.stream()
                .collect(Collectors.groupingBy(Task::getStatus, LinkedHashMap::new, Collectors.counting()));

        traceSteps.add(new CalcStep(
                "\u8fc7\u6ee4\u672a\u5b8c\u6210\u4efb\u52a1\uff08Status != 'Completed'\uff09",
                "Filter open tasks (Status != 'Completed')",
                "\u5171 " + openTaskCount + " \u6761\u672a\u5b8c\u6210\uff0c\u5176\u4e2d " + overdueCount + " \u6761\u5df2\u903e\u671f",
                openTaskCount + " open tasks, " + overdueCount + " overdue"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "\u8fc7\u6ee4\u672a\u5b8c\u6210\u4efb\u52a1" : "Filter open tasks",
                isZh(language) ? openTaskCount + " \u6761\u672a\u5b8c\u6210, " + overdueCount + " \u6761\u5df2\u903e\u671f" : openTaskCount + " open, " + overdueCount + " overdue",
                auditMap(
                        "formula", "openTaskCount = count(status != Completed); overdueCount = count(status == Overdue)",
                        "totalTaskCount", filtered.size(),
                        "openCount", openTaskCount,
                        "overdueCount", overdueCount,
                        "statusCounts", statusCounts,
                        "sampleRows", taskRows(filtered, 5)
                )));

        if (focus == SalesFollowUpFocus.HIGH_ACTIVITY) {
            return analyzeSalesFollowUpActivity(filtered, scope, language, traceSteps, openTaskCount, overdueCount, traceId, seq, onStep);
        }

        List<TaskBacklogMetric> backlogMetrics = filtered.stream()
                .collect(Collectors.groupingBy(Task::getDealerName))
                .entrySet().stream()
                .map(entry -> {
                    List<Task> tasks = entry.getValue();
                    long backlog = tasks.stream()
                            .filter(task -> !"Completed".equalsIgnoreCase(task.getStatus()))
                            .count();
                    return new TaskBacklogMetric(entry.getKey(), tasks.size(), backlog, percentage((int) backlog, tasks.size()));
                })
                .sorted(Comparator.comparingDouble(TaskBacklogMetric::backlogRate).reversed())
                .toList();

        TaskBacklogMetric highestBacklog = backlogMetrics.getFirst();

        traceSteps.add(new CalcStep(
                "\u6309 dealerName \u5206\u7ec4\u7edf\u8ba1\u79ef\u538b\u7387",
                "Group by dealerName, compute backlog rate",
                backlogMetrics.size() + " \u4e2a\u95e8\u5e97\uff0c\u79ef\u538b\u7387 = \u672a\u5b8c\u6210\u6570 / \u4efb\u52a1\u603b\u6570",
                backlogMetrics.size() + " dealers, backlog rate = open tasks / total tasks"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "\u6309\u95e8\u5e97\u5206\u7ec4\u7edf\u8ba1\u79ef\u538b\u7387" : "Group by dealer, compute backlog rate",
                isZh(language)
                        ? backlogMetrics.size() + " \u4e2a\u95e8\u5e97\uff0cbacklogRate = openTaskCount / totalTaskCount"
                        : backlogMetrics.size() + " dealers, backlogRate = openTaskCount / totalTaskCount",
                auditMap(
                        "formula", "backlogRate = openTaskCount / totalTaskCount",
                        "dealerCount", backlogMetrics.size(),
                        "totalTaskCount", filtered.size(),
                        "openTaskCount", openTaskCount,
                        "overdueTaskCount", overdueCount,
                        "sampleRows", taskBacklogRows(backlogMetrics, 5)
                )));

        List<TaskBacklogMetric> top3 = backlogMetrics.stream().limit(3).toList();
        StringBuilder zhTop = new StringBuilder();
        StringBuilder enTop = new StringBuilder();
        for (int i = 0; i < top3.size(); i++) {
            TaskBacklogMetric m = top3.get(i);
            if (i > 0) { zhTop.append("\uff1b"); enTop.append("; "); }
            zhTop.append(m.dealerName()).append("\uff1a\u79ef\u538b\u7387 ").append(formatPercent(m.backlogRate()))
                    .append("\uff08").append(m.backlogCount()).append("/").append(m.totalCount()).append("\uff09");
            enTop.append(m.dealerName()).append(": backlog rate ").append(formatPercent(m.backlogRate()))
                    .append(" (").append(m.backlogCount()).append("/").append(m.totalCount()).append(")");
        }

        traceSteps.add(new CalcStep(
                "\u6392\u5e8f\u627e\u51fa\u79ef\u538b\u6700\u9ad8\u95e8\u5e97",
                "Sort to find highest-backlog dealers",
                "Top 3\uff1a" + zhTop,
                "Top 3: " + enTop
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "\u6392\u5e8f\u627e\u51fa\u79ef\u538b\u6700\u9ad8\u95e8\u5e97" : "Sort to find highest-backlog dealers",
                isZh(language) ? "Top 3: " + zhTop : "Top 3: " + enTop,
                auditMap(
                        "sortBy", "backlogRate descending",
                        "highestBacklogDealer", taskBacklogRow(highestBacklog),
                        "topRows", taskBacklogRows(top3, 3)
                )));

        DataQualityContext quality = classifyCountQuality(
                "sales follow-up",
                "Task backlog",
                filtered.size(),
                backlogMetrics.size(),
                3,
                (int) openTaskCount,
                filtered.size(),
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "\u6570\u636e\u8d28\u91cf\u4e0d\u8db3\uff0c\u65e0\u6cd5\u751f\u6210\u5206\u6790" : "Insufficient data quality for analysis",
                    isZh(language) ? "\u72b6\u6001: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

                if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- **%s** \u8ddf\u8fdb\u79ef\u538b\u6700\u4e25\u91cd\uff0c\u672a\u5b8c\u6210\u4efb\u52a1\u5360\u6bd4 **%s**\uff0c\u4e3a\u6700\u9ad8\u98ce\u9669\u95e8\u5e97\n"
                    + "- \u5f53\u524d %d \u6761\u4efb\u52a1\u4e2d %d \u6761\u672a\u5b8c\u6210\uff0c\u5176\u4e2d\u6709 **%d \u6761\u5df2\u903e\u671f**\uff0c\u8fc7\u7a0b\u7ba1\u7406\u6025\u9700\u52a0\u5f3a\n"
                    + "- \u79ef\u538b\u7387 Top 3 \u95e8\u5e97\u5408\u8ba1\u5360\u672a\u5b8c\u6210\u4efb\u52a1\u603b\u91cf\u7684\u7edd\u5927\u90e8\u5206\uff0c\u9002\u5408\u96c6\u4e2d\u6e05\u7406",
                    highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()),
                    filtered.size(),
                    openTaskCount,
                    overdueCount
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total tasks", String.valueOf(filtered.size())});
            dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
            dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
            dataRows.add(new String[]{"Highest backlog dealer", "%s (%d / %d)".formatted(highestBacklog.dealerName(), highestBacklog.backlogCount(), highestBacklog.totalCount())});
            dataRows.add(new String[]{"Overdue rate", formatPercent(percentage((int) overdueCount, filtered.size()))});

            List<TaskBacklogMetric> top5 = backlogMetrics.stream().limit(3).toList();
            String mermaid = buildMermaidXyChart(
                    "%s \u8ddf\u8fdb\u79ef\u538b Top 3".formatted(scope.summary(language)),
                    "\u95e8\u5e97", "\u672a\u5b8c\u6210\u7387 (%)",
                    ChartEntityType.DEALER,
                    top5.stream().map(TaskBacklogMetric::dealerName).toList(),
                    top5.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                    null
            );
            double maxRate = top5.stream().mapToDouble(TaskBacklogMetric::backlogRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.DEALER,
                    top5.stream().map(TaskBacklogMetric::dealerName).toList(),
                    top5.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s \u79ef\u538b\u7387 %s\uff0c\u5171 %d \u6761\u672a\u5b8c\u6210\uff0c\u9ad8\u79ef\u538b\u901a\u5e38\u4f34\u968f\u5546\u673a\u63a8\u8fdb\u8282\u594f\u653e\u7f13".formatted(highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()), highestBacklog.backlogCount()),
                    "\u903e\u671f\u4efb\u52a1 %d \u6761\uff0c\u96c6\u4e2d\u51fa\u73b0\u5728\u79ef\u538b\u7387\u9ad8\u7684\u95e8\u5e97\uff0c\u8bf4\u660e\u90e8\u5206\u9500\u552e\u56e2\u961f\u7684\u6267\u884c\u7eaa\u5f8b\u5f85\u63d0\u5347".formatted(overdueCount)
            );

            List<String> recommendations = List.of(
                    "\u4f18\u5148\u6e05\u7406 %s \u7684\u5f85\u529e\u4e0e\u903e\u671f\u4efb\u52a1\uff0c\u786e\u4fdd\u9ad8\u610f\u5411\u5546\u673a\u8ddf\u8fdb\u4e0d\u6389\u961f".formatted(highestBacklog.dealerName()),
                    "\u5efa\u7acb\u6bcf\u65e5\u4efb\u52a1\u5b8c\u6210\u7387\u770b\u677f\uff0c\u5bf9\u79ef\u538b\u7387 >50%% \u7684\u95e8\u5e97\u542f\u52a8\u4e13\u9879\u7763\u5bfc"
            );

            List<String> followUps = List.of(
                    "\u8fd9\u4e2a\u8303\u56f4\u5185\u903e\u671f\u4efb\u52a1\u4e3b\u8981\u96c6\u4e2d\u5728\u54ea\u4e9b\u95e8\u5e97\uff1f",
                    "\u672a\u5b8c\u6210\u4efb\u52a1\u8f83\u591a\u7684\u95e8\u5e97\u76ee\u6807\u8fbe\u6210\u7387\u600e\u4e48\u6837\uff1f"
            );

            return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("\u9500\u552e\u8ddf\u8fdb\u5206\u6790", backlogMetrics.size(), "\u903e\u671f\u7387",
                            formatPercent(percentage((int) overdueCount, filtered.size())), "\u2014",
                            null, null,
                            highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate())),
                    mermaid, fallback, attributions, recommendations, followUps),
                    quality, traceSteps);
        }
        String conclusion = String.format(
                "- **%s** has the heaviest follow-up backlog at **%s** \u2014 the highest-risk dealer.\n"
                + "- %d tasks open out of %d total, with **%d overdue** \u2014 execution management needs immediate attention.\n"
                + "- The top 3 backlog-heavy dealers account for a disproportionate share of open tasks.",
                highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()),
                openTaskCount,
                filtered.size(), overdueCount
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total tasks", String.valueOf(filtered.size())});
        dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
        dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
        dataRows.add(new String[]{"Highest backlog dealer", "%s (%d / %d)".formatted(highestBacklog.dealerName(), highestBacklog.backlogCount(), highestBacklog.totalCount())});
        dataRows.add(new String[]{"Overdue rate", formatPercent(percentage((int) overdueCount, filtered.size()))});

        List<TaskBacklogMetric> top5En = backlogMetrics.stream().limit(3).toList();
        String mermaid = buildMermaidXyChart(
                "Follow-up Backlog \u2014 Top 3 %s".formatted(scope.summary(language)),
                "Dealer", "Open Rate (%)",
                ChartEntityType.DEALER,
                top5En.stream().map(TaskBacklogMetric::dealerName).toList(),
                top5En.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                null
        );
        double maxRateEn = top5En.stream().mapToDouble(TaskBacklogMetric::backlogRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.DEALER,
                top5En.stream().map(TaskBacklogMetric::dealerName).toList(),
                top5En.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s has a backlog rate of %s (%d open), which usually slows opportunity progression.".formatted(highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()), highestBacklog.backlogCount()),
                "%d overdue tasks are concentrated in high-backlog dealers, indicating execution discipline gaps.".formatted(overdueCount)
        );

        List<String> recommendations = List.of(
                "Clear pending and overdue tasks for %s first \u2014 protect high-intent opportunity follow-ups.".formatted(highestBacklog.dealerName()),
                "Set up a daily completion-rate dashboard and initiate focused supervision for dealers with >50%% backlog."
        );

        List<String> followUps = List.of(
                "Which dealers account for most of the overdue tasks in this scope?",
                "How does the target achievement of backlog-heavy dealers compare with others?"
        );

        return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                quality, traceSteps);
    }

    private ScenarioResult analyzeSalesFollowUpActivity(
            List<Task> filtered,
            AnalysisScope scope,
            String language,
            List<CalcStep> traceSteps,
            long openTaskCount,
            long overdueCount
    ) {
        return analyzeSalesFollowUpActivity(filtered, scope, language, traceSteps, openTaskCount, overdueCount, null, null, null);
    }

    private ScenarioResult analyzeSalesFollowUpActivity(
            List<Task> filtered,
            AnalysisScope scope,
            String language,
            List<CalcStep> traceSteps,
            long openTaskCount,
            long overdueCount,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep
    ) {
        long completedTaskCount = filtered.stream()
                .filter(task -> "Completed".equalsIgnoreCase(task.getStatus()))
                .count();
        List<TaskActivityMetric> activityMetrics = filtered.stream()
                .collect(Collectors.groupingBy(Task::getDealerName))
                .entrySet().stream()
                .map(entry -> {
                    List<Task> tasks = entry.getValue();
                    long completed = tasks.stream()
                            .filter(task -> "Completed".equalsIgnoreCase(task.getStatus()))
                            .count();
                    return new TaskActivityMetric(entry.getKey(), tasks.size(), completed,
                            percentage((int) completed, tasks.size()));
                })
                .sorted(Comparator.comparingDouble(TaskActivityMetric::completionRate).reversed()
                        .thenComparing(Comparator.comparingLong(TaskActivityMetric::completedCount).reversed())
                        .thenComparing(Comparator.comparingInt(TaskActivityMetric::totalCount).reversed())
                        .thenComparing(TaskActivityMetric::dealerName))
                .toList();

        return buildSalesFollowUpActivityResult(
                scope,
                language,
                traceSteps,
                filtered.size(),
                openTaskCount,
                overdueCount,
                completedTaskCount,
                activityMetrics,
                traceId, seq, onStep
        );
    }

    private ScenarioResult buildSalesFollowUpActivityResult(
            AnalysisScope scope,
            String language,
            List<CalcStep> traceSteps,
            int totalTasks,
            long openTaskCount,
            long overdueCount,
            long completedTaskCount,
            List<TaskActivityMetric> activityMetrics,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep
    ) {
        TaskActivityMetric highestActivity = activityMetrics.getFirst();

        traceSteps.add(new CalcStep(
                "\u6309 dealerName \u5206\u7ec4\u7edf\u8ba1\u8ddf\u8fdb\u5b8c\u6210\u7387",
                "Group by dealerName, compute follow-up completion rate",
                activityMetrics.size() + " \u4e2a\u95e8\u5e97\uff0c\u6d3b\u8dc3\u5ea6 = \u5df2\u5b8c\u6210\u4efb\u52a1\u6570 / \u4efb\u52a1\u603b\u6570",
                activityMetrics.size() + " dealers, activity = completed tasks / total tasks"
        ));
        if (onStep != null && seq != null) {
            emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "\u6309\u95e8\u5e97\u5206\u7ec4\u7edf\u8ba1\u8ddf\u8fdb\u5b8c\u6210\u7387" : "Group by dealer, compute completion rate",
                    isZh(language) ? activityMetrics.size() + " \u4e2a\u95e8\u5e97" : activityMetrics.size() + " dealers",
                    Map.of("dealerCount", activityMetrics.size())));
        }

        List<TaskActivityMetric> top3 = activityMetrics.stream().limit(3).toList();
        StringBuilder zhTop = new StringBuilder();
        StringBuilder enTop = new StringBuilder();
        for (int i = 0; i < top3.size(); i++) {
            TaskActivityMetric metric = top3.get(i);
            if (i > 0) {
                zhTop.append("\uff1b");
                enTop.append("; ");
            }
            zhTop.append(metric.dealerName()).append("\uff1a\u5b8c\u6210\u7387 ")
                    .append(formatPercent(metric.completionRate()))
                    .append("\uff08").append(metric.completedCount()).append("/")
                    .append(metric.totalCount()).append("\uff09");
            enTop.append(metric.dealerName()).append(": completion rate ")
                    .append(formatPercent(metric.completionRate()))
                    .append(" (").append(metric.completedCount()).append("/")
                    .append(metric.totalCount()).append(")");
        }

        traceSteps.add(new CalcStep(
                "\u6392\u5e8f\u627e\u51fa\u8ddf\u8fdb\u6d3b\u8dc3\u5ea6\u6700\u9ad8\u95e8\u5e97",
                "Sort to find highest-activity dealers",
                "Top 3\uff1a" + zhTop,
                "Top 3: " + enTop
        ));
        if (onStep != null && seq != null) {
            emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "\u6392\u5e8f\u627e\u51fa\u8ddf\u6d3b\u8dc3\u5ea6\u6700\u9ad8\u95e8\u5e97" : "Sort to find highest-activity dealers",
                    isZh(language) ? "Top 3\uff1a" + zhTop : "Top 3: " + enTop,
                    Map.of("topDealerCount", Math.min(3, activityMetrics.size()))));
        }

        DataQualityContext quality = classifyCountQuality(
                "sales follow-up",
                "Follow-up activity",
                totalTasks,
                activityMetrics.size(),
                3,
                (int) completedTaskCount,
                totalTasks,
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- **%s** \u8ddf\u8fdb\u6d3b\u8dc3\u5ea6\u6700\u9ad8\uff0c\u4efb\u52a1\u5b8c\u6210\u7387 **%s**\uff08%d/%d \u5df2\u5b8c\u6210\uff09\n"
                    + "- \u5f53\u524d %d \u6761\u4efb\u52a1\u4e2d %d \u6761\u5df2\u5b8c\u6210\uff0c%d \u6761\u672a\u5b8c\u6210\uff0c\u5176\u4e2d **%d \u6761\u5df2\u903e\u671f**\n"
                    + "- \u9ad8\u6d3b\u8dc3\u95e8\u5e97\u66f4\u9002\u5408\u4f5c\u4e3a\u8ddf\u8fdb\u8282\u594f\u548c\u4efb\u52a1\u95ed\u73af\u7684\u53c2\u7167\u6837\u672c",
                    highestActivity.dealerName(), formatPercent(highestActivity.completionRate()),
                    highestActivity.completedCount(), highestActivity.totalCount(),
                    totalTasks,
                    completedTaskCount,
                    openTaskCount,
                    overdueCount
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total tasks", String.valueOf(totalTasks)});
            dataRows.add(new String[]{"Completed tasks", String.valueOf(completedTaskCount)});
            dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
            dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
            dataRows.add(new String[]{"Highest activity dealer", "%s (%d / %d)".formatted(
                    highestActivity.dealerName(), highestActivity.completedCount(), highestActivity.totalCount())});
            dataRows.add(new String[]{"Completion rate", formatPercent(highestActivity.completionRate())});

            String mermaid = buildMermaidXyChart(
                    "%s \u8ddf\u8fdb\u6d3b\u8dc3\u5ea6 Top 3".formatted(scope.summary(language)),
                    "\u95e8\u5e97", "\u5b8c\u6210\u7387 (%)",
                    ChartEntityType.DEALER,
                    top3.stream().map(TaskActivityMetric::dealerName).toList(),
                    top3.stream().map(metric -> metric.completionRate()).collect(Collectors.toList()),
                    null
            );
            double maxRate = top3.stream().mapToDouble(TaskActivityMetric::completionRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.DEALER,
                    top3.stream().map(TaskActivityMetric::dealerName).toList(),
                    top3.stream().map(metric -> metric.completionRate()).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s \u5df2\u5b8c\u6210 %d/%d \u6761\u8ddf\u8fdb\u4efb\u52a1\uff0c\u5b8c\u6210\u7387 %s\uff0c\u662f\u5f53\u524d\u8303\u56f4\u5185\u6700\u9ad8\u6d3b\u8dc3\u53c2\u7167".formatted(
                            highestActivity.dealerName(), highestActivity.completedCount(), highestActivity.totalCount(),
                            formatPercent(highestActivity.completionRate())),
                    "\u79ef\u538b\u4fe1\u53f7\u4f5c\u4e3a\u8865\u5145\u80cc\u666f\u4fdd\u7559\uff1a\u5f53\u524d\u5171 %d \u6761\u672a\u5b8c\u6210\uff0c%d \u6761\u5df2\u903e\u671f".formatted(openTaskCount, overdueCount)
            );

            List<String> recommendations = List.of(
                    "\u62c6\u89e3 %s \u7684\u6bcf\u65e5\u8ddf\u8fdb\u8282\u594f\u548c\u4efb\u52a1\u95ed\u73af\u65b9\u5f0f\uff0c\u4f5c\u4e3a\u5176\u4ed6\u95e8\u5e97\u7684\u6267\u884c\u53c2\u7167".formatted(highestActivity.dealerName()),
                    "\u5c06\u9ad8\u6d3b\u8dc3\u95e8\u5e97\u7684\u5b8c\u6210\u7387\u4e0e\u903e\u671f\u7387\u4e00\u8d77\u590d\u76d8\uff0c\u907f\u514d\u5355\u7eaf\u7528\u79ef\u538b\u7387\u89e3\u91ca\u6d3b\u8dc3\u5ea6"
            );

            List<String> followUps = List.of(
                    "\u9ad8\u6d3b\u8dc3\u95e8\u5e97\u7684\u76ee\u6807\u8fbe\u6210\u7387\u662f\u5426\u4e5f\u66f4\u9ad8\uff1f",
                    "\u54ea\u4e9b\u95e8\u5e97\u540c\u65f6\u505a\u5230\u9ad8\u6d3b\u8dc3\u548c\u4f4e\u903e\u671f\uff1f"
            );

            return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    quality, traceSteps);
        }

        String conclusion = String.format(
                "- **%s** shows the strongest follow-up activity at **%s** completion (%d/%d tasks completed).\n"
                + "- %d of %d tasks are completed, while %d remain open and **%d are overdue**.\n"
                + "- The top 3 high-activity dealers are better references for follow-up cadence than backlog-heavy dealers.",
                highestActivity.dealerName(), formatPercent(highestActivity.completionRate()),
                highestActivity.completedCount(), highestActivity.totalCount(),
                completedTaskCount,
                totalTasks,
                openTaskCount,
                overdueCount
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total tasks", String.valueOf(totalTasks)});
        dataRows.add(new String[]{"Completed tasks", String.valueOf(completedTaskCount)});
        dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
        dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
        dataRows.add(new String[]{"Highest activity dealer", "%s (%d / %d)".formatted(
                highestActivity.dealerName(), highestActivity.completedCount(), highestActivity.totalCount())});
        dataRows.add(new String[]{"Completion rate", formatPercent(highestActivity.completionRate())});

        String mermaid = buildMermaidXyChart(
                "Follow-up Activity - Top 3 %s".formatted(scope.summary(language)),
                "Dealer", "Completion Rate (%)",
                ChartEntityType.DEALER,
                top3.stream().map(TaskActivityMetric::dealerName).toList(),
                top3.stream().map(metric -> metric.completionRate()).collect(Collectors.toList()),
                null
        );
        double maxRate = top3.stream().mapToDouble(TaskActivityMetric::completionRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.DEALER,
                top3.stream().map(TaskActivityMetric::dealerName).toList(),
                top3.stream().map(metric -> metric.completionRate()).collect(Collectors.toList()),
                Math.max(maxRate, 100)
        );

        List<String> attributions = List.of(
                "%s completed %d of %d follow-up tasks (%s), making it the strongest activity reference.".formatted(
                        highestActivity.dealerName(), highestActivity.completedCount(), highestActivity.totalCount(),
                        formatPercent(highestActivity.completionRate())),
                "Backlog remains a separate context signal: %d open tasks and %d overdue tasks across the scope.".formatted(openTaskCount, overdueCount)
        );

        List<String> recommendations = List.of(
                "Use %s as the reference for daily follow-up cadence and task closure habits.".formatted(highestActivity.dealerName()),
                "Review completion rate and overdue rate together before using backlog cleanup as the activity explanation."
        );

        List<String> followUps = List.of(
                "Do high-activity dealers also have higher target achievement?",
                "Which dealers combine high activity with low overdue counts?"
        );

        return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                quality, traceSteps);
    }

    private ScenarioResult analyzeCampaignPerformance(AnalysisScope scope, String language) {
        return analyzeCampaignPerformance(scope, language, null, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeCampaignPerformance(AnalysisScope scope, String language, String message) {
        return analyzeCampaignPerformance(scope, language, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeCampaignPerformance(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        return analyzeCampaignPerformance(scope, language, null, traceId, seq, onStep);
    }

    private ScenarioResult analyzeCampaignPerformance(AnalysisScope scope, String language, String message,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        ScenarioResult directResult = tryAnswerCampaignQuestion(scope, language, message);
        if (directResult != null) {
            return directResult;
        }

        DataQueryResponse response = dataQueryService.query("campaigns", buildQueryFilters(scope, true));
        if (!response.items().isEmpty()) {
            return analyzeCampaignPerformanceFromQuery(response, scope, language, traceId, seq, onStep);
        }

        List<Campaign> allCampaigns = cachedCampaigns();
        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "从 Campaign 表加载全部市场活动数据",
                "Load all campaign records from the Campaign table",
                "共 " + allCampaigns.size() + " 条记录",
                allCampaigns.size() + " records total"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Campaign 表加载全部市场活动数据" : "Load all campaign records from Campaign table",
                isZh(language) ? "共 " + allCampaigns.size() + " 条记录" : allCampaigns.size() + " records total",
                Map.of("source_type", "database", "table", "Campaign", "recordCount", allCampaigns.size())));

        List<Campaign> filtered = allCampaigns.stream()
                .filter(campaign -> matchesScope(campaign.getDealerCode(), campaign.getDealerName(), campaign.getCity(),
                        campaign.getDealerGroupName(), campaign.getProductModel(), scope))
                .filter(campaign -> scope.timeRange().matchesDate(campaign.getCreatedDate()))
                .toList();

        traceSteps.add(new CalcStep(
                "按分析范围过滤（城市/门店/时间）",
                "Filter by analysis scope (city/dealer/time)",
                "匹配 " + filtered.size() + " 条记录",
                filtered.size() + " matching records"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + filtered.size() + " 条记录" : filtered.size() + " matching records",
                filterAuditMeta(scope, language, allCampaigns.size(), filtered.size())));

        if (filtered.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "市场活动分析无可用数据" : "No data for campaign performance analysis",
                    isZh(language) ? "过滤后无匹配记录" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "campaign performance");
        }

        List<CampaignMetric> allMetrics = filtered.stream()
                .map(campaign -> new CampaignMetric(
                        campaign.getCampaignId(),
                        campaign.getDealerName(),
                        campaign.getCampaignType(),
                        campaign.getActualOpportunityCount(),
                        campaign.getTotalNewCustomerTarget(),
                        percentage(campaign.getActualOpportunityCount(), campaign.getTotalNewCustomerTarget())
                ))
                .toList();
        List<CampaignMetric> validMetrics = allMetrics.stream()
                .filter(metric -> metric.totalNewCustomerTarget() > 0)
                .toList();
        int primaryNumerator = validMetrics.stream().mapToInt(CampaignMetric::actualOpportunityCount).sum();
        int primaryDenominator = validMetrics.stream().mapToInt(CampaignMetric::totalNewCustomerTarget).sum();
        DataQualityContext quality = classifyRateQuality(
                "campaign performance",
                "Campaign attainment",
                filtered.size(),
                validMetrics.size(),
                2,
                primaryNumerator,
                primaryDenominator,
                allMetrics.size() - validMetrics.size(),
                validMetrics.size() < 2 ? 0.0 : validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(0.0)
                        - validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).min().orElse(0.0),
                true
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

        List<CampaignMetric> metrics = validMetrics.stream()
                .sorted(Comparator.comparingDouble(CampaignMetric::attainmentRate).reversed())
                .toList();

        CampaignMetric best = metrics.getFirst();
        double averageAttainment = metrics.stream().mapToDouble(CampaignMetric::attainmentRate).average().orElse(0.0);

        traceSteps.add(new CalcStep(
                "按 campaignType 分组计算活动达成率",
                "Group by campaignType, compute attainment rate",
                validMetrics.size() + " 个活动，达成率 = actualOpportunityCount / totalNewCustomerTarget",
                validMetrics.size() + " campaigns, attainment rate = actualOpportunityCount / totalNewCustomerTarget"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按活动类型分组计算达成率" : "Group by campaign type, compute attainment rate",
                isZh(language)
                        ? validMetrics.size() + " 个活动，达成率 = actualOpportunityCount / totalNewCustomerTarget"
                        : validMetrics.size() + " campaigns, attainmentRate = actualOpportunityCount / totalNewCustomerTarget",
                auditMap(
                        "formula", "attainmentRate = actualOpportunityCount / totalNewCustomerTarget",
                        "inputRecordCount", filtered.size(),
                        "campaignCount", validMetrics.size(),
                        "totalActualOpportunityCount", primaryNumerator,
                        "totalNewCustomerTarget", primaryDenominator,
                        "excludedZeroTargetCount", allMetrics.size() - validMetrics.size(),
                        "sampleRows", campaignMetricRows(validMetrics, 5)
                )));

        traceSteps.add(new CalcStep(
                "排序找出最佳活动并计算平均值",
                "Sort to find best campaign and average",
                "最佳：" + best.campaignId() + "（" + best.dealerName() + "，" + formatPercent(best.attainmentRate()) + "）；平均达成率 " + formatPercent(averageAttainment),
                "Best: " + best.campaignId() + " (" + best.dealerName() + ", " + formatPercent(best.attainmentRate()) + "); Average: " + formatPercent(averageAttainment)
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "排序找出最佳活动" : "Sort to find best campaign",
                isZh(language) ? "最佳: " + best.campaignId() + ", 平均: " + formatPercent(averageAttainment) : "Best: " + best.campaignId() + ", Average: " + formatPercent(averageAttainment),
                Map.of("bestCampaign", best.campaignId())));

                if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- \u5f53\u524d\u6d3b\u52a8\u6548\u679c\u6700\u597d\u7684\u662f **%s** \u7684 **%s**\uff0c\u8fbe\u6210\u7387 **%s**\uff0c\u8fdc\u8d85\u5e73\u5747\u7ebf\n"
                    + "- \u6574\u4f53\u5e73\u5747\u8fbe\u6210\u7387 **%s**\uff0c%d \u573a\u6d3b\u52a8\u4e2d\u6709 %d \u573a\u4f4e\u4e8e\u5e73\u5747\u7ebf\uff0c\u6d3b\u52a8\u8d28\u91cf\u5206\u5316\u660e\u663e\n"
                    + "- \u6700\u4f73\u6d3b\u52a8\u6253\u6cd5\u503c\u5f97\u590d\u76d8\u548c\u6a2a\u5411\u590d\u5236\uff0c\u4f18\u5148\u63a8\u5e7f\u5230\u76f8\u8fd1\u57ce\u5e02\u6216\u540c\u8f66\u578b\u95e8\u5e97",
                    best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate()),
                    formatPercent(averageAttainment),
                    metrics.size(),
                    metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count()
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Campaign count", String.valueOf(metrics.size())});
            dataRows.add(new String[]{"Best campaign", "%s (%d / %d)".formatted(best.campaignId(), best.actualOpportunityCount(), best.totalNewCustomerTarget())});
            dataRows.add(new String[]{"Average attainment", formatPercent(averageAttainment)});
            dataRows.add(new String[]{"Campaigns below average", String.valueOf(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())});

            List<CampaignMetric> top5 = metrics.stream().limit(3).toList();
            String mermaid = buildMermaidXyChart(
                    "%s \u6d3b\u52a8\u8fbe\u6210\u7387 Top 3".formatted(scope.summary(language)),
                    "\u6d3b\u52a8", "\u8fbe\u6210\u7387 (%)",
                    ChartEntityType.CAMPAIGN,
                    top5.stream().map(CampaignMetric::campaignId).toList(),
                    top5.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                    averageAttainment
            );
            double maxRate = top5.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.CAMPAIGN,
                    top5.stream().map(CampaignMetric::campaignId).toList(),
                    top5.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s \u7684 %s \u8fbe\u6210\u7387\u9ad8\u8fbe %s\uff0c\u63a8\u6d4b\u5176\u7ebf\u7d22\u9080\u7ea6\u3001\u73b0\u573a\u8f6c\u5316\u6d41\u7a0b\u548c\u8d44\u6e90\u914d\u7f6e\u66f4\u4f18".formatted(best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate())),
                    "\u4f4e\u4e8e\u5e73\u5747\u7ebf\u7684 %d \u573a\u6d3b\u52a8\u4e3b\u8981\u96c6\u4e2d\u5728\u6267\u884c\u529b\u5ea6\u5f31\u6216\u7ebf\u7d22\u83b7\u53d6\u80fd\u529b\u4e0d\u8db3\u7684\u95e8\u5e97".formatted(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())
            );

            List<String> recommendations = List.of(
                    "\u590d\u7528 %s \u7684\u6d3b\u52a8\u6253\u6cd5\uff0c\u4f18\u5148\u590d\u5236\u5230\u76f8\u8fd1\u57ce\u5e02\u6216\u540c\u8f66\u578b\u95e8\u5e97\uff0c\u9884\u8ba1\u53ef\u5c06\u6574\u4f53\u5e73\u5747\u8fbe\u6210\u7387\u62c9\u5347 10 \u4e2a\u767e\u5206\u70b9".formatted(best.dealerName()),
                    "\u5bf9\u6bd4\u4f4e\u4e8e\u5e73\u5747\u7ebf\u7684\u6d3b\u52a8\uff0c\u627e\u51fa\u7ebf\u7d22\u83b7\u53d6\u548c\u73b0\u573a\u8f6c\u5316\u7684\u4e3b\u8981\u77ed\u677f\u5e76\u9010\u9879\u6539\u8fdb"
            );

            List<String> followUps = List.of(
                    "\u54ea\u4e9b\u6d3b\u52a8\u8fbe\u6210\u7387\u4f4e\u4e8e\u5e73\u5747\u6c34\u5e73\uff1f",
                    "\u8868\u73b0\u6700\u597d\u7684\u6d3b\u52a8\u4e3b\u8981\u5e26\u6765\u4e86\u54ea\u4e9b\u8f66\u578b\u7684\u5546\u673a\uff1f"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    "campaign performance", "Campaign attainment", traceSteps);
        }
        String conclusion = String.format(
                "- **%s**'s **%s** leads with **%s** attainment, far above the average.\n"
                + "- Overall average is **%s**; %d out of %d campaigns fall below it \u2014 quality is uneven.\n"
                + "- The winning playbook is worth replicating across similar cities and product lines.",
                best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate()),
                formatPercent(averageAttainment),
                metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count(),
                metrics.size()
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Campaign count", String.valueOf(metrics.size())});
        dataRows.add(new String[]{"Best campaign", "%s (%d / %d)".formatted(best.campaignId(), best.actualOpportunityCount(), best.totalNewCustomerTarget())});
        dataRows.add(new String[]{"Average attainment", formatPercent(averageAttainment)});
        dataRows.add(new String[]{"Campaigns below average", String.valueOf(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())});

        List<CampaignMetric> top5En = metrics.stream().limit(3).toList();
        String mermaid = buildMermaidXyChart(
                "Campaign Attainment \u2014 Top 3 %s".formatted(scope.summary(language)),
                "Campaign", "Attainment (%)",
                ChartEntityType.CAMPAIGN,
                top5En.stream().map(CampaignMetric::campaignId).toList(),
                top5En.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                averageAttainment
        );
        double maxRateEn = top5En.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.CAMPAIGN,
                top5En.stream().map(CampaignMetric::campaignId).toList(),
                top5En.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s's %s achieved %s attainment \u2014 likely benefiting from stronger invitation and on-site conversion.".formatted(best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate())),
                "The %d below-average campaigns are concentrated in dealers with weaker execution or lead acquisition.".formatted(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())
        );

        List<String> recommendations = List.of(
                "Replicate %s's campaign playbook to similar cities or product lines \u2014 targeting a 10 pp average uplift.".formatted(best.dealerName()),
                "Review the below-average campaigns to identify specific gaps in lead acquisition and on-site conversion."
        );

        List<String> followUps = List.of(
                "Which campaigns are currently below the average attainment rate?",
                "Which product models benefited most from the best-performing campaign?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "campaign performance", "Campaign attainment", traceSteps);
    }

    private ScenarioResult analyzeLeadSource(AnalysisScope scope, String language) {
        return analyzeLeadSource(scope, language, null, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeLeadSource(AnalysisScope scope, String language, String message) {
        return analyzeLeadSource(scope, language, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeLeadSource(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        return analyzeLeadSource(scope, language, null, traceId, seq, onStep);
    }

    private ScenarioResult analyzeLeadSource(AnalysisScope scope, String language, String message,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        ScenarioResult directResult = tryAnswerLeadQuestion(scope, language, message);
        if (directResult != null) {
            return directResult;
        }

        DataQueryResponse response = dataQueryService.query("leads", buildQueryFilters(scope, true));
        if (!response.items().isEmpty()) {
            return analyzeLeadSourceFromQuery(response, scope, language, traceId, seq, onStep);
        }

        List<Lead> allLeads = cachedLeads();
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Lead 表加载全部线索数据" : "Load all lead records from Lead table",
                isZh(language) ? "共 " + allLeads.size() + " 条记录" : allLeads.size() + " records total",
                Map.of("source_type", "database", "table", "Lead", "recordCount", allLeads.size())));

        List<Lead> filtered = allLeads.stream()
                .filter(lead -> matchesScope(lead.getDealerCode(), lead.getDealerName(), lead.getCity(),
                        lead.getDealerGroupName(), lead.getProductModel(), scope))
                .filter(lead -> scope.timeRange().matchesDate(lead.getCreatedDate()))
                .filter(lead -> scope.leadSource() == null
                        || scope.leadSource().equalsIgnoreCase(lead.getLeadSource()))
                .toList();

        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + filtered.size() + " 条记录" : filtered.size() + " matching records",
                filterAuditMeta(scope, language, allLeads.size(), filtered.size())));

        if (filtered.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "线索来源分析无可用数据" : "No data for lead source analysis",
                    isZh(language) ? "过滤后无匹配记录" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "lead source analysis");
        }

        List<LeadSourceMetric> sourceMetrics = filtered.stream()
                .collect(Collectors.groupingBy(Lead::getLeadSource))
                .entrySet().stream()
                .map(entry -> {
                    List<Lead> leads = entry.getValue();
                    long convertedCount = leads.stream().filter(Lead::getConverted).count();
                    return new LeadSourceMetric(entry.getKey(), leads.size(), convertedCount,
                            percentage((int) convertedCount, leads.size()));
                })
                .sorted(Comparator.comparingInt(LeadSourceMetric::leadCount).reversed())
                .toList();
        int convertedTotal = (int) sourceMetrics.stream().mapToLong(LeadSourceMetric::convertedCount).sum();
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按线索来源分组统计转化率" : "Group by lead source, compute conversion rate",
                isZh(language)
                        ? sourceMetrics.size() + " 个来源, " + filtered.size() + " 条线索，转化率 = convertedCount / leadCount"
                        : sourceMetrics.size() + " sources, " + filtered.size() + " leads, conversionRate = convertedCount / leadCount",
                auditMap(
                        "formula", "conversionRate = convertedCount / leadCount",
                        "sourceCount", sourceMetrics.size(),
                        "leadCount", filtered.size(),
                        "convertedCount", convertedTotal,
                        "sampleRows", leadSourceMetricRows(sourceMetrics, 5)
                )));

        Map<String, Long> statusCounts = filtered.stream()
                .collect(Collectors.groupingBy(lead -> {
                    String stage = lead.getStageName();
                    return stage != null && !stage.isBlank() ? stage : "Unknown";
                }, LinkedHashMap::new, Collectors.counting()));
        long newCount = statusCounts.getOrDefault("New", 0L);
        long qualifiedCount = statusCounts.getOrDefault("Qualified", 0L);

        Map<String, Long> modelCounts = filtered.stream()
                .collect(Collectors.groupingBy(lead -> {
                    String model = lead.getProductModel();
                    return model != null && !model.isBlank() ? model : "Unknown";
                }, LinkedHashMap::new, Collectors.counting()));
        String topLeadModel = modelCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");

        Map<String, List<Lead>> dealerLeads = filtered.stream()
                .filter(lead -> lead.getDealerName() != null && !lead.getDealerName().isBlank())
                .collect(Collectors.groupingBy(Lead::getDealerName));
        record DealerLeadMetric(String name, int total, int converted, double rate) {}
        List<DealerLeadMetric> dealerMetrics = dealerLeads.entrySet().stream()
                .map(e -> {
                    int total = e.getValue().size();
                    int converted = (int) e.getValue().stream().filter(Lead::getConverted).count();
                    return new DealerLeadMetric(e.getKey(), total, converted, percentage(converted, total));
                })
                .sorted(Comparator.comparingInt(DealerLeadMetric::total).reversed())
                .toList();
        DealerLeadMetric topDealerByVolume = dealerMetrics.isEmpty() ? null : dealerMetrics.getFirst();
        DealerLeadMetric topDealerByConversion = dealerMetrics.stream()
                .max(Comparator.comparingDouble(DealerLeadMetric::rate))
                .orElse(null);

        DataQualityContext quality = classifyCountQuality(
                "lead source analysis",
                "Lead conversion",
                filtered.size(),
                sourceMetrics.size(),
                3,
                convertedTotal,
                filtered.size(),
                true
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, new ArrayList<>());
        }

        LeadSourceMetric topVolume = sourceMetrics.getFirst();
        LeadSourceMetric topConversion = sourceMetrics.stream()
                .max(Comparator.comparingDouble(LeadSourceMetric::conversionRate))
                .orElse(topVolume);

                if ("zh".equals(language)) {
            String conclusion;
            StringBuilder zhConclusion = new StringBuilder();
            zhConclusion.append(String.format(
                    "- **%s** \u662f\u5f53\u524d\u7ebf\u7d22\u91cf\u6700\u5927\u7684\u6765\u6e90\uff08%d \u6761\uff0c\u5360 %s\uff09\uff0c\u4f46\u8f6c\u5316\u7387\u4ec5 **%s**\n",
                    topVolume.source(), topVolume.leadCount(),
                    formatPercent(percentage(topVolume.leadCount(), filtered.size())),
                    formatPercent(topVolume.conversionRate())));
            zhConclusion.append(String.format(
                    "- **%s** \u8f6c\u5316\u7387\u6700\u9ad8\uff08**%s**\uff09\uff0c\u867d\u7136\u7ebf\u7d22\u91cf\u4ec5 %d \u6761\uff0c\u4f46\u8d28\u91cf\u8fdc\u8d85\u5176\u4ed6\u6765\u6e90\n",
                    topConversion.source(), formatPercent(topConversion.conversionRate()),
                    topConversion.leadCount()));
            zhConclusion.append(String.format(
                    "- \u7ebf\u7d22\u72b6\u6001\u5206\u5e03\uff1aNew %d \u6761\uff0cQualified %d \u6761\n",
                    newCount, qualifiedCount));
            zhConclusion.append(String.format(
                    "- \u7ebf\u7d22\u6700\u591a\u7684\u610f\u5411\u8f66\u578b\uff1a**%s**\uff08%d \u6761\uff09\n",
                    topLeadModel, modelCounts.getOrDefault(topLeadModel, 0L)));
            if (topDealerByVolume != null) {
                zhConclusion.append(String.format(
                        "- \u7ebf\u7d22\u6700\u591a\u7684\u7ecf\u9500\u5546\uff1a**%s**\uff08%d \u6761\uff09\n",
                        topDealerByVolume.name(), topDealerByVolume.total()));
            }
            if (topDealerByConversion != null) {
                zhConclusion.append(String.format(
                        "- \u8f6c\u5316\u7387\u6700\u9ad8\u7684\u7ecf\u9500\u5546\uff1a**%s**\uff08%s\uff09",
                        topDealerByConversion.name(), formatPercent(topDealerByConversion.rate())));
            }
            conclusion = zhConclusion.toString();

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total leads", String.valueOf(filtered.size())});
            dataRows.add(new String[]{"New leads", String.valueOf(newCount)});
            dataRows.add(new String[]{"Qualified leads", String.valueOf(qualifiedCount)});
            dataRows.add(new String[]{"Top lead source by volume", "%s (%d leads)".formatted(topVolume.source(), topVolume.leadCount())});
            dataRows.add(new String[]{"Top lead source by conversion", "%s (%s)".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))});
            dataRows.add(new String[]{"Top lead model", "%s (%d leads)".formatted(topLeadModel, modelCounts.getOrDefault(topLeadModel, 0L))});
            if (topDealerByVolume != null) {
                dataRows.add(new String[]{"Top dealer by leads", "%s (%d leads)".formatted(topDealerByVolume.name(), topDealerByVolume.total())});
            }
            if (topDealerByConversion != null) {
                dataRows.add(new String[]{"Top dealer by conversion", "%s (%s)".formatted(topDealerByConversion.name(), formatPercent(topDealerByConversion.rate()))});
            }

            Map<String, Double> pieData = new LinkedHashMap<>();
            for (LeadSourceMetric metric : sourceMetrics) {
                pieData.put(metric.source(), (double) metric.leadCount());
            }
            String mermaid = buildMermaidPie(
                    "%s \u7ebf\u7d22\u6765\u6e90\u5206\u5e03".formatted(scope.summary(language)),
                    ChartEntityType.SOURCE,
                    pieData
            );
            double maxVal = sourceMetrics.stream().mapToDouble(LeadSourceMetric::leadCount).max().orElse(1);
            String fallback = buildFallbackBars(
                    ChartEntityType.SOURCE,
                    sourceMetrics.stream().map(LeadSourceMetric::source).toList(),
                    sourceMetrics.stream().map(m -> (double) m.leadCount()).collect(Collectors.toList()),
                    maxVal
            );

            List<String> attributions = List.of(
                    "%s \u7ebf\u7d22\u91cf\u6700\u5927\u4f46\u8f6c\u5316\u7387\u4ec5 %s\uff0c\u91cf\u5927\u4e0d\u4e00\u5b9a\u4ee3\u8868\u8d28\u4f18\uff0c\u9700\u8981\u540c\u65f6\u770b\u7ebf\u7d22\u89c4\u6a21\u548c\u8f6c\u5316\u6548\u7387".formatted(topVolume.source(), formatPercent(topVolume.conversionRate())),
                    "%s \u8f6c\u5316\u7387\u6700\u9ad8\u8fbe %s\uff0c\u5176\u7ebf\u7d22\u8ddf\u8fdb\u7684\u6d41\u7a0b\u548c\u8bdd\u672f\u503c\u5f97\u63d0\u70bc\u4e3a\u53ef\u590d\u5236\u7684\u6253\u6cd5".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))
            );

            List<String> recommendations = List.of(
                    "\u7ee7\u7eed\u653e\u5927 %s \u7684\u83b7\u5ba2\u89c4\u6a21\uff0c\u540c\u65f6\u5efa\u7acb\u8f6c\u5316\u7387\u5468\u5ea6\u76d1\u63a7\uff0c\u786e\u4fdd\u91cf\u4ef7\u9f50\u5347".formatted(topVolume.source()),
                    "\u4f18\u5148\u590d\u76d8 %s \u7684\u8f6c\u5316\u8def\u5f84\uff0c\u63d0\u70bc\u6210\u53ef\u590d\u5236\u7684\u7ebf\u7d22\u8ddf\u8fdb SOP \u63a8\u5e7f\u5230\u5176\u4ed6\u6765\u6e90".formatted(topConversion.source())
            );

            List<String> followUps = List.of(
                    "\u7f51\u7ad9\u6765\u6e90\u7ebf\u7d22\u5728\u5f53\u524d\u8303\u56f4\u5185\u7684\u8f6c\u5316\u8868\u73b0\u5982\u4f55\uff1f",
                    "\u54ea\u4e9b\u95e8\u5e97\u6700\u4f9d\u8d56\u5355\u4e00\u7ebf\u7d22\u6765\u6e90\uff1f"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    "lead source analysis", "Lead conversion", new ArrayList<>());
        }
        StringBuilder enConclusion = new StringBuilder();
        enConclusion.append(String.format(
                "- **%s** delivers the most leads (%d, %s of total), but converts at only **%s**.\n",
                topVolume.source(), topVolume.leadCount(),
                formatPercent(percentage(topVolume.leadCount(), filtered.size())),
                formatPercent(topVolume.conversionRate())));
        enConclusion.append(String.format(
                "- **%s** has the best conversion rate at **%s** \u2014 only %d leads, but far higher quality.\n",
                topConversion.source(), formatPercent(topConversion.conversionRate()),
                topConversion.leadCount()));
        enConclusion.append(String.format(
                "- Status distribution: New %d, Qualified %d\n",
                newCount, qualifiedCount));
        enConclusion.append(String.format(
                "- Most leads by model: **%s** (%d leads)\n",
                topLeadModel, modelCounts.getOrDefault(topLeadModel, 0L)));
        if (topDealerByVolume != null) {
            enConclusion.append(String.format(
                    "- Most leads by dealer: **%s** (%d leads)\n",
                    topDealerByVolume.name(), topDealerByVolume.total()));
        }
        if (topDealerByConversion != null) {
            enConclusion.append(String.format(
                    "- Best conversion by dealer: **%s** (%s)",
                    topDealerByConversion.name(), formatPercent(topDealerByConversion.rate())));
        }
        String conclusion = enConclusion.toString();

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total leads", String.valueOf(filtered.size())});
        dataRows.add(new String[]{"New leads", String.valueOf(newCount)});
        dataRows.add(new String[]{"Qualified leads", String.valueOf(qualifiedCount)});
        dataRows.add(new String[]{"Top lead source by volume", "%s (%d leads)".formatted(topVolume.source(), topVolume.leadCount())});
        dataRows.add(new String[]{"Top lead source by conversion", "%s (%s)".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))});
        dataRows.add(new String[]{"Top lead model", "%s (%d leads)".formatted(topLeadModel, modelCounts.getOrDefault(topLeadModel, 0L))});
        if (topDealerByVolume != null) {
            dataRows.add(new String[]{"Top dealer by leads", "%s (%d leads)".formatted(topDealerByVolume.name(), topDealerByVolume.total())});
        }
        if (topDealerByConversion != null) {
            dataRows.add(new String[]{"Top dealer by conversion", "%s (%s)".formatted(topDealerByConversion.name(), formatPercent(topDealerByConversion.rate()))});
        }

        Map<String, Double> pieData = new LinkedHashMap<>();
        for (LeadSourceMetric metric : sourceMetrics) {
            pieData.put(metric.source(), (double) metric.leadCount());
        }
        String mermaid = buildMermaidPie(
                "Lead Source Distribution \u2014 %s".formatted(scope.summary(language)),
                ChartEntityType.SOURCE,
                pieData
        );
        double maxVal = sourceMetrics.stream().mapToDouble(LeadSourceMetric::leadCount).max().orElse(1);
        String fallback = buildFallbackBars(
                ChartEntityType.SOURCE,
                sourceMetrics.stream().map(LeadSourceMetric::source).toList(),
                sourceMetrics.stream().map(m -> (double) m.leadCount()).collect(Collectors.toList()),
                maxVal
        );

        List<String> attributions = List.of(
                "%s has the highest volume but only %s conversion \u2014 scale does not guarantee quality.".formatted(topVolume.source(), formatPercent(topVolume.conversionRate())),
                "%s converts at %s \u2014 its follow-up process and scripts can be codified into a repeatable playbook.".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))
        );

        List<String> recommendations = List.of(
                "Keep scaling %s while instituting weekly conversion monitoring to ensure quality keeps pace.".formatted(topVolume.source()),
                "Codify %s's conversion path into a repeatable SOP and roll it out to other sources.".formatted(topConversion.source())
        );

        List<String> followUps = List.of(
                "How does website-sourced lead conversion perform in this scope?",
                "Which dealers rely too heavily on a single lead source?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "lead source analysis", "Lead conversion", new ArrayList<>());
    }

    private ScenarioResult analyzeOpportunityFunnelFromQuery(DataQueryResponse response, AnalysisScope scope, String language) {
        List<Map<String, Object>> items = response.items();
        Map<String, Long> stageCounts = items.stream()
                .collect(Collectors.groupingBy(item -> stringValue(item, "stageName"), LinkedHashMap::new, Collectors.counting()));
        long wonCount = items.stream().filter(item -> "Won".equalsIgnoreCase(stringValue(item, "stageName"))).count();
        long lostCount = items.stream().filter(item -> "Lost".equalsIgnoreCase(stringValue(item, "stageName"))).count();
        long highProbabilityCount = items.stream()
                .filter(item -> intValue(item, "probability") >= 60 && !"Won".equalsIgnoreCase(stringValue(item, "stageName")))
                .count();
        int totalOpportunities = aggregateValue(response, "totalCount");

        String topStage = stageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
        String topLeadSource = items.stream()
                .collect(Collectors.groupingBy(item -> stringValue(item, "leadSource"), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");

        DataQualityContext quality = classifyCountQuality(
                "opportunity funnel",
                "Opportunity count",
                totalOpportunities,
                totalOpportunities >= 3 ? 2 : 1,
                3,
                (int) wonCount,
                totalOpportunities,
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            return lowConfidenceResult(language, scope, quality, new ArrayList<>());
        }

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- 当前共有 **%d** 条商机，主力阶段集中在 **%s**，赢单率 **%s**\n"
                    + "- 仍有 **%d** 条高概率商机（≥60%%）值得重点推动，短期赢单转化空间明确\n"
                    + "- 漏斗中后段积压明显，%s 阶段占 %d%%，需排查推进瓶颈",
                    totalOpportunities, topStage,
                    formatPercent(percentage((int) wonCount, totalOpportunities)),
                    highProbabilityCount,
                    topStage,
                    (int) (stageCounts.getOrDefault(topStage, 0L) * 100 / totalOpportunities)
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total opportunities", String.valueOf(totalOpportunities)});
            dataRows.add(new String[]{"Won opportunities", String.valueOf(wonCount)});
            dataRows.add(new String[]{"Lost opportunities", String.valueOf(lostCount)});
            dataRows.add(new String[]{"High-probability open opportunities", String.valueOf(highProbabilityCount)});
            dataRows.add(new String[]{"Primary lead source", topLeadSource});
            dataRows.add(new String[]{"Win rate", formatPercent(percentage((int) wonCount, totalOpportunities))});

            Map<String, Double> pieData = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : stageCounts.entrySet()) {
                pieData.put(entry.getKey(), (double) entry.getValue());
            }
            String mermaid = buildMermaidPie(
                    "%s 商机阶段分布".formatted(scope.summary(language)),
                    pieData
            );
            String fallback = buildFallbackBars(
                    new ArrayList<>(pieData.keySet()),
                    pieData.values().stream().toList(),
                    pieData.values().stream().mapToDouble(Double::doubleValue).max().orElse(1)
            );

            List<String> attributions = List.of(
                    "当前漏斗 %s 阶段积压最多（%d 条），说明该阶段的推进效率存在瓶颈".formatted(topStage, stageCounts.getOrDefault(topStage, 0L)),
                    "丢单 %d 条，主要来自中间阶段，结合商机卡点分析可定位流失原因".formatted(lostCount),
                    "主要线索来源 %s 贡献了最大商机量，但其转化效率还需跟最终赢单率对照评估".formatted(topLeadSource)
            );

            List<String> recommendations = List.of(
                    "按阶段盘点 %s 的商机卡点，优先解决该阶段的推进瓶颈，预期释放积压商机的 30%%".formatted(topStage),
                    "追踪来自 %s 的商机转化效率，若赢单率持续偏低则调整投放策略".formatted(topLeadSource),
                    "重点跟进 %d 条高概率商机，预计 2 周内可转化为 %d 条新增赢单".formatted(highProbabilityCount, (int)(highProbabilityCount * 0.3))
            );

            List<String> followUps = List.of(
                    "这个范围内高概率商机主要集中在哪些门店？",
                    "当前漏斗里丢单最多的阶段是什么？"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("商机漏斗分析", totalOpportunities, "赢单率",
                            formatPercent(percentage((int) wonCount, totalOpportunities)), "—",
                            null, null, null, null),
                    mermaid, fallback, attributions, recommendations, followUps),
                    "opportunity funnel", "Opportunity count", new ArrayList<>());
        }
        String conclusion = String.format(
                "- **%d** opportunities in scope, concentrated in the **%s** stage with a win rate of **%s**.\n"
                + "- **%d** high-probability opportunities (≥60%%) are open — clear near-term conversion upside.\n"
                + "- %s stage holds %d%% of the funnel, suggesting a bottleneck worth investigating.",
                totalOpportunities, topStage, formatPercent(percentage((int) wonCount, totalOpportunities)),
                highProbabilityCount,
                topStage, (int) (stageCounts.getOrDefault(topStage, 0L) * 100 / totalOpportunities)
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total opportunities", String.valueOf(totalOpportunities)});
        dataRows.add(new String[]{"Won opportunities", String.valueOf(wonCount)});
        dataRows.add(new String[]{"Lost opportunities", String.valueOf(lostCount)});
        dataRows.add(new String[]{"High-probability open opportunities", String.valueOf(highProbabilityCount)});
        dataRows.add(new String[]{"Primary lead source", topLeadSource});
        dataRows.add(new String[]{"Win rate", formatPercent(percentage((int) wonCount, totalOpportunities))});

        Map<String, Double> pieData = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : stageCounts.entrySet()) {
            pieData.put(entry.getKey(), (double) entry.getValue());
        }
        String mermaid = buildMermaidPie(
                "Opportunity Stage — %s".formatted(scope.summary(language)),
                pieData
        );
        String fallback = buildFallbackBars(
                new ArrayList<>(pieData.keySet()),
                pieData.values().stream().toList(),
                pieData.values().stream().mapToDouble(Double::doubleValue).max().orElse(1)
        );

        List<String> attributions = List.of(
                "The %s stage holds %d opportunities — the largest accumulation, indicating a progression bottleneck.".formatted(topStage, stageCounts.getOrDefault(topStage, 0L)),
                "%d lost deals, mostly from mid-stages — review blocking points to identify churn causes.".formatted(lostCount),
                "%s is the dominant lead source by volume, but its win-rate should be benchmarked against cost.".formatted(topLeadSource)
        );

        List<String> recommendations = List.of(
                "Review blocking points in the %s stage — target releasing 30%% of the backlog.".formatted(topStage),
                "Track conversion efficiency of %s-sourced opportunities; adjust spend if win rates stay low.".formatted(topLeadSource),
                "Focus follow-up on the %d high-probability opportunities — expect %d additional wins in 2 weeks.".formatted(highProbabilityCount, (int)(highProbabilityCount * 0.3))
        );

        List<String> followUps = List.of(
                "Which dealers hold most of the high-probability pipeline in this scope?",
                "Which stage currently contributes the most lost opportunities?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "opportunity funnel", "Opportunity count", new ArrayList<>());
    }

    private ScenarioResult analyzeSalesFollowUpFromQuery(
            DataQueryResponse response,
            AnalysisScope scope,
            String language,
            SalesFollowUpFocus focus
    ) {
        List<Map<String, Object>> items = response.items();
        int totalTasks = aggregateValue(response, "totalTaskCount");
        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "从 queryTasks 接口加载任务数据",
                "Load task records from queryTasks API",
                "共 " + totalTasks + " 条记录",
                totalTasks + " records total"
        ));

        long openTaskCount = items.stream().filter(item -> !"Completed".equalsIgnoreCase(stringValue(item, "status"))).count();
        long overdueCount = items.stream().filter(item -> "Overdue".equalsIgnoreCase(stringValue(item, "status"))).count();

        traceSteps.add(new CalcStep(
                "过滤未完成任务（Status != 'Completed'）",
                "Filter open tasks (Status != 'Completed')",
                "共 " + openTaskCount + " 条未完成，其中 " + overdueCount + " 条已逾期",
                openTaskCount + " open tasks, " + overdueCount + " overdue"
        ));

        if (focus == SalesFollowUpFocus.HIGH_ACTIVITY) {
            return analyzeSalesFollowUpActivityFromQuery(
                    items,
                    totalTasks,
                    scope,
                    language,
                    traceSteps,
                    openTaskCount,
                    overdueCount
            );
        }

        List<TaskBacklogMetric> backlogMetrics = items.stream()
                .collect(Collectors.groupingBy(item -> stringValue(item, "dealerName")))
                .entrySet().stream()
                .map(entry -> {
                    List<Map<String, Object>> dealerTasks = entry.getValue();
                    long backlog = dealerTasks.stream()
                            .filter(item -> !"Completed".equalsIgnoreCase(stringValue(item, "status")))
                            .count();
                    return new TaskBacklogMetric(entry.getKey(), dealerTasks.size(), backlog, percentage((int) backlog, dealerTasks.size()));
                })
                .sorted(Comparator.comparingDouble(TaskBacklogMetric::backlogRate).reversed())
                .toList();

        TaskBacklogMetric highestBacklog = backlogMetrics.getFirst();

        traceSteps.add(new CalcStep(
                "按 dealerName 分组统计积压率",
                "Group by dealerName, compute backlog rate",
                backlogMetrics.size() + " 个门店，积压率 = 未完成数 / 任务总数",
                backlogMetrics.size() + " dealers, backlog rate = open tasks / total tasks"
        ));

        List<TaskBacklogMetric> top3 = backlogMetrics.stream().limit(3).toList();
        StringBuilder zhTop = new StringBuilder();
        StringBuilder enTop = new StringBuilder();
        for (int i = 0; i < top3.size(); i++) {
            TaskBacklogMetric m = top3.get(i);
            if (i > 0) { zhTop.append("；"); enTop.append("; "); }
            zhTop.append(m.dealerName()).append("：积压率 ").append(formatPercent(m.backlogRate()))
                    .append("（").append(m.backlogCount()).append("/").append(m.totalCount()).append("）");
            enTop.append(m.dealerName()).append(": backlog rate ").append(formatPercent(m.backlogRate()))
                    .append(" (").append(m.backlogCount()).append("/").append(m.totalCount()).append(")");
        }

        traceSteps.add(new CalcStep(
                "排序找出积压最高门店",
                "Sort to find highest-backlog dealers",
                "Top 3：" + zhTop,
                "Top 3: " + enTop
        ));

        DataQualityContext quality = classifyCountQuality(
                "sales follow-up",
                "Task backlog",
                totalTasks,
                backlogMetrics.size(),
                3,
                (int) openTaskCount,
                totalTasks,
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- **%s** 跟进积压最严重，未完成任务占比 **%s**，为最高风险门店\n"
                    + "- 当前 %d 条任务中 %d 条未完成，其中有 **%d 条已逾期**，过程管理急需加强\n"
                    + "- 积压率 Top 3 门店合计占未完成任务总量的绝大部分，适合集中清理",
                    highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()),
                    totalTasks,
                    openTaskCount,
                    overdueCount
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total tasks", String.valueOf(totalTasks)});
            dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
            dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
            dataRows.add(new String[]{"Highest backlog dealer", "%s (%d / %d)".formatted(highestBacklog.dealerName(), highestBacklog.backlogCount(), highestBacklog.totalCount())});
            dataRows.add(new String[]{"Overdue rate", formatPercent(percentage((int) overdueCount, totalTasks))});

            List<TaskBacklogMetric> top5 = backlogMetrics.stream().limit(3).toList();
            String mermaid = buildMermaidXyChart(
                    "%s 跟进积压 Top 5".formatted(scope.summary(language)),
                    "门店", "未完成率 (%)",
                    ChartEntityType.DEALER,
                    top5.stream().map(TaskBacklogMetric::dealerName).toList(),
                    top5.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                    null
            );
            double maxRate = top5.stream().mapToDouble(TaskBacklogMetric::backlogRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.DEALER,
                    top5.stream().map(TaskBacklogMetric::dealerName).toList(),
                    top5.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s 积压率 %s，共 %d 条未完成，高积压通常伴随商机推进节奏放缓".formatted(highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()), highestBacklog.backlogCount()),
                    "逾期任务 %d 条，集中出现在积压率高的门店，说明部分销售团队的执行纪律待提升".formatted(overdueCount)
            );

            List<String> recommendations = List.of(
                    "优先清理 %s 的待办与逾期任务，确保高意向商机跟进不掉队".formatted(highestBacklog.dealerName()),
                    "建立每日任务完成率看板，对积压率 >50%% 的门店启动专项督导"
            );

            List<String> followUps = List.of(
                    "这个范围内逾期任务主要集中在哪些门店？",
                    "未完成任务较多的门店目标达成率怎么样？"
            );

            return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("销售跟进分析", backlogMetrics.size(), "逾期率",
                            formatPercent(percentage((int) overdueCount, totalTasks)), "—",
                            null, null,
                            highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate())),
                    mermaid, fallback, attributions, recommendations, followUps),
                    quality, traceSteps);
        }
        String conclusion = String.format(
                "- **%s** has the heaviest follow-up backlog at **%s** — the highest-risk dealer.\n"
                + "- %d tasks open out of %d total, with **%d overdue** — execution management needs immediate attention.\n"
                + "- The top 3 backlog-heavy dealers account for a disproportionate share of open tasks.",
                highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()),
                openTaskCount,
                totalTasks, overdueCount
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total tasks", String.valueOf(totalTasks)});
        dataRows.add(new String[]{"Open tasks", String.valueOf(openTaskCount)});
        dataRows.add(new String[]{"Overdue tasks", String.valueOf(overdueCount)});
        dataRows.add(new String[]{"Highest backlog dealer", "%s (%d / %d)".formatted(highestBacklog.dealerName(), highestBacklog.backlogCount(), highestBacklog.totalCount())});
        dataRows.add(new String[]{"Overdue rate", formatPercent(percentage((int) overdueCount, totalTasks))});

        List<TaskBacklogMetric> top5En = backlogMetrics.stream().limit(3).toList();
        String mermaid = buildMermaidXyChart(
                "Follow-up Backlog — Top 5 %s".formatted(scope.summary(language)),
                "Dealer", "Open Rate (%)",
                ChartEntityType.DEALER,
                top5En.stream().map(TaskBacklogMetric::dealerName).toList(),
                top5En.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                null
        );
        double maxRateEn = top5En.stream().mapToDouble(TaskBacklogMetric::backlogRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.DEALER,
                top5En.stream().map(TaskBacklogMetric::dealerName).toList(),
                top5En.stream().map(m -> m.backlogRate()).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s has a backlog rate of %s (%d open), which usually slows opportunity progression.".formatted(highestBacklog.dealerName(), formatPercent(highestBacklog.backlogRate()), highestBacklog.backlogCount()),
                "%d overdue tasks are concentrated in high-backlog dealers, indicating execution discipline gaps.".formatted(overdueCount)
        );

        List<String> recommendations = List.of(
                "Clear pending and overdue tasks for %s first — protect high-intent opportunity follow-ups.".formatted(highestBacklog.dealerName()),
                "Set up a daily completion-rate dashboard and initiate focused supervision for dealers with >50%% backlog."
        );

        List<String> followUps = List.of(
                "Which dealers account for most of the overdue tasks in this scope?",
                "How does the target achievement of backlog-heavy dealers compare with others?"
        );

        return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                quality, traceSteps);
    }

    private ScenarioResult analyzeSalesFollowUpActivityFromQuery(
            List<Map<String, Object>> items,
            int totalTasks,
            AnalysisScope scope,
            String language,
            List<CalcStep> traceSteps,
            long openTaskCount,
            long overdueCount
    ) {
        long completedTaskCount = items.stream()
                .filter(item -> "Completed".equalsIgnoreCase(stringValue(item, "status")))
                .count();
        List<TaskActivityMetric> activityMetrics = items.stream()
                .collect(Collectors.groupingBy(item -> stringValue(item, "dealerName")))
                .entrySet().stream()
                .map(entry -> {
                    List<Map<String, Object>> dealerTasks = entry.getValue();
                    long completed = dealerTasks.stream()
                            .filter(item -> "Completed".equalsIgnoreCase(stringValue(item, "status")))
                            .count();
                    return new TaskActivityMetric(entry.getKey(), dealerTasks.size(), completed,
                            percentage((int) completed, dealerTasks.size()));
                })
                .sorted(Comparator.comparingDouble(TaskActivityMetric::completionRate).reversed()
                        .thenComparing(Comparator.comparingLong(TaskActivityMetric::completedCount).reversed())
                        .thenComparing(Comparator.comparingInt(TaskActivityMetric::totalCount).reversed())
                        .thenComparing(TaskActivityMetric::dealerName))
                .toList();

        return buildSalesFollowUpActivityResult(
                scope,
                language,
                traceSteps,
                totalTasks,
                openTaskCount,
                overdueCount,
                completedTaskCount,
                activityMetrics,
                null, null, null
        );
    }

    private ScenarioResult analyzeCampaignPerformanceFromQuery(DataQueryResponse response, AnalysisScope scope, String language) {
        return analyzeCampaignPerformanceFromQuery(response, scope, language, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeCampaignPerformanceFromQuery(DataQueryResponse response, AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        List<Map<String, Object>> items = response.items();
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Campaign 数据集加载市场活动数据" : "Load campaign records from dataset",
                isZh(language) ? "共 " + response.count() + " 条记录" : response.count() + " records total",
                Map.of("source_type", "dataset", "table", "Campaign", "recordCount", response.count())));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + items.size() + " 条记录" : items.size() + " matching records",
                filterAuditMeta(scope, language, response.count(), items.size())));
        List<CampaignMetric> allMetrics = items.stream()
                .map(item -> new CampaignMetric(
                        stringValue(item, "campaignId"),
                        stringValue(item, "dealerName"),
                        stringValue(item, "campaignType"),
                        intValue(item, "actualOpportunityCount"),
                        intValue(item, "totalNewCustomerTarget"),
                        percentage(intValue(item, "actualOpportunityCount"), intValue(item, "totalNewCustomerTarget"))
                ))
                .toList();

        int campaignCount = aggregateValue(response, "campaignCount");
        List<CampaignMetric> validMetrics = allMetrics.stream()
                .filter(metric -> metric.totalNewCustomerTarget() > 0)
                .toList();
        int primaryNumerator = validMetrics.stream().mapToInt(CampaignMetric::actualOpportunityCount).sum();
        int primaryDenominator = validMetrics.stream().mapToInt(CampaignMetric::totalNewCustomerTarget).sum();
        DataQualityContext quality = classifyRateQuality(
                "campaign performance",
                "Campaign attainment",
                campaignCount,
                validMetrics.size(),
                2,
                primaryNumerator,
                primaryDenominator,
                allMetrics.size() - validMetrics.size(),
                validMetrics.size() < 2 ? 0.0 : validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(0.0)
                        - validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).min().orElse(0.0),
                true
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, new ArrayList<>());
        }

        List<CampaignMetric> metrics = validMetrics.stream()
                .sorted(Comparator.comparingDouble(CampaignMetric::attainmentRate).reversed())
                .toList();

        CampaignMetric best = metrics.getFirst();
        double averageAttainment = metrics.stream().mapToDouble(CampaignMetric::attainmentRate).average().orElse(0.0);
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按活动类型分组计算达成率" : "Group by campaign type, compute attainment rate",
                isZh(language)
                        ? validMetrics.size() + " 个活动，达成率 = actualOpportunityCount / totalNewCustomerTarget"
                        : validMetrics.size() + " campaigns, attainmentRate = actualOpportunityCount / totalNewCustomerTarget",
                auditMap(
                        "formula", "attainmentRate = actualOpportunityCount / totalNewCustomerTarget",
                        "inputRecordCount", items.size(),
                        "campaignCount", validMetrics.size(),
                        "totalActualOpportunityCount", primaryNumerator,
                        "totalNewCustomerTarget", primaryDenominator,
                        "excludedZeroTargetCount", allMetrics.size() - validMetrics.size(),
                        "sampleRows", campaignMetricRows(validMetrics, 5)
                )));

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- 当前活动效果最好的是 **%s** 的 **%s**，达成率 **%s**，远超平均线\n"
                    + "- 整体平均达成率 **%s**，%d 场活动中有 %d 场低于平均线，活动质量分化明显\n"
                    + "- 最佳活动打法值得复盘和横向复制，优先推广到相近城市或同车型门店",
                    best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate()),
                    formatPercent(averageAttainment),
                    metrics.size(),
                    metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count()
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Campaign count", String.valueOf(campaignCount)});
            dataRows.add(new String[]{"Best campaign", "%s (%d / %d)".formatted(best.campaignId(), best.actualOpportunityCount(), best.totalNewCustomerTarget())});
            dataRows.add(new String[]{"Average attainment", formatPercent(averageAttainment)});
            dataRows.add(new String[]{"Campaigns below average", String.valueOf(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())});

            List<CampaignMetric> top5 = metrics.stream().limit(3).toList();
            String mermaid = buildMermaidXyChart(
                    "%s 活动达成率 Top 5".formatted(scope.summary(language)),
                    "活动", "达成率 (%)",
                    ChartEntityType.CAMPAIGN,
                    top5.stream().map(CampaignMetric::campaignId).toList(),
                    top5.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                    averageAttainment
            );
            double maxRate = top5.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.CAMPAIGN,
                    top5.stream().map(CampaignMetric::campaignId).toList(),
                    top5.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s 的 %s 达成率高达 %s，推测其线索邀约、现场转化流程和资源配置更优".formatted(best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate())),
                    "低于平均线的 %d 场活动主要集中在执行力度弱或线索获取能力不足的门店".formatted(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())
            );

            List<String> recommendations = List.of(
                    "复用 %s 的活动打法，优先复制到相近城市或同车型门店，预计可将整体平均达成率拉升 10 个百分点".formatted(best.dealerName()),
                    "对比低于平均线的活动，找出线索获取和现场转化的主要短板并逐项改进"
            );

            List<String> followUps = List.of(
                    "哪些活动达成率低于平均水平？",
                    "表现最好的活动主要带来了哪些车型的商机？"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    "campaign performance", "Campaign attainment", new ArrayList<>());
        }
        String conclusion = String.format(
                "- **%s**'s **%s** leads with **%s** attainment, far above the average.\n"
                + "- Overall average is **%s**; %d out of %d campaigns fall below it — quality is uneven.\n"
                + "- The winning playbook is worth replicating across similar cities and product lines.",
                best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate()),
                formatPercent(averageAttainment),
                metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count(),
                metrics.size()
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Campaign count", String.valueOf(campaignCount)});
        dataRows.add(new String[]{"Best campaign", "%s (%d / %d)".formatted(best.campaignId(), best.actualOpportunityCount(), best.totalNewCustomerTarget())});
        dataRows.add(new String[]{"Average attainment", formatPercent(averageAttainment)});
        dataRows.add(new String[]{"Campaigns below average", String.valueOf(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())});

        List<CampaignMetric> top5En = metrics.stream().limit(3).toList();
        String mermaid = buildMermaidXyChart(
                "Campaign Attainment — Top 5 %s".formatted(scope.summary(language)),
                "Campaign", "Attainment (%)",
                ChartEntityType.CAMPAIGN,
                top5En.stream().map(CampaignMetric::campaignId).toList(),
                top5En.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                averageAttainment
        );
        double maxRateEn = top5En.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.CAMPAIGN,
                top5En.stream().map(CampaignMetric::campaignId).toList(),
                top5En.stream().map(CampaignMetric::attainmentRate).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s's %s achieved %s attainment — likely benefiting from stronger invitation and on-site conversion.".formatted(best.dealerName(), best.campaignType(), formatPercent(best.attainmentRate())),
                "The %d below-average campaigns are concentrated in dealers with weaker execution or lead acquisition.".formatted(metrics.stream().filter(m -> m.attainmentRate() < averageAttainment).count())
        );

        List<String> recommendations = List.of(
                "Replicate %s's campaign playbook to similar cities or product lines — targeting a 10 pp average uplift.".formatted(best.dealerName()),
                "Review the below-average campaigns to identify specific gaps in lead acquisition and on-site conversion."
        );

        List<String> followUps = List.of(
                "Which campaigns are currently below the average attainment rate?",
                "Which product models benefited most from the best-performing campaign?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "campaign performance", "Campaign attainment", new ArrayList<>());
    }

    private ScenarioResult analyzeLeadSourceFromQuery(DataQueryResponse response, AnalysisScope scope, String language) {
        return analyzeLeadSourceFromQuery(response, scope, language, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeLeadSourceFromQuery(DataQueryResponse response, AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        List<Map<String, Object>> items = response.items();
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Lead 数据集加载线索数据" : "Load lead records from dataset",
                isZh(language) ? "共 " + response.count() + " 条记录" : response.count() + " records total",
                Map.of("source_type", "dataset", "table", "Lead", "recordCount", response.count())));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + items.size() + " 条记录" : items.size() + " matching records",
                filterAuditMeta(scope, language, response.count(), items.size())));
        List<LeadSourceMetric> sourceMetrics = items.stream()
                .collect(Collectors.groupingBy(item -> stringValue(item, "leadSource")))
                .entrySet().stream()
                .map(entry -> {
                    List<Map<String, Object>> leads = entry.getValue();
                    long convertedCount = leads.stream().filter(item -> booleanValue(item, "isConverted")).count();
                    return new LeadSourceMetric(entry.getKey(), leads.size(), convertedCount,
                            percentage((int) convertedCount, leads.size()));
                })
                .sorted(Comparator.comparingInt(LeadSourceMetric::leadCount).reversed())
                .toList();

        int totalLeads = aggregateValue(response, "totalCount");
        int convertedTotal = (int) sourceMetrics.stream().mapToLong(LeadSourceMetric::convertedCount).sum();
        DataQualityContext quality = classifyCountQuality(
                "lead source analysis",
                "Lead conversion",
                totalLeads,
                sourceMetrics.size(),
                3,
                convertedTotal,
                totalLeads,
                true
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, new ArrayList<>());
        }

        LeadSourceMetric topVolume = sourceMetrics.getFirst();
        LeadSourceMetric topConversion = sourceMetrics.stream()
                .max(Comparator.comparingDouble(LeadSourceMetric::conversionRate))
                .orElse(topVolume);
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按线索来源分组统计转化率" : "Group by lead source, compute conversion rate",
                isZh(language)
                        ? sourceMetrics.size() + " 个来源, " + items.size() + " 条线索，转化率 = convertedCount / leadCount"
                        : sourceMetrics.size() + " sources, " + items.size() + " leads, conversionRate = convertedCount / leadCount",
                auditMap(
                        "formula", "conversionRate = convertedCount / leadCount",
                        "sourceCount", sourceMetrics.size(),
                        "leadCount", items.size(),
                        "convertedCount", convertedTotal,
                        "sampleRows", leadSourceMetricRows(sourceMetrics, 5)
                )));

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- **%s** 是当前线索量最大的来源（%d 条，占 %s），但转化率仅 **%s**\n"
                    + "- **%s** 转化率最高（**%s**），虽然线索量仅 %d 条，但质量远超其他来源\n"
                    + "- 量大但转化低 vs 量小但转化高，需要在投放策略上平衡规模和效率",
                    topVolume.source(), topVolume.leadCount(),
                    formatPercent(percentage(topVolume.leadCount(), totalLeads)),
                    formatPercent(topVolume.conversionRate()),
                    topConversion.source(), formatPercent(topConversion.conversionRate()),
                    topConversion.leadCount()
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Total leads", String.valueOf(totalLeads)});
            dataRows.add(new String[]{"Highest-volume source", "%s (%d leads, conversion %s)".formatted(topVolume.source(), topVolume.leadCount(), formatPercent(topVolume.conversionRate()))});
            dataRows.add(new String[]{"Best conversion source", "%s (conversion rate %s, %d leads)".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()), topConversion.leadCount())});
            dataRows.add(new String[]{"Unique sources", String.valueOf(sourceMetrics.size())});

            Map<String, Double> pieData = new LinkedHashMap<>();
            for (LeadSourceMetric metric : sourceMetrics) {
                pieData.put(metric.source(), (double) metric.leadCount());
            }
            String mermaid = buildMermaidPie(
                    "%s 线索来源分布".formatted(scope.summary(language)),
                    pieData
            );
            double maxVal = sourceMetrics.stream().mapToDouble(LeadSourceMetric::leadCount).max().orElse(1);
            String fallback = buildFallbackBars(
                    sourceMetrics.stream().map(LeadSourceMetric::source).toList(),
                    sourceMetrics.stream().map(m -> (double) m.leadCount()).collect(Collectors.toList()),
                    maxVal
            );

            List<String> attributions = List.of(
                    "%s 线索量最大但转化率仅 %s，量大不一定代表质优，需要同时看线索规模和转化效率".formatted(topVolume.source(), formatPercent(topVolume.conversionRate())),
                    "%s 转化率最高达 %s，其线索跟进的流程和话术值得提炼为可复制的打法".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))
            );

            List<String> recommendations = List.of(
                    "继续放大 %s 的获客规模，同时建立转化率周度监控，确保量价齐升".formatted(topVolume.source()),
                    "优先复盘 %s 的转化路径，提炼成可复制的线索跟进 SOP 推广到其他来源".formatted(topConversion.source())
            );

            List<String> followUps = List.of(
                    "网站来源线索在当前范围内的转化表现如何？",
                    "哪些门店最依赖单一线索来源？"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    "lead source analysis", "Lead conversion", new ArrayList<>());
        }
        String conclusion = String.format(
                "- **%s** delivers the most leads (%d, %s of total), but converts at only **%s**.\n"
                + "- **%s** has the best conversion rate at **%s** — only %d leads, but far higher quality.\n"
                + "- The volume-vs-quality tradeoff calls for a balanced investment strategy.",
                topVolume.source(), topVolume.leadCount(),
                formatPercent(percentage(topVolume.leadCount(), totalLeads)),
                formatPercent(topVolume.conversionRate()),
                topConversion.source(), formatPercent(topConversion.conversionRate()),
                topConversion.leadCount()
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Total leads", String.valueOf(totalLeads)});
        dataRows.add(new String[]{"Highest-volume source", "%s (%d leads, conversion %s)".formatted(topVolume.source(), topVolume.leadCount(), formatPercent(topVolume.conversionRate()))});
        dataRows.add(new String[]{"Best conversion source", "%s (conversion rate %s, %d leads)".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()), topConversion.leadCount())});
        dataRows.add(new String[]{"Unique sources", String.valueOf(sourceMetrics.size())});

        Map<String, Double> pieData = new LinkedHashMap<>();
        for (LeadSourceMetric metric : sourceMetrics) {
            pieData.put(metric.source(), (double) metric.leadCount());
        }
        String mermaid = buildMermaidPie(
                "Lead Source Distribution — %s".formatted(scope.summary(language)),
                pieData
        );
        double maxVal = sourceMetrics.stream().mapToDouble(LeadSourceMetric::leadCount).max().orElse(1);
        String fallback = buildFallbackBars(
                sourceMetrics.stream().map(LeadSourceMetric::source).toList(),
                sourceMetrics.stream().map(m -> (double) m.leadCount()).collect(Collectors.toList()),
                maxVal
        );

        List<String> attributions = List.of(
                "%s has the highest volume but only %s conversion — scale does not guarantee quality.".formatted(topVolume.source(), formatPercent(topVolume.conversionRate())),
                "%s converts at %s — its follow-up process and scripts can be codified into a repeatable playbook.".formatted(topConversion.source(), formatPercent(topConversion.conversionRate()))
        );

        List<String> recommendations = List.of(
                "Keep scaling %s while instituting weekly conversion monitoring to ensure quality keeps pace.".formatted(topVolume.source()),
                "Codify %s's conversion path into a repeatable SOP and roll it out to other sources.".formatted(topConversion.source())
        );

        List<String> followUps = List.of(
                "How does website-sourced lead conversion perform in this scope?",
                "Which dealers rely too heavily on a single lead source?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "lead source analysis", "Lead conversion", new ArrayList<>());
    }

    private ScenarioResult tryAnswerTargetQuestion(
            List<Target> filtered,
            AnalysisScope scope,
            String language,
            String message,
            List<CalcStep> traceSteps
    ) {
        String normalized = normalize(message);
        if (normalized == null) {
            return null;
        }
        if (!"zh".equals(language) || !isDirectTargetQuestion(normalized)) {
            return null;
        }

        boolean asksModel = mentionsProductDimension(normalized);
        boolean asksMostWon = asksTopSalesVolume(normalized);
        boolean asksHighestRate = asksHighestRate(normalized);
        boolean asksLowestRate = asksLowestRate(normalized);
        boolean asksDealer = mentionsDealerDimension(normalized)
                || (!asksModel && (asksMostWon || asksHighestRate || asksLowestRate));

        if (asksModel && asksMostWon) {
            List<TargetAggregateMetric> metrics = aggregateTargets(filtered, Target::getProductModel);
            TargetAggregateMetric top = metrics.stream()
                    .max(Comparator.comparingInt(TargetAggregateMetric::wonCount)
                            .thenComparing(TargetAggregateMetric::label))
                    .orElse(null);
            return top == null ? null : targetRankingAnswer(language, scope, "车型赢单最多", top,
                    metrics.stream().sorted(targetWonDescending()).limit(5).toList(), traceSteps);
        }

        if (asksModel && asksHighestRate) {
            List<TargetAggregateMetric> metrics = aggregateTargets(filtered, Target::getProductModel).stream()
                    .filter(metric -> metric.targetValue() > 0)
                    .toList();
            TargetAggregateMetric top = metrics.stream()
                    .max(Comparator.comparingDouble(TargetAggregateMetric::achievementRate)
                            .thenComparingInt(TargetAggregateMetric::wonCount))
                    .orElse(null);
            return top == null ? null : targetRankingAnswer(language, scope, "车型目标达成率最高", top,
                    metrics.stream().sorted(targetRateDescending()).limit(5).toList(), traceSteps);
        }

        if (asksDealer && asksMostWon) {
            List<TargetAggregateMetric> metrics = aggregateTargets(filtered, Target::getDealerName);
            TargetAggregateMetric top = metrics.stream()
                    .max(Comparator.comparingInt(TargetAggregateMetric::wonCount)
                            .thenComparing(TargetAggregateMetric::label))
                    .orElse(null);
            return top == null ? null : targetRankingAnswer(language, scope, "经销商赢单数最多", top,
                    metrics.stream().sorted(targetWonDescending()).limit(5).toList(), traceSteps);
        }

        if (asksDealer && (asksHighestRate || asksLowestRate)) {
            List<TargetAggregateMetric> metrics = aggregateTargets(filtered, Target::getDealerName).stream()
                    .filter(metric -> metric.targetValue() > 0)
                    .toList();
            List<TargetAggregateMetric> sorted = metrics.stream()
                    .sorted(asksLowestRate ? targetRateAscending() : targetRateDescending())
                    .limit(containsAny(normalized, "哪些", "较低") ? 5 : 3)
                    .toList();
            TargetAggregateMetric focus = sorted.isEmpty() ? null : sorted.getFirst();
            String title = asksLowestRate ? "经销商目标达成率最低" : "经销商目标达成率最高";
            return focus == null ? null : targetRankingAnswer(language, scope, title, focus, sorted, traceSteps);
        }

        if (containsAny(normalized, "目标达成", "完成情况", "整体", "全量") || scope.dealerCode() != null) {
            TargetAggregateMetric overall = aggregateTarget(filtered, scope.dealerName() != null ? scope.dealerName() : scope.summary(language));
            String conclusion = "zh".equals(language)
                    ? "%s目标 %d，商机创建 %d，赢单 %d，目标达成率 %s。".formatted(
                            scope.timeRange().hasValue() ? scope.timeRange().label(language) + " " : "",
                            overall.targetValue(), overall.createCount(), overall.wonCount(), formatPercent(overall.achievementRate()))
                    : "%s target %d, opportunity creates %d, won %d, achievement rate %s.".formatted(
                            scope.summary(language), overall.targetValue(), overall.createCount(), overall.wonCount(),
                            formatPercent(overall.achievementRate()));
            return directAnswer(language, conclusion, targetRows(List.of(overall)),
                    "target achievement", "Achievement rate", traceSteps);
        }

        return null;
    }

    private ScenarioResult targetRankingAnswer(
            String language,
            AnalysisScope scope,
            String title,
            TargetAggregateMetric focus,
            List<TargetAggregateMetric> rows,
            List<CalcStep> traceSteps
    ) {
        String conclusion;
        if ("zh".equals(language)) {
            conclusion = "%s：**%s**，目标 %d，赢单 %d，商机创建 %d，达成率 %s。".formatted(
                    title, focus.label(), focus.targetValue(), focus.wonCount(), focus.createCount(),
                    formatPercent(focus.achievementRate()));
        } else {
            conclusion = "%s: **%s**, target %d, won %d, created %d, achievement %s.".formatted(
                    title, focus.label(), focus.targetValue(), focus.wonCount(), focus.createCount(),
                    formatPercent(focus.achievementRate()));
        }
        return directAnswer(language, conclusion, targetRows(rows),
                "target achievement", "Achievement rate", traceSteps);
    }

    private ScenarioResult tryAnswerOpportunityQuestion(AnalysisScope scope, String language, String message) {
        String normalized = normalize(message);
        if (normalized == null) {
            return null;
        }
        if (!"zh".equals(language) || !isDirectOpportunityQuestion(normalized)) {
            return null;
        }
        List<Opportunity> filtered = cachedOpportunities().stream()
                .filter(opportunity -> matchesScope(opportunity.getDealerCode(), opportunity.getDealerName(), opportunity.getCity(),
                        opportunity.getDealerGroupName(), opportunity.getProductModel(), scope))
                .filter(opportunity -> matchesField(opportunity.getLeadSource(), scope.leadSource()))
                .filter(opportunity -> scope.timeRange().matchesDate(opportunity.getCreatedDate()))
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }

        if (containsAny(normalized, "购买周期", "购车周期")) {
            List<CountMetric> metrics = countBy(filtered, Opportunity::getPurchaseHorizon).stream()
                    .sorted(countDescending())
                    .toList();
            CountMetric top = metrics.getFirst();
            String conclusion = "zh".equals(language)
                    ? "购买周期最多集中在 **%s**，共 %d 条；其次是 %s，共 %d 条。".formatted(
                            top.label(), top.count(), secondLabel(metrics), secondCount(metrics))
                    : "Purchase horizon concentrates most in **%s** with %d opportunities; next is %s with %d.".formatted(
                            top.label(), top.count(), secondLabel(metrics), secondCount(metrics));
            return directAnswer(language, conclusion, countRows("Purchase horizon", metrics, 5),
                    "opportunity funnel", "Opportunity count", List.of());
        }

        if (asksOpportunityStageBreakdown(normalized) || containsAny(normalized, "各阶段", "阶段分别", "商机漏斗")) {
            List<CountMetric> metrics = countBy(filtered, Opportunity::getStageName).stream()
                    .sorted(countDescending())
                    .toList();
            String conclusion = "zh".equals(language)
                    ? "商机共 %d 条。阶段分布：%s。".formatted(filtered.size(), joinCountMetrics(metrics, 6))
                    : "%d opportunities. Stage distribution: %s.".formatted(filtered.size(), joinCountMetrics(metrics, 6));
            return directAnswer(language, conclusion, countRows("Stage", metrics, 6),
                    "opportunity funnel", "Opportunity count", List.of());
        }

        if (containsAny(normalized, "高概率")) {
            List<Opportunity> highProbability = filtered.stream()
                    .filter(opportunity -> opportunity.getProbability() != null && opportunity.getProbability() >= 70)
                    .toList();
            List<CountMetric> metrics = countBy(highProbability, Opportunity::getDealerName).stream()
                    .sorted(countDescending())
                    .toList();
            if (metrics.isEmpty()) {
                return null;
            }
            String conclusion = "zh".equals(language)
                    ? "按 Probability>=70 统计，高概率商机最多的是 %s。".formatted(joinCountMetrics(metrics, 3))
                    : "Using Probability>=70, high-probability opportunities concentrate in %s.".formatted(joinCountMetrics(metrics, 3));
            return directAnswer(language, conclusion, countRows("Dealer", metrics, 5),
                    "opportunity funnel", "Opportunity count", List.of());
        }

        if (containsAny(normalized, "线索来源", "来源", "渠道", "source")) {
            if (asksWinRate(normalized)) {
                List<RateMetric> metrics = rateBy(filtered, Opportunity::getLeadSource, this::isWonOpportunity).stream()
                        .filter(metric -> metric.total() >= 30 && isKnown(metric.label()))
                        .sorted(rateDescending())
                        .toList();
                if (metrics.isEmpty()) {
                    return null;
                }
                RateMetric top = metrics.getFirst();
                String conclusion = "zh".equals(language)
                        ? "在主要来源中，**%s** 商机赢单率最高：%d 条商机中 %d 条赢单，赢单率约 %s。".formatted(
                                top.label(), top.total(), top.positive(), formatPercent(top.rate()))
                        : "Among major sources, **%s** has the highest opportunity win rate: %d won out of %d, about %s.".formatted(
                                top.label(), top.positive(), top.total(), formatPercent(top.rate()));
                return directAnswer(language, conclusion, rateRows("Lead source", metrics, 5),
                        "opportunity funnel", "Opportunity count", List.of());
            }
            List<CountMetric> metrics = countBy(filtered, Opportunity::getLeadSource).stream()
                    .filter(metric -> isKnown(metric.label()))
                    .sorted(countDescending())
                    .toList();
            String conclusion = "zh".equals(language)
                    ? "商机来源最多的是 %s。".formatted(joinCountMetrics(metrics, 5))
                    : "Top opportunity sources are %s.".formatted(joinCountMetrics(metrics, 5));
            return directAnswer(language, conclusion, countRows("Lead source", metrics, 5),
                    "opportunity funnel", "Opportunity count", List.of());
        }

        if (mentionsProductDimension(normalized)) {
            if (asksWinRate(normalized)) {
                List<RateMetric> metrics = rateBy(filtered, Opportunity::getProductModel, this::isWonOpportunity).stream()
                        .filter(metric -> metric.total() >= 30 && isKnown(metric.label()))
                        .sorted(rateDescending())
                        .toList();
                if (metrics.isEmpty()) {
                    return null;
                }
                RateMetric top = metrics.getFirst();
                String conclusion = "zh".equals(language)
                        ? "不考虑空车型时，**%s** 赢单率最高：%d 条商机中 %d 条赢单，赢单率约 %s。".formatted(
                                top.label(), top.total(), top.positive(), formatPercent(top.rate()))
                        : "Excluding blank models, **%s** has the highest win rate: %d won out of %d, about %s.".formatted(
                                top.label(), top.positive(), top.total(), formatPercent(top.rate()));
                return directAnswer(language, conclusion, rateRows("Model", metrics, 5),
                        "opportunity funnel", "Opportunity count", List.of());
            }
            List<CountMetric> metrics = countBy(filtered, Opportunity::getProductModel).stream()
                    .filter(metric -> isKnown(metric.label()))
                    .sorted(countDescending())
                    .toList();
            CountMetric top = metrics.isEmpty() ? null : metrics.getFirst();
            if (top == null) {
                return null;
            }
            String conclusion = "zh".equals(language)
                    ? "**%s** 商机数量最多，共 %d 条；其次 %s。".formatted(top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3))
                    : "**%s** has the most opportunities: %d; next %s.".formatted(top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3));
            return directAnswer(language, conclusion, countRows("Model", metrics, 5),
                    "opportunity funnel", "Opportunity count", List.of());
        }

        if (containsAny(normalized, "赢单商机最多", "成交商机最多")
                || (containsAny(normalized, "商机") && asksTopSalesVolume(normalized))) {
            List<CountMetric> metrics = countBy(filtered.stream().filter(this::isWonOpportunity).toList(), Opportunity::getDealerName).stream()
                    .sorted(countDescending())
                    .toList();
            return opportunityCountAnswer(language, "赢单商机最多的经销商", metrics);
        }
        if (containsAny(normalized, "战败商机最多", "丢单最多")) {
            List<CountMetric> metrics = countBy(filtered.stream().filter(this::isLostOpportunity).toList(), Opportunity::getDealerName).stream()
                    .sorted(countDescending())
                    .toList();
            return opportunityCountAnswer(language, "战败商机最多的经销商", metrics);
        }
        if (containsAny(normalized, "商机最多的经销商")) {
            List<CountMetric> metrics = countBy(filtered, Opportunity::getDealerName).stream()
                    .sorted(countDescending())
                    .toList();
            return opportunityCountAnswer(language, "商机最多的经销商", metrics);
        }

        return null;
    }

    private ScenarioResult opportunityCountAnswer(String language, String title, List<CountMetric> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }
        CountMetric top = metrics.getFirst();
        String conclusion = "zh".equals(language)
                ? "%s是 **%s**，共 %d 条；其次 %s。".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3))
                : "%s is **%s** with %d; next %s.".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3));
        return directAnswer(language, conclusion, countRows("Dealer", metrics, 5),
                "opportunity funnel", "Opportunity count", List.of());
    }

    private ScenarioResult tryAnswerLeadQuestion(AnalysisScope scope, String language, String message) {
        String normalized = normalize(message);
        if (normalized == null) {
            return null;
        }
        if (!"zh".equals(language) || !isDirectLeadQuestion(normalized)) {
            return null;
        }
        List<Lead> filtered = cachedLeads().stream()
                .filter(lead -> matchesScope(lead.getDealerCode(), lead.getDealerName(), lead.getCity(),
                        lead.getDealerGroupName(), lead.getProductModel(), scope))
                .filter(lead -> matchesField(lead.getLeadSource(), scope.leadSource()))
                .filter(lead -> scope.timeRange().matchesDate(lead.getCreatedDate()))
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }

        if (asksStatusBreakdown(normalized) || containsAny(normalized, "状态分布", "一共有多少")) {
            List<CountMetric> metrics = countBy(filtered, Lead::getStageName).stream().sorted(countDescending()).toList();
            String conclusion = "zh".equals(language)
                    ? "线索共 %d 条，其中 %s。".formatted(filtered.size(), joinCountMetrics(metrics, 5))
                    : "%d leads, with status distribution %s.".formatted(filtered.size(), joinCountMetrics(metrics, 5));
            return directAnswer(language, conclusion, countRows("Status", metrics, 5),
                    "lead source analysis", "Lead conversion", List.of());
        }

        if (containsAny(normalized, "意向车型")) {
            long unknownCount = filtered.stream().filter(lead -> !isKnown(lead.getProductModel())).count();
            List<CountMetric> metrics = countBy(filtered, Lead::getProductModel).stream()
                    .filter(metric -> isKnown(metric.label()))
                    .sorted(countDescending())
                    .toList();
            CountMetric top = metrics.isEmpty() ? null : metrics.getFirst();
            if (top == null) {
                return null;
            }
            String conclusion = "zh".equals(language)
                    ? "线索中有 %d 条未填写意向车型；已填写车型里 **%s** 最多，共 %d 条。".formatted(
                            unknownCount, top.label(), top.count())
                    : "%d leads have blank intended model; among filled models, **%s** leads with %d.".formatted(
                            unknownCount, top.label(), top.count());
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Blank model leads", String.valueOf(unknownCount)});
            rows.addAll(countRows("Model", metrics, 5));
            return directAnswer(language, conclusion, rows,
                    "lead source analysis", "Lead conversion", List.of());
        }

        if (containsAny(normalized, "分配最多")) {
            List<CountMetric> metrics = countBy(filtered.stream().filter(lead -> isKnown(lead.getDealerName())
                            && !"未分配".equals(lead.getDealerName())).toList(), Lead::getDealerName)
                    .stream().sorted(countDescending()).toList();
            return leadDealerAnswer(language, "线索分配最多的经销商", metrics);
        }

        if (containsAny(normalized, "转化线索最多")) {
            List<CountMetric> metrics = countBy(filtered.stream().filter(lead -> Boolean.TRUE.equals(lead.getConverted()))
                            .filter(lead -> isKnown(lead.getDealerName()) && !"未分配".equals(lead.getDealerName())).toList(), Lead::getDealerName)
                    .stream().sorted(countDescending()).toList();
            return leadDealerAnswer(language, "转化线索最多的经销商", metrics);
        }

        if (scope.leadSource() != null || containsAny(normalized, "转化情况", "转化怎么样")) {
            String source = scope.leadSource();
            if (source == null) {
                source = detectLeadSourceFromMessage(normalized);
            }
            if (source != null) {
                String finalSource = source;
                List<Lead> sourceLeads = filtered.stream()
                        .filter(lead -> matchesField(lead.getLeadSource(), finalSource))
                        .toList();
                if (!sourceLeads.isEmpty()) {
                    long converted = sourceLeads.stream().filter(lead -> Boolean.TRUE.equals(lead.getConverted())).count();
                    List<CountMetric> statusCounts = countBy(sourceLeads, Lead::getStageName).stream().sorted(countDescending()).toList();
                    String conclusion = "zh".equals(language)
                            ? "%s 线索共 %d 条，已转化 %d 条，转化率约 %s；其中 %s。".formatted(
                                    finalSource, sourceLeads.size(), converted, formatPercent(percentage((int) converted, sourceLeads.size())),
                                    joinCountMetrics(statusCounts, 5))
                            : "%s has %d leads, %d converted, conversion rate about %s; status %s.".formatted(
                                    finalSource, sourceLeads.size(), converted, formatPercent(percentage((int) converted, sourceLeads.size())),
                                    joinCountMetrics(statusCounts, 5));
                    List<String[]> rows = new ArrayList<>();
                    rows.add(new String[]{"Lead source", finalSource});
                    rows.add(new String[]{"Total leads", String.valueOf(sourceLeads.size())});
                    rows.add(new String[]{"Converted leads", String.valueOf(converted)});
                    rows.add(new String[]{"Conversion rate", formatPercent(percentage((int) converted, sourceLeads.size()))});
                    rows.addAll(countRows("Status", statusCounts, 5));
                    return directAnswer(language, conclusion, rows,
                            "lead source analysis", "Lead conversion", List.of());
                }
            }
        }

        if (containsAny(normalized, "转化率最高")) {
            List<RateMetric> allMetrics = rateBy(filtered, Lead::getLeadSource, lead -> Boolean.TRUE.equals(lead.getConverted()))
                    .stream().sorted(rateDescending()).toList();
            List<RateMetric> knownMetrics = allMetrics.stream()
                    .filter(metric -> isKnown(metric.label()))
                    .sorted(rateDescending())
                    .toList();
            RateMetric blankTop = allMetrics.stream().filter(metric -> !isKnown(metric.label())).findFirst().orElse(null);
            RateMetric knownTop = knownMetrics.isEmpty() ? null : knownMetrics.getFirst();
            if (knownTop == null) {
                return null;
            }
            String blankPart = blankTop == null ? "" : "空来源 %d 条中 %d 条转化，转化率 %s；".formatted(
                    blankTop.total(), blankTop.positive(), formatPercent(blankTop.rate()));
            String conclusion = "zh".equals(language)
                    ? "%s在明确来源中，**%s** 转化率最高，%d 条中 %d 条转化，约 %s。".formatted(
                            blankPart, knownTop.label(), knownTop.total(), knownTop.positive(), formatPercent(knownTop.rate()))
                    : "%s Among explicit sources, **%s** converts best: %d of %d, about %s.".formatted(
                            blankPart, knownTop.label(), knownTop.positive(), knownTop.total(), formatPercent(knownTop.rate()));
            return directAnswer(language, conclusion, rateRows("Lead source", allMetrics, 6),
                    "lead source analysis", "Lead conversion", List.of());
        }

        if (containsAny(normalized, "来源最多", "哪个渠道")) {
            List<CountMetric> metrics = countBy(filtered, Lead::getLeadSource).stream()
                    .filter(metric -> isKnown(metric.label()))
                    .sorted(countDescending())
                    .toList();
            CountMetric top = metrics.isEmpty() ? null : metrics.getFirst();
            if (top == null) {
                return null;
            }
            String conclusion = "zh".equals(language)
                    ? "Lead 表中线索来源最多的是 **%s**，共 %d 条；其次 %s。".formatted(
                            top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3))
                    : "Top lead source is **%s** with %d; next %s.".formatted(
                            top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3));
            return directAnswer(language, conclusion, countRows("Lead source", metrics, 5),
                    "lead source analysis", "Lead conversion", List.of());
        }

        return null;
    }

    private ScenarioResult leadDealerAnswer(String language, String title, List<CountMetric> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }
        CountMetric top = metrics.getFirst();
        String conclusion = "zh".equals(language)
                ? "%s是 **%s**，共 %d 条；其次 %s。".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3))
                : "%s is **%s** with %d; next %s.".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3));
        return directAnswer(language, conclusion, countRows("Dealer", metrics, 5),
                "lead source analysis", "Lead conversion", List.of());
    }

    private ScenarioResult tryAnswerSalesFollowUpQuestion(AnalysisScope scope, String language, String message) {
        String normalized = normalize(message);
        if (normalized == null) {
            return null;
        }
        if (!"zh".equals(language) || !isDirectTaskQuestion(normalized)) {
            return null;
        }
        List<Task> filtered = cachedTasks().stream()
                .filter(task -> matchesScope(task.getDealerCode(), task.getDealerName(), task.getCity(),
                        task.getDealerGroupName(), null, scope))
                .filter(task -> scope.timeRange().matchesDate(task.getCreatedDate()))
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }

        if (asksTaskSubjectBreakdown(normalized)) {
            List<CountMetric> metrics = countBy(filtered, Task::getSubject).stream().sorted(countDescending()).toList();
            int limit = requestedTopLimit(normalized, 5);
            String conclusion = "zh".equals(language)
                    ? "任务 Subject 中最多的是 %s。".formatted(joinCountMetrics(metrics, limit))
                    : "Top task subjects are %s.".formatted(joinCountMetrics(metrics, limit));
            return directAnswer(language, conclusion, countRows("Task subject", metrics, limit),
                    "sales follow-up", "Task backlog", List.of());
        }

        if (containsAny(normalized, "计划中任务最多", "planned")) {
            List<CountMetric> metrics = countBy(filtered.stream()
                            .filter(task -> "Planned".equalsIgnoreCase(task.getStatus()))
                            .filter(task -> isKnown(task.getDealerName()) && !"未分配".equals(task.getDealerName()))
                            .toList(), Task::getDealerName)
                    .stream().sorted(countDescending()).toList();
            return taskDealerAnswer(language, "Planned 任务最多的经销商", metrics);
        }

        if (containsAny(normalized, "任务完成率最低")) {
            List<RateMetric> metrics = rateBy(filtered.stream()
                            .filter(task -> isKnown(task.getDealerName()) && !"未分配".equals(task.getDealerName()))
                            .toList(), Task::getDealerName, task -> "Completed".equalsIgnoreCase(task.getStatus()))
                    .stream()
                    .filter(metric -> metric.total() > 100)
                    .sorted(rateAscending())
                    .toList();
            if (metrics.isEmpty()) {
                return null;
            }
            RateMetric lowest = metrics.getFirst();
            String conclusion = "zh".equals(language)
                    ? "在任务数超过100的经销商中，**%s** 完成率最低：任务 %d 条，完成 %d 条，完成率约 %s。".formatted(
                            lowest.label(), lowest.total(), lowest.positive(), formatPercent(lowest.rate()))
                    : "Among dealers with more than 100 tasks, **%s** has the lowest completion rate: %d completed out of %d, about %s.".formatted(
                            lowest.label(), lowest.positive(), lowest.total(), formatPercent(lowest.rate()));
            return directAnswer(language, conclusion, rateRows("Dealer", metrics, 5),
                    "sales follow-up", "Task backlog", List.of());
        }

        if (scope.dealerCode() != null || containsAny(normalized, "任务完成情况")) {
            long completed = filtered.stream().filter(task -> "Completed".equalsIgnoreCase(task.getStatus())).count();
            List<CountMetric> statusCounts = countBy(filtered, Task::getStatus).stream().sorted(countDescending()).toList();
            String subject = scope.dealerName() != null ? scope.dealerName() : scope.summary(language);
            String conclusion = "zh".equals(language)
                    ? "%s关联任务 %d 条，完成 %d 条，%s，完成率约 %s。".formatted(
                            subject, filtered.size(), completed, joinCountMetrics(statusCounts, 5),
                            formatPercent(percentage((int) completed, filtered.size())))
                    : "%s has %d linked tasks, %d completed, %s, completion rate about %s.".formatted(
                            subject, filtered.size(), completed, joinCountMetrics(statusCounts, 5),
                            formatPercent(percentage((int) completed, filtered.size())));
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Total tasks", String.valueOf(filtered.size())});
            rows.add(new String[]{"Completed tasks", String.valueOf(completed)});
            rows.add(new String[]{"Completion rate", formatPercent(percentage((int) completed, filtered.size()))});
            rows.addAll(countRows("Status", statusCounts, 5));
            return directAnswer(language, conclusion, rows,
                    "sales follow-up", "Task backlog", List.of());
        }

        if (containsAny(normalized, "关联任务最多", "任务最多")) {
            List<RateMetric> metrics = rateBy(filtered.stream()
                            .filter(task -> isKnown(task.getDealerName()) && !"未分配".equals(task.getDealerName()))
                            .toList(), Task::getDealerName, task -> "Completed".equalsIgnoreCase(task.getStatus()))
                    .stream()
                    .sorted(Comparator.comparingInt(RateMetric::total).reversed())
                    .toList();
            if (metrics.isEmpty()) {
                return null;
            }
            RateMetric top = metrics.getFirst();
            String conclusion = "zh".equals(language)
                    ? "排除未匹配商机后，关联任务最多的是 **%s**，共 %d 条，完成 %d 条，完成率约 %s。".formatted(
                            top.label(), top.total(), top.positive(), formatPercent(top.rate()))
                    : "Excluding unmatched opportunities, **%s** has the most linked tasks: %d, completed %d, completion %s.".formatted(
                            top.label(), top.total(), top.positive(), formatPercent(top.rate()));
            return directAnswer(language, conclusion, rateRows("Dealer", metrics, 5),
                    "sales follow-up", "Task backlog", List.of());
        }

        if (containsAny(normalized, "总体完成情况", "任务总体")) {
            long completed = filtered.stream().filter(task -> "Completed".equalsIgnoreCase(task.getStatus())).count();
            List<CountMetric> statusCounts = countBy(filtered, Task::getStatus).stream().sorted(countDescending()).toList();
            String conclusion = "zh".equals(language)
                    ? "任务共 %d 条，其中 %s，整体完成率约 %s。".formatted(
                            filtered.size(), joinCountMetrics(statusCounts, 5),
                            formatPercent(percentage((int) completed, filtered.size())))
                    : "%d tasks total, %s, overall completion rate about %s.".formatted(
                            filtered.size(), joinCountMetrics(statusCounts, 5),
                            formatPercent(percentage((int) completed, filtered.size())));
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Total tasks", String.valueOf(filtered.size())});
            rows.add(new String[]{"Completion rate", formatPercent(percentage((int) completed, filtered.size()))});
            rows.addAll(countRows("Status", statusCounts, 5));
            return directAnswer(language, conclusion, rows,
                    "sales follow-up", "Task backlog", List.of());
        }

        return null;
    }

    private ScenarioResult taskDealerAnswer(String language, String title, List<CountMetric> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }
        CountMetric top = metrics.getFirst();
        String conclusion = "zh".equals(language)
                ? "%s是 **%s**，共 %d 条；其次 %s。".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3))
                : "%s is **%s** with %d; next %s.".formatted(title, top.label(), top.count(), joinCountMetrics(metrics.stream().skip(1).toList(), 3));
        return directAnswer(language, conclusion, countRows("Dealer", metrics, 5),
                "sales follow-up", "Task backlog", List.of());
    }

    private ScenarioResult tryAnswerCampaignQuestion(AnalysisScope scope, String language, String message) {
        String normalized = normalize(message);
        if (normalized == null) {
            return null;
        }
        if (!"zh".equals(language) || !isDirectCampaignQuestion(normalized)) {
            return null;
        }
        List<Campaign> filtered = cachedCampaigns().stream()
                .filter(campaign -> matchesScope(campaign.getDealerCode(), campaign.getDealerName(), campaign.getCity(),
                        campaign.getDealerGroupName(), campaign.getProductModel(), scope))
                .filter(campaign -> scope.timeRange().matchesDate(campaign.getCreatedDate()))
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }

        if (containsAny(normalized, "完成率为0", "为0")) {
            List<Campaign> zeroCampaigns = filtered.stream()
                    .filter(campaign -> campaign.getTargetOpportunityAmount() > 0 && campaign.getActualOpportunityCount() == 0)
                    .sorted(Comparator.comparing((Campaign campaign) -> dealerCodeSortKey(campaign.getDealerName()))
                            .thenComparing(Campaign::getDealerName)
                            .thenComparing(Comparator.comparingInt(Campaign::getTargetOpportunityAmount).reversed())
                            .thenComparing(Campaign::getCampaignName))
                    .toList();
            List<Campaign> sampleCampaigns = representativeCampaignsByDealer(zeroCampaigns, 30);
            String dealerSummary = zeroCampaigns.stream()
                    .map(Campaign::getDealerName)
                    .filter(this::isKnown)
                    .distinct()
                    .sorted(Comparator.comparing(this::dealerCodeSortKey))
                    .limit(30)
                    .collect(Collectors.joining("、"));
            String conclusion = "zh".equals(language)
                    ? "目标商机完成率为0的活动样例：%s。涉及经销商包括：%s。".formatted(sampleCampaigns.stream()
                            .limit(5)
                            .map(campaign -> "%s（目标%d、实际%d）".formatted(campaign.getCampaignName(),
                                    campaign.getTargetOpportunityAmount(), campaign.getActualOpportunityCount()))
                            .collect(Collectors.joining("；")), dealerSummary)
                    : "Campaigns with zero opportunity attainment include %s. Affected dealers include: %s.".formatted(sampleCampaigns.stream()
                            .limit(5)
                            .map(campaign -> "%s (target %d, actual %d)".formatted(campaign.getCampaignName(),
                                    campaign.getTargetOpportunityAmount(), campaign.getActualOpportunityCount()))
                            .collect(Collectors.joining("; ")), dealerSummary);
            return directAnswer(language, conclusion, campaignRows(sampleCampaigns),
                    "campaign performance", "Campaign attainment", List.of());
        }

        if (containsAny(normalized, "单个活动", "产生商机最多")) {
            Campaign top = filtered.stream()
                    .max(Comparator.comparingInt(Campaign::getActualOpportunityCount))
                    .orElse(null);
            if (top == null) {
                return null;
            }
            String conclusion = "zh".equals(language)
                    ? "单个活动中，**%s** 产生商机最多，NumberOfOpportunities 为 %d，NumberOfWonOpportunities 为 %d。".formatted(
                            top.getCampaignName(), top.getActualOpportunityCount(), top.getWonOpportunityCount())
                    : "Single campaign **%s** generated the most opportunities: %d, with %d won opportunities.".formatted(
                            top.getCampaignName(), top.getActualOpportunityCount(), top.getWonOpportunityCount());
            return directAnswer(language, conclusion, campaignRows(List.of(top)),
                    "campaign performance", "Campaign attainment", List.of());
        }

        if (containsAny(normalized, "商机产出最多")) {
            List<CampaignDealerMetric> metrics = aggregateCampaignsByDealer(filtered).stream()
                    .sorted(Comparator.comparingInt(CampaignDealerMetric::actualOpportunityCount).reversed())
                    .toList();
            return campaignDealerAnswer(language, "活动商机产出最多的经销商", metrics);
        }

        if (scope.dealerCode() != null || containsAny(normalized, "活动效果怎么样")) {
            CampaignDealerMetric metric = aggregateCampaigns(filtered, scope.dealerName() != null ? scope.dealerName() : scope.summary(language));
            String conclusion = "zh".equals(language)
                    ? "%s共有 %d 个活动，目标商机 %d，实际商机 %d，活动商机完成率约 %s；目标订单 %d，赢单 %d。".formatted(
                            metric.dealerName(), metric.campaignCount(), metric.targetOpportunityAmount(),
                            metric.actualOpportunityCount(), formatPercent(metric.opportunityAttainmentRate()),
                            metric.targetOrderAmount(), metric.wonOpportunityCount())
                    : "%s has %d campaigns, target opportunities %d, actual opportunities %d, attainment %s; target orders %d, won %d.".formatted(
                            metric.dealerName(), metric.campaignCount(), metric.targetOpportunityAmount(),
                            metric.actualOpportunityCount(), formatPercent(metric.opportunityAttainmentRate()),
                            metric.targetOrderAmount(), metric.wonOpportunityCount());
            return directAnswer(language, conclusion, campaignDealerRows(List.of(metric)),
                    "campaign performance", "Campaign attainment", List.of());
        }

        if (containsAny(normalized, "活动最多的经销商")) {
            List<CampaignDealerMetric> metrics = aggregateCampaignsByDealer(filtered).stream()
                    .sorted(Comparator.comparingInt(CampaignDealerMetric::campaignCount).reversed())
                    .toList();
            return campaignDealerAnswer(language, "活动数量最多的经销商", metrics);
        }

        if (containsAny(normalized, "总体商机目标", "目标完成情况")) {
            CampaignDealerMetric metric = aggregateCampaigns(filtered, "Campaign 全量汇总");
            String conclusion = "zh".equals(language)
                    ? "Campaign 全量汇总：目标商机 %d，实际活动商机 %d，活动商机目标完成率约 %s；目标订单 %d，赢单 %d，订单目标完成率约 %s。".formatted(
                            metric.targetOpportunityAmount(), metric.actualOpportunityCount(),
                            formatPercent(metric.opportunityAttainmentRate()), metric.targetOrderAmount(),
                            metric.wonOpportunityCount(), formatPercent(metric.orderAttainmentRate()))
                    : "Campaign total: target opportunities %d, actual opportunities %d, opportunity attainment %s; target orders %d, won %d, order attainment %s.".formatted(
                            metric.targetOpportunityAmount(), metric.actualOpportunityCount(),
                            formatPercent(metric.opportunityAttainmentRate()), metric.targetOrderAmount(),
                            metric.wonOpportunityCount(), formatPercent(metric.orderAttainmentRate()));
            return directAnswer(language, conclusion, campaignDealerRows(List.of(metric)),
                    "campaign performance", "Campaign attainment", List.of());
        }

        if (containsAny(normalized, "一共有多少", "活动类型")) {
            List<CountMetric> eventTypes = countBy(filtered, Campaign::getEventType).stream().sorted(countDescending()).toList();
            List<CountMetric> campaignTypes = countBy(filtered, Campaign::getCampaignType).stream().sorted(countDescending()).toList();
            String conclusion = "zh".equals(language)
                    ? "Campaign 表共有 %d 个活动，Type 分布：%s；CampaignType 主要是 %s。".formatted(
                            filtered.size(), joinCountMetrics(eventTypes, 3), joinCountMetrics(campaignTypes, 5))
                    : "Campaign table has %d campaigns. Type distribution: %s; CampaignType: %s.".formatted(
                            filtered.size(), joinCountMetrics(eventTypes, 3), joinCountMetrics(campaignTypes, 5));
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Campaign count", String.valueOf(filtered.size())});
            rows.addAll(countRows("Type", eventTypes, 3));
            rows.addAll(countRows("CampaignType", campaignTypes, 5));
            return directAnswer(language, conclusion, rows,
                    "campaign performance", "Campaign attainment", List.of());
        }

        return null;
    }

    private ScenarioResult campaignDealerAnswer(String language, String title, List<CampaignDealerMetric> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }
        CampaignDealerMetric top = metrics.getFirst();
        String conclusion = "zh".equals(language)
                ? "%s是 **%s**：%d 个活动，目标商机 %d，实际商机 %d，完成率约 %s。".formatted(
                        title, top.dealerName(), top.campaignCount(), top.targetOpportunityAmount(),
                        top.actualOpportunityCount(), formatPercent(top.opportunityAttainmentRate()))
                : "%s is **%s**: %d campaigns, target opportunities %d, actual opportunities %d, attainment %s.".formatted(
                        title, top.dealerName(), top.campaignCount(), top.targetOpportunityAmount(),
                        top.actualOpportunityCount(), formatPercent(top.opportunityAttainmentRate()));
        return directAnswer(language, conclusion, campaignDealerRows(metrics.stream().limit(5).toList()),
                "campaign performance", "Campaign attainment", List.of());
    }

    private ScenarioResult analyzeDataOverview(AnalysisScope scope, String language) {
        return analyzeDataOverview(scope, language, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeDataOverview(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        int dealerCount = cachedDealers().size();
        int opportunityCount = cachedOpportunities().size();
        int leadCount = cachedLeads().size();
        int taskCount = cachedTasks().size();
        int campaignCount = cachedCampaigns().size();

        List<CalcStep> traceSteps = new ArrayList<>();
        traceSteps.add(new CalcStep(
                "加载全部实体表统计数据总量",
                "Load all entity tables and count totals",
                "经销商 " + dealerCount + " | 商机 " + opportunityCount + " | 线索 " + leadCount + " | 任务 " + taskCount + " | 活动 " + campaignCount,
                "Dealers " + dealerCount + " | Opportunities " + opportunityCount + " | Leads " + leadCount + " | Tasks " + taskCount + " | Campaigns " + campaignCount
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "加载全部实体表统计数据总量" : "Load all entity tables and count totals",
                isZh(language)
                        ? "经销商 " + dealerCount + ", 商机 " + opportunityCount + ", 线索 " + leadCount + ", 任务 " + taskCount + ", 活动 " + campaignCount
                        : "Dealers " + dealerCount + ", Opps " + opportunityCount + ", Leads " + leadCount + ", Tasks " + taskCount + ", Campaigns " + campaignCount,
                Map.of("dealerCount", dealerCount, "opportunityCount", opportunityCount, "leadCount", leadCount,
                        "taskCount", taskCount, "campaignCount", campaignCount)));

        if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- 当前系统**经销商** %d 家\n- **商机** %d 条\n- **线索/客户** %d 条\n- **销售任务** %d 条\n- **市场活动** %d 个",
                    dealerCount, opportunityCount, leadCount, taskCount, campaignCount);

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"经销商", String.valueOf(dealerCount)});
            dataRows.add(new String[]{"商机", String.valueOf(opportunityCount)});
            dataRows.add(new String[]{"线索/客户", String.valueOf(leadCount)});
            dataRows.add(new String[]{"销售任务", String.valueOf(taskCount)});
            dataRows.add(new String[]{"市场活动", String.valueOf(campaignCount)});

            List<String> followUps = List.of(
                    "各经销商的门店目标达成率如何？",
                    "商机漏斗各阶段的转化率怎么样？"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows,
                    new SummaryContext("数据概况", 0, "实体总量",
                            String.valueOf(dealerCount + opportunityCount + leadCount + taskCount + campaignCount), "—",
                            null, null, null, null),
                    null, null, List.of(), List.of(), followUps),
                    "data overview", "Entity count", traceSteps);
        }

        String conclusion = String.format(
                "- System has **%d** dealers\n- **%d** opportunities\n- **%d** leads/customers\n- **%d** sales tasks\n- **%d** campaigns",
                dealerCount, opportunityCount, leadCount, taskCount, campaignCount);

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Dealers", String.valueOf(dealerCount)});
        dataRows.add(new String[]{"Opportunities", String.valueOf(opportunityCount)});
        dataRows.add(new String[]{"Leads/Customers", String.valueOf(leadCount)});
        dataRows.add(new String[]{"Sales Tasks", String.valueOf(taskCount)});
        dataRows.add(new String[]{"Campaigns", String.valueOf(campaignCount)});

        List<String> followUps = List.of(
                "What is the target achievement rate by dealer?",
                "How does the opportunity funnel conversion look?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows,
                new SummaryContext("Data Overview", 0, "Total Entities",
                        String.valueOf(dealerCount + opportunityCount + leadCount + taskCount + campaignCount), "—",
                        null, null, null, null),
                null, null, List.of(), List.of(), followUps),
                "data overview", "Entity count", traceSteps);
    }

    private ScenarioResult analyzeDealerBenchmark(AnalysisScope scope, String language) {
        return analyzeDealerBenchmark(scope, language, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeDealerBenchmark(AnalysisScope scope, String language,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
        List<Target> allTargets = cachedTargets();
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                isZh(language) ? "从 Target 表加载全部目标数据" : "Load all target records from Target table",
                isZh(language) ? "共 " + allTargets.size() + " 条记录" : allTargets.size() + " records total",
                Map.of("source_type", "database", "table", "Target", "recordCount", allTargets.size())));

        List<Target> filteredTargets = allTargets.stream()
                .filter(target -> matchesScope(target.getDealerCode(), target.getDealerName(), target.getCity(),
                        target.getDealerGroupName(), target.getProductModel(), scope))
                .filter(target -> scope.timeRange().matchesTarget(target.getTargetYear(), target.getTargetMonth()))
                .toList();

        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
                isZh(language) ? "匹配 " + filteredTargets.size() + " 条记录" : filteredTargets.size() + " matching records",
                Map.of("recordCount", filteredTargets.size())));

        if (filteredTargets.isEmpty()) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "门店对标分析无可用数据" : "No data for dealer benchmark analysis",
                    isZh(language) ? "过滤后无匹配记录" : "No matching records after filtering",
                    Map.of("recordCount", 0)));
            return noDataResult(language, scope, "dealer benchmark");
        }

        List<DealerTargetMetric> allMetrics = buildDealerTargetMetrics(filteredTargets);
        List<DealerTargetMetric> validMetrics = allMetrics.stream()
                .filter(metric -> metric.targetValue() > 0)
                .toList();
        double spread = validMetrics.size() < 2 ? 0.0 : validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(0.0)
                - validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).min().orElse(0.0);
        int primaryNumerator = validMetrics.stream().mapToInt(DealerTargetMetric::wonCount).sum();
        int primaryDenominator = validMetrics.stream().mapToInt(DealerTargetMetric::targetValue).sum();

        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按门店聚合目标与赢单数据" : "Aggregate targets and won deals per dealer",
                isZh(language) ? validMetrics.size() + " 个门店" : validMetrics.size() + " dealers",
                Map.of("dealerCount", validMetrics.size())));

        DataQualityContext quality = classifyRateQuality(
                "dealer benchmark",
                "Achievement rate",
                filteredTargets.size(),
                validMetrics.size(),
                2,
                primaryNumerator,
                primaryDenominator,
                allMetrics.size() - validMetrics.size(),
                spread < 1.0 ? -1.0 : spread,
                false
        );
        if (quality.state() != DataQualityState.NORMAL) {
            emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.calculation,
                    isZh(language) ? "数据质量不足，无法生成分析" : "Insufficient data quality for analysis",
                    isZh(language) ? "状态: " + quality.state().name() : "State: " + quality.state().name(),
                    Map.of("quality_state", quality.state().name())));
            return lowConfidenceResult(language, scope, quality, new ArrayList<>());
        }

        List<DealerTargetMetric> metrics = validMetrics.stream()
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate).reversed())
                .toList();

        DealerTargetMetric best = metrics.getFirst();
        DealerTargetMetric lowest = metrics.getLast();

                if ("zh".equals(language)) {
            String conclusion = String.format(
                    "- **%s** \u7ecf\u8425\u8868\u73b0\u6700\u4f18\uff0c\u8fbe\u6210\u7387 **%s**\uff0c\u9002\u5408\u4f5c\u4e3a\u6a2a\u5411\u6807\u6746\n"
                    + "- **%s** \u6700\u9700\u5173\u6ce8\uff0c\u8fbe\u6210\u7387\u4ec5 **%s**\uff0c\u4e0e\u6807\u6746\u5dee\u8ddd\u8fbe **%s**\n"
                    + "- %d \u5bb6\u95e8\u5e97\u4e2d %d \u5bb6\u4f4e\u4e8e 75%%\uff0c\u6574\u4f53\u7ecf\u8425\u5747\u8861\u6027\u6709\u5f85\u63d0\u5347",
                    best.dealerName(), formatPercent(best.achievementRate()),
                    lowest.dealerName(), formatPercent(lowest.achievementRate()),
                    formatPercent(best.achievementRate() - lowest.achievementRate()),
                    metrics.size(),
                    metrics.stream().filter(m -> m.achievementRate() < 75.0).count()
            );

            List<String[]> dataRows = new ArrayList<>();
            dataRows.add(new String[]{"Best dealer achievement rate", "%s \u2014 %s".formatted(best.dealerName(), formatPercent(best.achievementRate()))});
            dataRows.add(new String[]{"Lowest dealer achievement rate", "%s \u2014 %s".formatted(lowest.dealerName(), formatPercent(lowest.achievementRate()))});
            dataRows.add(new String[]{"Dealers compared", String.valueOf(metrics.size())});
            dataRows.add(new String[]{"Gap (best - lowest)", formatPercent(best.achievementRate() - lowest.achievementRate())});

            List<DealerTargetMetric> sorted = metrics.stream()
                    .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                    .toList();
            List<DealerTargetMetric> chartDealers = bottomTopDistinct(sorted, 3);

            String mermaid = buildMermaidXyChart(
                    "%s \u95e8\u5e97\u8fbe\u6807\u7387\u5bf9\u6bd4".formatted(scope.summary(language)),
                    "\u95e8\u5e97", "\u8fbe\u6210\u7387 (%)",
                    ChartEntityType.DEALER,
                    chartDealers.stream().map(DealerTargetMetric::dealerName).toList(),
                    chartDealers.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                    null
            );
            double maxRate = chartDealers.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(100);
            String fallback = buildFallbackBars(
                    ChartEntityType.DEALER,
                    chartDealers.stream().map(DealerTargetMetric::dealerName).toList(),
                    chartDealers.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                    Math.max(maxRate, 100)
            );

            List<String> attributions = List.of(
                    "%s \u5728\u76ee\u6807\u63a8\u8fdb\u548c\u6210\u4ea4\u8f6c\u5316\u4e0a\u66f4\u5747\u8861\uff0c\u5176\u6d3b\u52a8\u8282\u594f\u53ca\u5546\u673a\u7ba1\u7406\u6a21\u5f0f\u503c\u5f97\u4f5c\u4e3a\u6807\u6746\u7814\u7a76".formatted(best.dealerName()),
                    "%s \u5f53\u524d\u76ee\u6807\u7f3a\u53e3\u66f4\u5927\uff08%d \u53f0\uff09\uff0c\u9700\u8981\u7ed3\u5408\u5546\u673a\u548c\u4efb\u52a1\u72b6\u6001\u4e00\u8d77\u770b\u6267\u884c\u95ee\u9898".formatted(lowest.dealerName(), Math.max(lowest.targetValue() - lowest.wonCount(), 0))
            );

            List<String> recommendations = List.of(
                    "\u590d\u7528 %s \u7684\u7ecf\u8425\u6253\u6cd5\u548c\u6d3b\u52a8\u8282\u594f\uff0c\u4f5c\u4e3a\u6a2a\u5411\u6807\u6746\u63a8\u5e7f".formatted(best.dealerName()),
                    "\u9488\u5bf9 %s \u505a\u4e13\u9879\u8ddf\u8fdb\uff0c\u4f18\u5148\u7f29\u5c0f\u76ee\u6807\u4e0e\u6210\u4ea4\u5dee\u8ddd".formatted(lowest.dealerName())
            );

            List<String> followUps = List.of(
                    "\u6700\u4f18\u95e8\u5e97\u548c\u6700\u4f4e\u95e8\u5e97\u7684\u5546\u673a\u7ed3\u6784\u5dee\u522b\u5728\u54ea\u91cc\uff1f",
                    "\u9700\u8981\u5173\u6ce8\u7684\u95e8\u5e97\u8fd1\u671f\u6d3b\u52a8\u6548\u679c\u600e\u4e48\u6837\uff1f"
            );

            return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                    "dealer benchmark", "Achievement rate", new ArrayList<>());
        }
        String conclusion = String.format(
                "- **%s** is the top performer at **%s** \u2014 the benchmark for others.\n"
                + "- **%s** needs the most attention at only **%s**, trailing the leader by **%s**.\n"
                + "- %d out of %d dealers are below 75%% \u2014 operating balance needs improvement.",
                best.dealerName(), formatPercent(best.achievementRate()),
                lowest.dealerName(), formatPercent(lowest.achievementRate()),
                formatPercent(best.achievementRate() - lowest.achievementRate()),
                metrics.stream().filter(m -> m.achievementRate() < 75.0).count(),
                metrics.size()
        );

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{"Best dealer achievement rate", "%s \u2014 %s".formatted(best.dealerName(), formatPercent(best.achievementRate()))});
        dataRows.add(new String[]{"Lowest dealer achievement rate", "%s \u2014 %s".formatted(lowest.dealerName(), formatPercent(lowest.achievementRate()))});
        dataRows.add(new String[]{"Dealers compared", String.valueOf(metrics.size())});
        dataRows.add(new String[]{"Gap (best - lowest)", formatPercent(best.achievementRate() - lowest.achievementRate())});

        List<DealerTargetMetric> sortedEn = metrics.stream()
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                .toList();
        List<DealerTargetMetric> chartDealersEn = bottomTopDistinct(sortedEn, 3);

        String mermaid = buildMermaidXyChart(
                "Dealer Benchmark \u2014 %s".formatted(scope.summary(language)),
                "Dealer", "Attainment (%)",
                ChartEntityType.DEALER,
                chartDealersEn.stream().map(DealerTargetMetric::dealerName).toList(),
                chartDealersEn.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                null
        );
        double maxRateEn = chartDealersEn.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(100);
        String fallback = buildFallbackBars(
                ChartEntityType.DEALER,
                chartDealersEn.stream().map(DealerTargetMetric::dealerName).toList(),
                chartDealersEn.stream().map(DealerTargetMetric::achievementRate).collect(Collectors.toList()),
                Math.max(maxRateEn, 100)
        );

        List<String> attributions = List.of(
                "%s is balanced across target execution and conversion \u2014 its operating model merits deeper study.".formatted(best.dealerName()),
                "%s has a larger target gap (%d units) \u2014 review alongside its opportunity and task execution.".formatted(lowest.dealerName(), Math.max(lowest.targetValue() - lowest.wonCount(), 0))
        );

        List<String> recommendations = List.of(
                "Replicate %s's operating rhythm and campaign approach as the cross-dealer benchmark.".formatted(best.dealerName()),
                "Run a focused improvement sprint for %s to close the target and win-gap.".formatted(lowest.dealerName())
        );

        List<String> followUps = List.of(
                "How does the opportunity mix differ between the best and lowest dealer?",
                "What has recent campaign performance looked like for the dealer that needs attention?"
        );

        return normalResult(buildEnrichedReply(language, conclusion, dataRows, null, mermaid, fallback, attributions, recommendations, followUps),
                "dealer benchmark", "Achievement rate", new ArrayList<>());
    }

    private ScenarioResult analyzeDealerBusinessActivity(
            AnalysisScope scope, String language, String message) {
        return analyzeDealerBusinessActivity(scope, language, message, "legacy", new AtomicInteger(1), null);
    }

    private ScenarioResult analyzeDealerBusinessActivity(
            AnalysisScope scope, String language, String message,
            String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {

        List<CalcStep> traceSteps = new ArrayList<>();
        String normalized = message != null ? normalize(message) : "";
        boolean includeDormant = containsAny(normalized,
                "休眠", "dormant", "不活跃", "inactive");

        // Determine 12-month window: [currentMonth - 11, currentMonth]
        LocalDate today = LocalDate.now(clock);
        int endYear = today.getYear();
        int endMonth = today.getMonthValue();
        LocalDate windowStart = today.minusMonths(11).withDayOfMonth(1);
        int startYear = windowStart.getYear();
        int startMonth = windowStart.getMonthValue();

        traceSteps.add(new CalcStep(
                "确定12个月统计窗口",
                "Determine 12-month statistics window",
                String.format("%d-%02d 至 %d-%02d", startYear, startMonth, endYear, endMonth),
                String.format("%d-%02d to %d-%02d", startYear, startMonth, endYear, endMonth)
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                isZh(language) ? "确定12个月统计窗口" : "Determine 12-month statistics window",
                isZh(language) ? String.format("%d-%02d 至 %d-%02d", startYear, startMonth, endYear, endMonth) : String.format("%d-%02d to %d-%02d", startYear, startMonth, endYear, endMonth),
                Map.of("window_start", String.format("%d-%02d", startYear, startMonth), "window_end", String.format("%d-%02d", endYear, endMonth))));

        // Load Target data within window
        List<Target> windowTargets = cachedTargets().stream()
                .filter(t -> isWithinWindow(t.getTargetYear(), t.getTargetMonth(),
                        startYear, startMonth, endYear, endMonth))
                .filter(t -> matchesScope(t, scope))
                .toList();

        // Group targets by dealerCode + business month
        Map<String, Map<String, List<Target>>> byDealerMonth = windowTargets.stream()
                .collect(Collectors.groupingBy(
                        Target::getDealerCode,
                        Collectors.groupingBy(
                                t -> t.getTargetYear() + "-" + String.format("%02d", t.getTargetMonth()))
                ));

        // Build list of 12 month keys in order
        List<String> monthKeys = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            LocalDate d = windowStart.plusMonths(m);
            monthKeys.add(d.getYear() + "-" + String.format("%02d", d.getMonthValue()));
        }

        // Determine set of dealers to score
        Set<String> dealerCodesToScore;
        Map<String, Dealer> dealerByCode = new LinkedHashMap<>();
        if (includeDormant) {
            // Full roster: all dealers matching scope
            List<Dealer> allDealers = cachedDealers().stream()
                    .filter(d -> scopeMatchesDealer(d, scope))
                    .toList();
            for (Dealer d : allDealers) {
                dealerByCode.put(d.getDealerCode(), d);
            }
            dealerCodesToScore = dealerByCode.keySet();
        } else {
            // Only dealers with at least one Target record in window
            dealerCodesToScore = byDealerMonth.keySet();
            for (Target t : windowTargets) {
                String code = t.getDealerCode();
                if (!dealerByCode.containsKey(code)) {
                    dealerByCode.put(code, new Dealer(code, t.getDealerName(), t.getCity(), t.getDealerGroupName()));
                }
            }
        }

        // Score each dealer
        List<DealerActivityScore> scores = new ArrayList<>();
        for (String dealerCode : dealerCodesToScore) {
            Map<String, List<Target>> monthData = byDealerMonth.getOrDefault(dealerCode, Map.of());
            Dealer dealer = dealerByCode.get(dealerCode);

            int targetMonths = 0, createMonths = 0, wonMonths = 0, dataMonths = 0;
            int firstDataMonthIndex = -1;

            for (int i = 0; i < monthKeys.size(); i++) {
                String mk = monthKeys.get(i);
                List<Target> monthTargets = monthData.getOrDefault(mk, List.of());
                if (!monthTargets.isEmpty()) {
                    dataMonths++;
                    if (firstDataMonthIndex == -1) firstDataMonthIndex = i;
                    boolean hasTarget = monthTargets.stream().anyMatch(t -> t.getAsKTarget() != null && t.getAsKTarget() >= 1);
                    boolean hasCreate = monthTargets.stream().anyMatch(t -> t.getOpportunityCreateCount() != null && t.getOpportunityCreateCount() >= 1);
                    boolean hasWon = monthTargets.stream().anyMatch(t -> t.getOpportunityWonCount() != null && t.getOpportunityWonCount() >= 1);
                    if (hasTarget) targetMonths++;
                    if (hasCreate) createMonths++;
                    if (hasWon) wonMonths++;
                }
            }

            int totalScore = targetMonths + createMonths + wonMonths + dataMonths;
            int observedMonths = firstDataMonthIndex >= 0 ? (12 - firstDataMonthIndex) : 0;
            boolean isNewStore = observedMonths > 0 && observedMonths < 12;

            scores.add(new DealerActivityScore(
                    dealerCode,
                    dealer != null ? dealer.getDealerName() : "",
                    dealer != null ? dealer.getCity() : "",
                    dealer != null ? dealer.getDealerGroupName() : "",
                    targetMonths, createMonths, wonMonths, dataMonths,
                    totalScore, isNewStore, observedMonths
            ));
        }

        scores.sort(Comparator.comparingInt(DealerActivityScore::totalScore).reversed()
                .thenComparing(DealerActivityScore::dealerCode));

        traceSteps.add(new CalcStep(
                "按门店计算4维度得分并分级",
                "Score each dealer across 4 dimensions and assign tier",
                scores.size() + " 个门店",
                scores.size() + " dealers"
        ));
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                isZh(language) ? "按门店计算4维度得分并分级" : "Score each dealer across 4 dimensions and assign tier",
                isZh(language) ? scores.size() + " 个门店" : scores.size() + " dealers",
                Map.of("dealerCount", scores.size())));

        return buildDealerActivityResult(scope, language, traceSteps, scores,
                startYear, startMonth, endYear, endMonth, includeDormant, windowTargets.size());
    }

    private List<String> buildProgressMessages(
            String language,
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow
    ) {
        if ("zh".equals(language)) {
            return List.of(
                    "正在进行意图识别与场景归类",
                    "正在按工具链执行：" + scenarioWorkflow.toolChainSummary(),
                    "正在处理数据并生成结构化报告"
            );
        }

        return List.of(
                "Identifying the intent and routing the scenario",
                "Executing tool chain: " + scenarioWorkflow.toolChainSummary(),
                "Processing facts and generating the structured report"
        );
    }

    private String buildGroundedReference(
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow,
            String scopeSummary,
            String language,
            String fallbackReply,
            DataQualityContext quality
    ) {
        return """
                Scenario: %s
                Scenario Label: %s
                Scope: %s
                Language: %s
                Workflow: %s
                Tool Chain: %s
                Logic Summary: %s
                Data Quality: %s
                Chart Suppressed: %s%s
                Canonical fallback report:
                %s
                """.formatted(
                scenarioWorkflow.scenario(),
                scenarioWorkflow.label(language),
                scopeSummary,
                language,
                AnalyticsScenarioCatalog.workflowSummary(),
                scenarioWorkflow.toolChainSummary(),
                scenarioWorkflow.logicSummary(language),
                quality.state(),
                quality.suppressChart(),
                quality.suppressChart() ? " (" + quality.chartSuppressionReason() + ")" : "",
                fallbackReply
        );
    }

    private AnalyticsPlan.Scenario mapScenario(AnalysisTopic topic) {
        return switch (topic) {
            case TARGET_ACHIEVEMENT -> AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT;
            case OPPORTUNITY_FUNNEL -> AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL;
            case SALES_FOLLOW_UP -> AnalyticsPlan.Scenario.SALES_FOLLOW_UP;
            case CAMPAIGN_PERFORMANCE -> AnalyticsPlan.Scenario.CAMPAIGN_PERFORMANCE;
            case LEAD_SOURCE -> AnalyticsPlan.Scenario.LEAD_SOURCE;
            case DEALER_BUSINESS_ACTIVITY -> AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY;
            case DEALER_BENCHMARK -> AnalyticsPlan.Scenario.DEALER_BENCHMARK;
            case DATA_OVERVIEW -> AnalyticsPlan.Scenario.DATA_OVERVIEW;
        };
    }

    private ScenarioResult normalResult(String reply, String scenarioLabel, String primaryMetricLabel, List<CalcStep> traceSteps) {
        return new ScenarioResult(reply, quality(scenarioLabel, primaryMetricLabel, 0, 0, 0, 0, 0, 0,
                DataQualityState.NORMAL, ChartSuppressionReason.NONE), traceSteps);
    }

    private ScenarioResult noDataResult(String language, AnalysisScope scope, String scenarioLabel) {
        return new ScenarioResult(
                buildNoDataReply(language, scope, scenarioLabel),
                quality(scenarioLabel, scenarioLabel, 0, 0, 1, 0, 0, 0,
                        DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA)
        );
    }

    private ScenarioResult lowConfidenceResult(String language, AnalysisScope scope, DataQualityContext quality, List<CalcStep> traceSteps) {
        List<CalcStep> steps = new ArrayList<>(traceSteps);
        steps.add(buildDataQualityExplanationStep(language, quality));
        return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality, steps);
    }

    private CalcStep buildDataQualityExplanationStep(String language, DataQualityContext quality) {
        DataQualityBusinessTerms terms = dataQualityBusinessTerms(language, quality.primaryMetricLabel());
        String validUnitsMeaning = "zh".equals(language)
                ? terms.comparableLabel() + "：" + quality.validComparableUnits() + " " + terms.comparableUnit()
                        + "（最低需要 " + quality.requiredComparableUnits() + " " + terms.comparableUnit() + "）"
                : terms.comparableLabel() + ": " + quality.validComparableUnits()
                        + " (minimum required: " + quality.requiredComparableUnits() + ")";
        String numeratorInfo = "zh".equals(language)
                ? terms.numeratorLabel() + "：" + quality.primaryNumerator()
                : terms.numeratorLabel() + ": " + quality.primaryNumerator();
        String denominatorInfo = "zh".equals(language)
                ? terms.denominatorLabel() + "：" + quality.primaryDenominator()
                : terms.denominatorLabel() + ": " + quality.primaryDenominator();
        String stateDesc = "zh".equals(language)
                ? "触发原因：" + describeDataQualityState(language, quality)
                : "Reason: " + describeDataQualityState(language, quality);
        String excludedInfo = quality.excludedUnits() > 0
                ? ("zh".equals(language)
                        ? "；已排除 " + quality.excludedUnits() + " " + terms.comparableUnit() + terms.invalidComparableLabel()
                        : "; excluded " + quality.excludedUnits() + " " + terms.invalidComparableLabel())
                : "";

        String detailZh = validUnitsMeaning + "；" + numeratorInfo + "；" + denominatorInfo + "；" + stateDesc + excludedInfo;
        String detailEn = validUnitsMeaning + "; " + numeratorInfo + "; " + denominatorInfo + "; " + stateDesc + excludedInfo;
        String labelZh = "数据质量判定";
        String labelEn = "Data quality assessment";

        // Ensure bilingual strings are correct
        if ("zh".equals(language)) {
            return new CalcStep(labelZh, labelEn, detailZh, "");
        } else {
            return new CalcStep(labelZh, labelEn, "", detailEn);
        }
    }

    private DataQualityContext classifyRateQuality(
            String scenarioLabel,
            String primaryMetricLabel,
            int observedRows,
            int validComparableUnits,
            int requiredComparableUnits,
            int primaryNumerator,
            int primaryDenominator,
            int excludedUnits,
            double spreadPercentagePoints,
            boolean allZeroSignalIsLowConfidence
    ) {
        if (observedRows == 0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                    DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA);
        }
        if (primaryDenominator <= 0 || validComparableUnits == 0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                    DataQualityState.DENOMINATOR_ZERO, ChartSuppressionReason.DENOMINATOR_ZERO);
        }
        if (primaryNumerator <= 0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                    DataQualityState.ALL_ZERO_SIGNAL, ChartSuppressionReason.ALL_ZERO_SIGNAL);
        }
        if (validComparableUnits < requiredComparableUnits || spreadPercentagePoints < 0.0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                    DataQualityState.INSUFFICIENT_SAMPLE, ChartSuppressionReason.INSUFFICIENT_SAMPLE);
        }
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                DataQualityState.NORMAL, ChartSuppressionReason.NONE);
    }

    private DataQualityContext classifyCountQuality(
            String scenarioLabel,
            String primaryMetricLabel,
            int observedRows,
            int validComparableUnits,
            int requiredComparableUnits,
            int primaryNumerator,
            int primaryDenominator,
            boolean allZeroSignalIsLowConfidence
    ) {
        if (observedRows == 0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                    DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA);
        }
        if (primaryNumerator <= 0) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                    DataQualityState.ALL_ZERO_SIGNAL, ChartSuppressionReason.ALL_ZERO_SIGNAL);
        }
        if (observedRows < requiredComparableUnits || validComparableUnits < 2) {
            return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                    requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                    DataQualityState.INSUFFICIENT_SAMPLE, ChartSuppressionReason.INSUFFICIENT_SAMPLE);
        }
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                DataQualityState.NORMAL, ChartSuppressionReason.NONE);
    }

    private DataQualityContext quality(
            String scenarioLabel,
            String primaryMetricLabel,
            int observedRows,
            int validComparableUnits,
            int requiredComparableUnits,
            int primaryNumerator,
            int primaryDenominator,
            int excludedUnits,
            DataQualityState state,
            ChartSuppressionReason chartSuppressionReason
    ) {
        return new DataQualityContext(
                state,
                scenarioLabel,
                primaryMetricLabel,
                observedRows,
                validComparableUnits,
                requiredComparableUnits,
                primaryNumerator,
                primaryDenominator,
                excludedUnits,
                chartSuppressionReason
        );
    }

    private void logDataQuality(String language, AnalysisScope scope, DataQualityContext quality) {
        if (quality.state() == DataQualityState.NORMAL) {
            return;
        }
        LOGGER.debug(
                "analytics data quality scenario={} language={} scope={} state={} observedRows={} validComparableUnits={} requiredComparableUnits={} excludedUnits={} primaryMetric={} chartSuppressed={} chartSuppressionReason={}",
                quality.scenarioLabel(),
                language,
                scope.summary(language),
                quality.state(),
                quality.observedRows(),
                quality.validComparableUnits(),
                quality.requiredComparableUnits(),
                quality.excludedUnits(),
                quality.primaryMetricLabel(),
                quality.suppressChart(),
                quality.chartSuppressionReason()
        );
    }

    private String buildLowConfidenceReply(String language, AnalysisScope scope, DataQualityContext quality) {
        boolean isZh = "zh".equals(language);
        String scopeSummary = scope.summary(language);
        String topicLabel = localizeTopicLabel(language, quality.scenarioLabel());
        String metricLabel = isZh ? quality.primaryMetricLabel() : quality.primaryMetricLabel();
        String conclusion = isZh
                ? "- \u5f53\u524d\u8303\u56f4\u6709\u8bb0\u5f55\uff0c\u4f46 %s \u4fe1\u53f7\u4e0d\u8db3\uff0c\u6682\u4e0d\u652f\u6301\u53ef\u9760\u6392\u540d\u6216\u5b9e\u8df5\u63d0\u70bc\n- \u6570\u636e\u72b6\u6001\uff1a%s\uff0c\u5efa\u8bae\u5148\u6269\u5927\u8303\u56f4\u6216\u68c0\u67e5\u6570\u636e\u5b57\u6bb5\u914d\u7f6e"
                        .formatted(topicLabel, describeDataQualityState(language, quality))
                : "- Records exist in %s for %s, but %s does not have enough reliable signal for ranking or practice extraction.\n- Data quality state: %s. Broaden the scope or verify the metric configuration before using this result operationally.%s"
                        .formatted(scopeSummary, sentenceCase(topicLabel), metricLabel, describeDataQualityState(language, quality), lowConfidenceObservedSentence(quality));

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Requested scope", scopeSummary});
        rows.add(new String[]{"Requested topic", sentenceCase(topicLabel)});
        rows.add(new String[]{isZh ? "\u6570\u636e\u8d28\u91cf" : "Data quality", describeDataQualityState(language, quality)});
        rows.add(new String[]{"Observed rows", String.valueOf(quality.observedRows())});
        rows.add(new String[]{"Primary numerator", String.valueOf(quality.primaryNumerator())});
        rows.add(new String[]{"Primary denominator", String.valueOf(quality.primaryDenominator())});
        if (quality.excludedUnits() > 0) {
            rows.add(new String[]{"Excluded units", String.valueOf(quality.excludedUnits())});
        }

        List<String> attributions = isZh
                ? List.of(
                        "\u5f53\u524d\u6570\u636e\u72b6\u6001\u4e3a\u300c" + describeDataQualityState(language, quality) + "\u300d\uff0c\u56e0\u6b64\u4ec5\u80fd\u8bf4\u660e\u6570\u91cf\u4e8b\u5b9e",
                        "\u56fe\u8868\u5df2\u9690\u85cf\uff0c\u539f\u56e0\u662f\u300c" + describeChartSuppressionReason(language, quality.chartSuppressionReason()) + "\u300d"
                )
                : List.of(
                        "The current data quality state is " + describeDataQualityState(language, quality) + ", so the reply is limited to factual counts.",
                        "The chart is hidden because " + describeChartSuppressionReason(language, quality.chartSuppressionReason()) + "."
                );

        List<String> recommendations = isZh
                ? List.of(
                        "\u5148\u6269\u5927\u5230\u57ce\u5e02\u6216\u6574\u4f53\u6837\u672c\u8303\u56f4\u518d\u6bd4\u8f83",
                        "\u68c0\u67e5 " + metricLabel + " \u7684\u5206\u5b50\u548c\u5206\u6bcd\u5b57\u6bb5\u662f\u5426\u5b8c\u6574"
                )
                : List.of(
                        "Broaden the scope to city level or the full sample before comparing entities.",
                        "Verify the numerator and denominator fields for " + metricLabel + "."
                );

        List<String> followUps = isZh
                ? List.of(
                        "\u653e\u5927\u5230\u6574\u4e2a\u6837\u672c\u540e\u4f1a\u770b\u5230\u4ec0\u4e48\uff1f",
                        "\u5f53\u524d\u8303\u56f4\u8fd8\u6709\u54ea\u4e9b\u53ef\u7528\u6307\u6807\uff1f"
                )
                : List.of(
                        "What does this look like across the full sample dataset?",
                        "Which related metrics are available in this scope?"
                );

        return buildEnrichedReply(language, conclusion, rows, null, null, buildChartEmptyFence(language, quality),
                attributions, recommendations, followUps);
    }

    private String describeDataQualityState(String language, DataQualityContext quality) {
        boolean isZh = "zh".equals(language);
        String metricLabel = dataQualityBusinessTerms(language, quality.primaryMetricLabel()).metricLabel();
        return switch (quality.state()) {
            case NO_DATA -> isZh ? "无匹配数据" : "No data";
            case DENOMINATOR_ZERO -> isZh ? "分母为 0，无法计算比率" : "Denominator is zero";
            case ALL_ZERO_SIGNAL -> isZh
                    ? metricLabel + "均为 0"
                    : metricLabel + " is zero across all comparable units";
            case INSUFFICIENT_SAMPLE -> isZh ? "样本量不足" : "Insufficient sample";
            case NORMAL -> isZh ? "正常" : "Normal";
        };
    }

    private DataQualityBusinessTerms dataQualityBusinessTerms(String language, String primaryMetricLabel) {
        boolean isZh = "zh".equals(language);
        return switch (primaryMetricLabel) {
            case "Achievement rate" -> isZh
                    ? new DataQualityBusinessTerms("目标达成率", "可对比门店", "家", "赢单数合计", "目标数合计", "目标值/分母无效的门店")
                    : new DataQualityBusinessTerms("Target achievement rate", "Comparable dealers", "", "Won deals total", "Target total", "dealers with invalid targets or denominators");
            case "Campaign attainment" -> isZh
                    ? new DataQualityBusinessTerms("活动达成率", "可对比活动", "场", "实际产出商机合计", "活动目标合计", "目标值/分母无效的活动")
                    : new DataQualityBusinessTerms("Campaign attainment", "Comparable campaigns", "", "Actual opportunities total", "Campaign target total", "campaigns with invalid targets or denominators");
            case "Lead conversion" -> isZh
                    ? new DataQualityBusinessTerms("线索转化率", "可对比线索来源", "个", "已转化线索合计", "线索总量", "数据无效的线索来源")
                    : new DataQualityBusinessTerms("Lead conversion rate", "Comparable lead sources", "", "Converted leads total", "Lead total", "lead sources with invalid data");
            case "Opportunity count" -> isZh
                    ? new DataQualityBusinessTerms("商机转化信号", "可分析商机样本", "条", "赢单商机合计", "商机总量", "数据无效的商机")
                    : new DataQualityBusinessTerms("Opportunity conversion signal", "Opportunity sample", "", "Won opportunities total", "Opportunity total", "opportunities with invalid data");
            case "Task backlog" -> isZh
                    ? new DataQualityBusinessTerms("跟进积压率", "可对比门店", "家", "未完成任务合计", "跟进任务总量", "数据无效的门店")
                    : new DataQualityBusinessTerms("Follow-up backlog rate", "Comparable dealers", "", "Open tasks total", "Task total", "dealers with invalid data");
            case "Follow-up activity" -> isZh
                    ? new DataQualityBusinessTerms("跟进活跃度", "可对比门店", "家", "已完成任务合计", "跟进任务总量", "数据无效的门店")
                    : new DataQualityBusinessTerms("Follow-up activity", "Comparable dealers", "", "Completed tasks total", "Task total", "dealers with invalid data");
            default -> isZh
                    ? new DataQualityBusinessTerms(localizeTopicLabel(language, primaryMetricLabel), "可对比对象", "个", "业务产出合计", "业务基数合计", "数据无效的对象")
                    : new DataQualityBusinessTerms(primaryMetricLabel, "Comparable objects", "", "Business output total", "Business base total", "objects with invalid data");
        };
    }

    private String describeChartSuppressionReason(String language, ChartSuppressionReason reason) {
        boolean isZh = "zh".equals(language);
        return switch (reason) {
            case NONE -> isZh ? "无" : "None";
            case NO_DATA -> isZh ? "无匹配数据" : "No data";
            case DENOMINATOR_ZERO -> isZh ? "分母为 0" : "Denominator is zero";
            case ALL_ZERO_SIGNAL -> isZh ? "关键指标均为 0" : "Key metric is zero";
            case INSUFFICIENT_SAMPLE -> isZh ? "样本量不足" : "Insufficient sample";
            case TOO_FEW_POINTS -> isZh ? "数据点过少" : "Too few data points";
            case EMPTY_LABELS -> isZh ? "标签为空" : "Empty labels";
        };
    }

    private String lowConfidenceObservedSentence(DataQualityContext quality) {
        if ("opportunity funnel".equals(quality.scenarioLabel())) {
            return " **%d** opportunities in scope observed.".formatted(quality.observedRows());
        }
        if ("sales follow-up".equals(quality.scenarioLabel())) {
            return " **%d** tasks observed.".formatted(quality.observedRows());
        }
        return "";
    }

    private String buildChartEmptyFence(String language, DataQualityContext quality) {
        boolean isZh = "zh".equals(language);
        String title = isZh ? "\u6682\u65e0\u53ef\u89c6\u5316\u4fe1\u53f7" : "No visual signal";
        String body = isZh
                ? "\u5f53\u524d\u6570\u636e\u4e0d\u652f\u6301\u53ef\u9760\u6392\u540d\u56fe\u8868\uff0c\u56e0\u6b64\u5df2\u9690\u85cf\u53ef\u89c6\u5316\u3002"
                : "The chart is hidden because the available data is not reliable enough for a ranked visualization.";
        return "```chart-empty\nreason: %s\ntitle: %s\nbody: %s\n```"
                .formatted(quality.chartSuppressionReason(), title, body);
    }

    private String sentenceCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String buildNoDataReply(String language, AnalysisScope scope, String topicLabel) {
        if ("zh".equals(language)) {
            String localizedTopicLabel = localizeTopicLabel(language, topicLabel);
            return buildEnrichedReply(
                    language,
                    "- \u5f53\u524d\u5728 %s \u8303\u56f4\u5185\uff0c\u7f3a\u5c11\u8db3\u591f\u6570\u636e\u6765\u652f\u6301\u53ef\u9760\u7684 %s \u5206\u6790\n- \u5efa\u8bae\u6269\u5927\u5206\u6790\u8303\u56f4\u6216\u5207\u6362\u5230\u5176\u4ed6\u6307\u6807\u4e3b\u9898\u4ee5\u83b7\u5f97\u6709\u6548\u4fe1\u53f7".formatted(scope.summary(language), localizedTopicLabel),
                    List.of(
                            new String[]{"Available sample records", "0 matching rows"},
                            new String[]{"Requested scope", scope.summary(language)},
                            new String[]{"Requested topic", localizedTopicLabel}
                    ),
                    null,
                    null,
                    null,
                    List.of(
                            "\u5f53\u524d\u8303\u56f4\u5185\u6ca1\u6709\u8db3\u591f\u7684\u5339\u914d\u8bb0\u5f55\uff0c\u6682\u65f6\u65e0\u6cd5\u5f62\u6210\u53ef\u9760\u7ed3\u8bba",
                            "\u5982\u679c\u6269\u5927\u5206\u6790\u8303\u56f4\uff0c\u6216\u5207\u6362\u5230\u5176\u4ed6\u6307\u6807\u4e3b\u9898\uff0c\u66f4\u53ef\u80fd\u5f97\u5230\u6709\u6548\u4fe1\u53f7"
                    ),
                    List.of(
                            "\u5148\u5c06\u8303\u56f4\u653e\u5bbd\u5230\u57ce\u5e02\u7ea7\u522b\uff0c\u6216\u76f4\u63a5\u67e5\u770b\u6574\u4e2a\u6837\u672c\u6570\u636e\u96c6",
                            "\u6216\u5207\u6362\u5230\u76ee\u6807\u8fbe\u6210\u3001\u6d3b\u52a8\u8868\u73b0\u3001\u7ebf\u7d22\u6765\u6e90\u7b49\u5176\u4ed6\u5206\u6790\u4e3b\u9898"
                    ),
                    List.of(
                            "\u5982\u679c\u653e\u5927\u5230\u6574\u4e2a\u6837\u672c\u6570\u636e\u96c6\uff0c\u8fd9\u4e2a\u4e3b\u9898\u4f1a\u5448\u73b0\u4ec0\u4e48\u7ed3\u679c\uff1f",
                            "\u5728\u5f53\u524d\u8303\u56f4\u5185\uff0c\u8fd8\u6709\u54ea\u4e9b\u53ef\u7528\u7684\u8fd0\u8425\u6307\u6807\u53ef\u4ee5\u5206\u6790\uff1f"
                    )
            );
        }
        String safeScope = escapeHtml(scope.summary(language));
        String safeTopicLabel = escapeHtml(topicLabel);

        return buildEnrichedReply(
                language,
                "- There is not enough data within %s to support a reliable %s answer.\n- Try broadening the scope or switching to another metric family.".formatted(safeScope, safeTopicLabel),
                List.of(
                        new String[]{"Available sample records", "0 matching rows"},
                        new String[]{"Requested scope", scope.summary(language)},
                        new String[]{"Requested topic", topicLabel}
                ),
                null,
                null,
                null,
                List.of(
                        "The current scope does not contain enough matching records to compute a reliable answer.",
                        "A broader scope or a different metric family should provide a stronger signal."
                ),
                List.of(
                        "Try widening the scope to a city-level or the full sample dataset.",
                        "Or switch to another angle such as target achievement, campaign performance, or lead sources."
                ),
                List.of(
                        "What does this look like across the full sample dataset?",
                        "Which other operating metrics are available in this scope?"
                )
        );
    }

    private String localizeTopicLabel(String language, String topicLabel) {
        if (!"zh".equals(language)) {
            return topicLabel;
        }
        return switch (topicLabel) {
            case "target achievement" -> "\u76ee\u6807\u8fbe\u6210";
            case "opportunity funnel" -> "\u5546\u673a\u6f0f\u6597";
            case "sales follow-up" -> "\u9500\u552e\u8ddf\u8fdb";
            case "campaign performance" -> "\u6d3b\u52a8\u8868\u73b0";
            case "lead source analysis" -> "\u7ebf\u7d22\u6765\u6e90\u5206\u6790";
            case "dealer benchmark" -> "\u7ecf\u9500\u5546\u5bf9\u6807";
            default -> topicLabel;
        };
    }

    @Deprecated
    private String buildFallbackReply(
            String language,
            String conclusion,
            List<String[]> dataRows,
            List<String> analysisPoints,
            List<String> recommendationPoints,
            List<String> followUpQuestions
    ) {
        return """
                ## Conclusion
                %s

                ## Data Support
                %s

                ## Short Analysis
                %s

                FOLLOW_UP_QUESTIONS:
                %s
                """.formatted(
                escapeHtml(conclusion),
                buildHtmlTable(dataRows, language),
                buildBulletList(Stream.concat(analysisPoints.stream(), recommendationPoints.stream()).toList()),
                buildFollowUpQuestions(followUpQuestions)
        );
    }

    private String buildHtmlTable(List<String[]> rows, String language) {
        return reportRenderer.htmlTable(rows, language);
    }

    private String buildSummaryTable(SummaryContext ctx, String language) {
        boolean isZh = "zh".equals(language);
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n<thead><tr>");
        sb.append(isZh
                ? "<th>指标</th><th>数值</th><th>范围</th><th>对比基准</th>"
                : "<th>Metric</th><th>Value</th><th>Scope</th><th>Benchmark</th>");
        sb.append("</tr></thead>\n<tbody>\n");

        sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>—</td></tr>\n",
                isZh ? "分析场景" : "Analysis Scenario",
                escapeHtml(ctx.scenarioLabel()),
                isZh ? "当前范围" : "Current scope"));

        sb.append(String.format("<tr><td>%s</td><td>%d</td><td>%s</td><td>—</td></tr>\n",
                isZh ? "覆盖单位数" : "Units covered",
                ctx.totalUnits(),
                isZh ? "当前范围" : "Current scope"));

        sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n",
                escapeHtml(ctx.primaryMetricLabel()),
                escapeHtml(ctx.primaryValue()),
                isZh ? "当前范围" : "Current scope",
                escapeHtml(ctx.primaryBenchmark())));

        if (ctx.bestUnitLabel() != null && !ctx.bestUnitLabel().isBlank()) {
            sb.append(String.format("<tr><td>%s</td><td>%s (%s)</td><td>%s</td><td>—</td></tr>\n",
                    isZh ? "最佳表现" : "Best performer",
                    escapeHtml(ctx.bestUnitLabel()),
                    escapeHtml(ctx.bestUnitValue()),
                    escapeHtml(ctx.scenarioLabel())));
        }

        if (ctx.worstUnitLabel() != null && !ctx.worstUnitLabel().isBlank()) {
            sb.append(String.format("<tr><td>%s</td><td>%s (%s)</td><td>%s</td><td>—</td></tr>\n",
                    isZh ? "最需关注" : "Needs attention",
                    escapeHtml(ctx.worstUnitLabel()),
                    escapeHtml(ctx.worstUnitValue()),
                    escapeHtml(ctx.scenarioLabel())));
        }

        sb.append("</tbody>\n</table>");
        return sb.toString();
    }

    private String buildFallbackSummaryTable(List<String[]> rows, String language) {
        return reportRenderer.fallbackSummaryTable(rows, language);
    }

    private ScenarioResult directAnswer(
            String language,
            String conclusion,
            List<String[]> rows,
            String scenarioLabel,
            String primaryMetricLabel,
            List<CalcStep> traceSteps
    ) {
        String reply = buildDirectReply(language, conclusion, rows, defaultFollowUps(language));
        return normalResult(reply, scenarioLabel, primaryMetricLabel, traceSteps);
    }

    private String buildDirectReply(String language, String conclusion, List<String[]> rows, List<String> followUps) {
        boolean isZh = "zh".equals(language);
        StringBuilder body = new StringBuilder();
        body.append(isZh ? "## 核心结论\n\n" : "## Conclusion\n\n");
        body.append(escapeHtml(conclusion)).append("\n\n");
        body.append(isZh ? "## 数据支撑\n\n" : "## Data Support\n\n");
        body.append(buildHtmlTable(rows, language)).append("\n\n");
        body.append(isZh ? "追问：\n\n" : "FOLLOW_UP_QUESTIONS:\n\n");
        for (int i = 0; i < followUps.size(); i++) {
            body.append(i + 1).append(". ").append(escapeHtml(followUps.get(i))).append("\n");
        }
        return body.toString();
    }

    private List<String> defaultFollowUps(String language) {
        if ("zh".equals(language)) {
            return List.of("是否需要按经销商继续拆分？", "是否需要查看相关明细数据？");
        }
        return List.of("Should I break this down by dealer?", "Would you like the related detail rows?");
    }

    private boolean isDirectTargetQuestion(String normalized) {
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

    private boolean isDirectOpportunityQuestion(String normalized) {
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

    private boolean isDirectLeadQuestion(String normalized) {
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
                || (containsAny(normalized, "来源", "XY") && containsAny(normalized, "线索", "转化情况"));
    }

    private boolean isDirectTaskQuestion(String normalized) {
        return containsAny(normalized,
                "任务总体完成情况",
                "任务类型最多",
                "哪个经销商关联任务最多",
                "哪个经销商计划中任务最多",
                "任务完成率最低",
                "任务完成情况怎么样")
                || asksTaskSubjectBreakdown(normalized);
    }

    private boolean isDirectCampaignQuestion(String normalized) {
        return containsAny(normalized,
                "市场活动一共有多少个",
                "活动最多的经销商",
                "市场活动总体商机目标完成情况",
                "活动产生商机最多的单个活动",
                "哪个经销商活动商机产出最多",
                "活动效果怎么样",
                "活动目标商机完成率为0");
    }

    private boolean asksTopSalesVolume(String normalized) {
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

    private boolean asksHighestRate(String normalized) {
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

    private boolean asksLowestRate(String normalized) {
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

    private boolean asksWinRate(String normalized) {
        return containsAny(normalized,
                "赢单率",
                "成交率",
                "成交转化率",
                "win rate",
                "conversion rate");
    }

    private boolean mentionsProductDimension(String normalized) {
        return containsAny(normalized,
                "车型",
                "车款",
                "哪款车",
                "哪种车",
                "product model",
                "model");
    }

    private boolean mentionsDealerDimension(String normalized) {
        return containsAny(normalized,
                "经销商",
                "门店",
                "店",
                "dealer",
                "store");
    }

    private boolean asksBreakdown(String normalized) {
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

    private boolean asksStatusBreakdown(String normalized) {
        return containsAny(normalized, "状态", "status") && asksBreakdown(normalized);
    }

    private boolean asksOpportunityStageBreakdown(String normalized) {
        return containsAny(normalized, "阶段", "stage") && asksBreakdown(normalized);
    }

    private boolean asksOpportunitySourceBreakdown(String normalized) {
        return containsAny(normalized, "来源", "渠道", "source")
                && (containsAny(normalized, "商机", "opportunity") || asksBreakdown(normalized) || asksWinRate(normalized));
    }

    private boolean asksTaskSubjectBreakdown(String normalized) {
        return containsAny(normalized, "任务类型", "任务类别", "subject")
                || (containsAny(normalized, "任务", "task") && containsAny(normalized, "类型", "类别"));
    }

    private int requestedTopLimit(String normalized, int defaultLimit) {
        if (containsAny(normalized, "前三", "top3", "top 3")) {
            return 3;
        }
        if (containsAny(normalized, "前五", "top5", "top 5")) {
            return 5;
        }
        return defaultLimit;
    }

    private TargetAggregateMetric aggregateTarget(List<Target> targets, String label) {
        int targetValue = targets.stream().mapToInt(Target::getAsKTarget).sum();
        int createCount = targets.stream().mapToInt(Target::getOpportunityCreateCount).sum();
        int wonCount = targets.stream().mapToInt(Target::getOpportunityWonCount).sum();
        return new TargetAggregateMetric(label, targetValue, createCount, wonCount, percentage(wonCount, targetValue));
    }

    private List<TargetAggregateMetric> aggregateTargets(List<Target> targets, Function<Target, String> classifier) {
        return targets.stream()
                .collect(Collectors.groupingBy(target -> knownOrUnknown(classifier.apply(target)), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> aggregateTarget(entry.getValue(), entry.getKey()))
                .toList();
    }

    private List<String[]> targetRows(List<TargetAggregateMetric> metrics) {
        List<String[]> rows = new ArrayList<>();
        for (TargetAggregateMetric metric : metrics) {
            rows.add(new String[]{
                    metric.label(),
                    "目标 %d，商机创建 %d，赢单 %d，达成率 %s".formatted(
                            metric.targetValue(), metric.createCount(), metric.wonCount(),
                            formatPercent(metric.achievementRate()))
            });
        }
        return rows;
    }

    private Comparator<TargetAggregateMetric> targetWonDescending() {
        return Comparator.comparingInt(TargetAggregateMetric::wonCount).reversed()
                .thenComparing(TargetAggregateMetric::label);
    }

    private Comparator<TargetAggregateMetric> targetRateDescending() {
        return Comparator.comparingDouble(TargetAggregateMetric::achievementRate).reversed()
                .thenComparing(Comparator.comparingInt(TargetAggregateMetric::wonCount).reversed())
                .thenComparing(TargetAggregateMetric::label);
    }

    private Comparator<TargetAggregateMetric> targetRateAscending() {
        return Comparator.comparingDouble(TargetAggregateMetric::achievementRate)
                .thenComparing(TargetAggregateMetric::label);
    }

    private <T> List<CountMetric> countBy(List<T> items, Function<T, String> classifier) {
        return items.stream()
                .collect(Collectors.groupingBy(item -> knownOrUnknown(classifier.apply(item)), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new CountMetric(entry.getKey(), entry.getValue()))
                .toList();
    }

    private <T> List<RateMetric> rateBy(List<T> items, Function<T, String> classifier, java.util.function.Predicate<T> positivePredicate) {
        return items.stream()
                .collect(Collectors.groupingBy(item -> knownOrUnknown(classifier.apply(item)), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<T> groupItems = entry.getValue();
                    long positive = groupItems.stream().filter(positivePredicate).count();
                    return new RateMetric(entry.getKey(), groupItems.size(), positive, percentage((int) positive, groupItems.size()));
                })
                .toList();
    }

    private Comparator<CountMetric> countDescending() {
        return Comparator.comparingLong(CountMetric::count).reversed()
                .thenComparing(CountMetric::label);
    }

    private Comparator<RateMetric> rateDescending() {
        return Comparator.comparingDouble(RateMetric::rate).reversed()
                .thenComparing(Comparator.comparingLong(RateMetric::positive).reversed())
                .thenComparing(Comparator.comparingInt(RateMetric::total).reversed())
                .thenComparing(RateMetric::label);
    }

    private Comparator<RateMetric> rateAscending() {
        return Comparator.comparingDouble(RateMetric::rate)
                .thenComparing(RateMetric::label);
    }

    private List<String[]> countRows(String labelPrefix, List<CountMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(metric -> new String[]{labelPrefix + ": " + metric.label(), String.valueOf(metric.count())})
                .toList();
    }

    private List<String[]> rateRows(String labelPrefix, List<RateMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(metric -> new String[]{
                        labelPrefix + ": " + metric.label(),
                        "%d / %d (%s)".formatted(metric.positive(), metric.total(), formatPercent(metric.rate()))
                })
                .toList();
    }

    private String joinCountMetrics(List<CountMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(metric -> metric.label() + " " + metric.count())
                .collect(Collectors.joining("，"));
    }

    private String secondLabel(List<CountMetric> metrics) {
        return metrics.size() > 1 ? metrics.get(1).label() : "-";
    }

    private long secondCount(List<CountMetric> metrics) {
        return metrics.size() > 1 ? metrics.get(1).count() : 0;
    }

    private boolean isWonOpportunity(Opportunity opportunity) {
        String stage = normalize(opportunity.getStageName());
        return stage != null && stage.contains("won");
    }

    private boolean isLostOpportunity(Opportunity opportunity) {
        String stage = normalize(opportunity.getStageName());
        return stage != null && stage.contains("lost");
    }

    private String detectLeadSourceFromMessage(String normalizedMessage) {
        return Stream.concat(cachedLeads().stream().map(Lead::getLeadSource), cachedOpportunities().stream().map(Opportunity::getLeadSource))
                .filter(Objects::nonNull)
                .filter(source -> !source.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(source -> contains(normalizedMessage, source))
                .findFirst()
                .orElse(null);
    }

    private String knownOrUnknown(String value) {
        return isKnown(value) ? value : "未知";
    }

    private boolean isKnown(String value) {
        return value != null && !value.isBlank()
                && !"未知".equals(value)
                && !"unknown".equalsIgnoreCase(value);
    }

    private CampaignDealerMetric aggregateCampaigns(List<Campaign> campaigns, String dealerName) {
        int targetOpportunityAmount = campaigns.stream().mapToInt(Campaign::getTargetOpportunityAmount).sum();
        int actualOpportunityCount = campaigns.stream().mapToInt(Campaign::getActualOpportunityCount).sum();
        int targetOrderAmount = campaigns.stream().mapToInt(Campaign::getTargetOrderAmount).sum();
        int wonOpportunityCount = campaigns.stream().mapToInt(Campaign::getWonOpportunityCount).sum();
        int leadCount = campaigns.stream().mapToInt(Campaign::getLeadCount).sum();
        return new CampaignDealerMetric(
                dealerName,
                campaigns.size(),
                targetOpportunityAmount,
                actualOpportunityCount,
                percentage(actualOpportunityCount, targetOpportunityAmount),
                targetOrderAmount,
                wonOpportunityCount,
                percentage(wonOpportunityCount, targetOrderAmount),
                leadCount
        );
    }

    private List<CampaignDealerMetric> aggregateCampaignsByDealer(List<Campaign> campaigns) {
        return campaigns.stream()
                .filter(campaign -> isKnown(campaign.getDealerName()) && !"未分配".equals(campaign.getDealerName()))
                .collect(Collectors.groupingBy(Campaign::getDealerName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> aggregateCampaigns(entry.getValue(), entry.getKey()))
                .toList();
    }

    private List<String[]> campaignDealerRows(List<CampaignDealerMetric> metrics) {
        List<String[]> rows = new ArrayList<>();
        for (CampaignDealerMetric metric : metrics) {
            rows.add(new String[]{
                    metric.dealerName(),
                    "%d 活动，目标商机 %d，实际商机 %d，完成率 %s，目标订单 %d，赢单 %d".formatted(
                            metric.campaignCount(), metric.targetOpportunityAmount(), metric.actualOpportunityCount(),
                            formatPercent(metric.opportunityAttainmentRate()), metric.targetOrderAmount(),
                            metric.wonOpportunityCount())
            });
        }
        return rows;
    }

    private List<String[]> campaignRows(List<Campaign> campaigns) {
        List<String[]> rows = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            rows.add(new String[]{
                    campaign.getCampaignName(),
                    "目标商机 %d，实际商机 %d，赢单 %d，类型 %s/%s".formatted(
                            campaign.getTargetOpportunityAmount(), campaign.getActualOpportunityCount(),
                            campaign.getWonOpportunityCount(), campaign.getEventType(), campaign.getCampaignType())
            });
        }
        return rows;
    }

    private List<Campaign> representativeCampaignsByDealer(List<Campaign> campaigns, int limit) {
        Map<String, Campaign> byDealer = new LinkedHashMap<>();
        for (Campaign campaign : campaigns) {
            String key = isKnown(campaign.getDealerName()) ? campaign.getDealerName() : campaign.getCampaignName();
            byDealer.putIfAbsent(key, campaign);
            if (byDealer.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(byDealer.values());
    }

    private String dealerCodeSortKey(String dealerName) {
        if (dealerName == null) {
            return "";
        }
        int start = -1;
        for (int i = 0; i < dealerName.length(); i++) {
            if (Character.isUpperCase(dealerName.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return dealerName;
        }
        int end = start;
        while (end < dealerName.length() && Character.isUpperCase(dealerName.charAt(end))) {
            end++;
        }
        return dealerName.substring(start, end);
    }

    private List<DealerTargetMetric> buildDealerTargetMetrics(List<Target> targets) {
        return targets.stream()
                .collect(Collectors.groupingBy(
                        target -> new DealerTargetKey(target.getDealerCode(), target.getDealerName(), target.getCity()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> {
                    DealerTargetKey key = entry.getKey();
                    List<Target> dealerTargets = entry.getValue();
                    int totalTarget = dealerTargets.stream().mapToInt(Target::getAsKTarget).sum();
                    int totalWon = dealerTargets.stream().mapToInt(Target::getOpportunityWonCount).sum();
                    return new DealerTargetMetric(
                            key.dealerCode(),
                            key.dealerName(),
                            key.city(),
                            null,
                            totalTarget,
                            totalWon,
                            percentage(totalWon, totalTarget)
                    );
                })
                .toList();
    }

    private String localizeTableLabel(String label, String language) {
        return reportRenderer.localizeTableLabel(label, language);
    }

    private String escapeHtml(String value) {
        return reportRenderer.escapeHtml(value);
    }

    private String buildBulletList(List<String> items) {
        return reportRenderer.bulletList(items);
    }

    private Map<String, String> buildQueryFilters(AnalysisScope scope, boolean includeProductModel) {
        Map<String, String> filters = new LinkedHashMap<>();
        putFilter(filters, "dealerCode", scope.dealerCode());
        putFilter(filters, "city", scope.city());
        putFilter(filters, "dealerGroupName", scope.dealerGroupName());
        if (includeProductModel) {
            putFilter(filters, "productModel", scope.productModel());
        }
        putFilter(filters, "leadSource", scope.leadSource());
        addDateFilters(filters, scope.timeRange());
        return filters;
    }

    private void putFilter(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.put(key, value);
        }
    }

    private void addDateFilters(Map<String, String> filters, AnalysisTimeRange timeRange) {
        if (timeRange == null || !timeRange.hasValue()) {
            return;
        }

        LocalDate startDate = null;
        LocalDate endDate = null;
        switch (timeRange.kind()) {
            case MONTH -> {
                startDate = LocalDate.of(timeRange.year(), timeRange.month(), 1);
                endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            }
            case QUARTER -> {
                int startMonth = ((timeRange.quarter() - 1) * 3) + 1;
                startDate = LocalDate.of(timeRange.year(), startMonth, 1);
                LocalDate endMonth = LocalDate.of(timeRange.year(), startMonth + 2, 1);
                endDate = endMonth.withDayOfMonth(endMonth.lengthOfMonth());
            }
            case YEAR -> {
                startDate = LocalDate.of(timeRange.year(), 1, 1);
                endDate = LocalDate.of(timeRange.year(), 12, 31);
            }
            case RECENT -> {
                startDate = timeRange.startDate();
                endDate = timeRange.endDate();
            }
            case NONE -> {
                return;
            }
        }

        if (startDate != null && endDate != null) {
            filters.put("startDate", startDate.toString());
            filters.put("endDate", endDate.toString());
        }
    }

    private int aggregateValue(List<Map<String, Object>> items, String fieldName, int fallback) {
        return analyticsCalculator.aggregateValue(items, fieldName, fallback);
    }

    private int aggregateValue(DataQueryResponse response, String fieldName) {
        return analyticsCalculator.aggregateValue(response, fieldName);
    }

    private String stringValue(Map<String, Object> item, String fieldName) {
        return analyticsCalculator.stringValue(item, fieldName);
    }

    private int intValue(Map<String, Object> item, String fieldName) {
        return analyticsCalculator.intValue(item, fieldName);
    }

    private int toInt(Object value) {
        return analyticsCalculator.toInt(value);
    }

    private boolean booleanValue(Map<String, Object> item, String fieldName) {
        return analyticsCalculator.booleanValue(item, fieldName);
    }

    private String buildFollowUpQuestions(List<String> questions) {
        return reportRenderer.followUpQuestions(questions);
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

    private boolean contains(String source, String candidate) {
        String normalizedSource = normalize(source);
        String normalizedCandidate = normalize(candidate);
        return normalizedSource != null && normalizedCandidate != null && normalizedSource.contains(normalizedCandidate);
    }

    private boolean matchesScope(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            AnalysisScope scope
    ) {
        if (hasScopeValue(scope.dealerCode()) || hasScopeValue(scope.dealerName())) {
            return matchesDealerIdentity(dealerCode, dealerName, scope)
                    && matchesCity(city, scope.city())
                    && matchesField(productModel, scope.productModel());
        }

        return matchesCity(city, scope.city())
                && matchesField(dealerGroupName, scope.dealerGroupName())
                && matchesField(productModel, scope.productModel());
    }

    private boolean hasScopeValue(String value) {
        return normalize(value) != null;
    }

    private boolean matchesDealerIdentity(String dealerCode, String dealerName, AnalysisScope scope) {
        return (hasScopeValue(scope.dealerCode()) && matchesField(dealerCode, scope.dealerCode()))
                || (hasScopeValue(scope.dealerName()) && matchesField(dealerName, scope.dealerName()));
    }

    private boolean matchesCity(String actual, String expected) {
        if (matchesField(actual, expected)) {
            return true;
        }

        String normalizedExpected = normalize(expected);
        String normalizedActual = normalize(actual);
        if (normalizedExpected == null) {
            return true;
        }
        if (normalizedActual == null) {
            return false;
        }

        String expectedZh = CITY_ZH.get(expected);
        return expectedZh != null && normalizedActual.contains(normalize(expectedZh));
    }

    private boolean matchesField(String actual, String expected) {
        String normalizedExpected = normalize(expected);
        if (normalizedExpected == null) {
            return true;
        }
        String normalizedActual = normalize(actual);
        return normalizedActual != null && normalizedActual.equals(normalizedExpected);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String removeParenthesizedSuffix(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int asciiOpen = trimmed.lastIndexOf('(');
        int asciiClose = trimmed.lastIndexOf(')');
        if (asciiOpen >= 0 && asciiClose > asciiOpen) {
            return trimmed.substring(0, asciiOpen).trim();
        }
        int fullWidthOpen = trimmed.lastIndexOf('（');
        int fullWidthClose = trimmed.lastIndexOf('）');
        if (fullWidthOpen >= 0 && fullWidthClose > fullWidthOpen) {
            return trimmed.substring(0, fullWidthOpen).trim();
        }
        return trimmed;
    }

    private double percentage(int numerator, int denominator) {
        return analyticsCalculator.percentage(numerator, denominator);
    }

    private String formatPercent(double value) {
        return analyticsCalculator.formatPercent(value);
    }

    private String describeDeltaToAverage(double deltaToAverage) {
        return analyticsCalculator.describeDeltaToAverage(deltaToAverage);
    }


    private enum DataQualityState {
        NO_DATA,
        DENOMINATOR_ZERO,
        ALL_ZERO_SIGNAL,
        INSUFFICIENT_SAMPLE,
        NORMAL
    }

    private enum ChartSuppressionReason {
        NONE,
        NO_DATA,
        DENOMINATOR_ZERO,
        ALL_ZERO_SIGNAL,
        INSUFFICIENT_SAMPLE,
        TOO_FEW_POINTS,
        EMPTY_LABELS
    }

    private record DataQualityContext(
            DataQualityState state,
            String scenarioLabel,
            String primaryMetricLabel,
            int observedRows,
            int validComparableUnits,
            int requiredComparableUnits,
            int primaryNumerator,
            int primaryDenominator,
            int excludedUnits,
            ChartSuppressionReason chartSuppressionReason
    ) {
        boolean suppressChart() {
            return chartSuppressionReason != ChartSuppressionReason.NONE;
        }
    }

    private record DataQualityBusinessTerms(
            String metricLabel,
            String comparableLabel,
            String comparableUnit,
            String numeratorLabel,
            String denominatorLabel,
            String invalidComparableLabel
    ) {
    }

    private record ScenarioResult(String reply, DataQualityContext quality, List<CalcStep> traceSteps) {
        ScenarioResult(String reply, DataQualityContext quality) {
            this(reply, quality, List.of());
        }

        ScenarioResult withReply(String nextReply) {
            return new ScenarioResult(nextReply, quality, traceSteps);
        }
    }

    private record SummaryContext(
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

    private final class AnalysisDataCache {

        private List<Dealer> dealers;
        private List<Target> targets;
        private List<Opportunity> opportunities;
        private List<Campaign> campaigns;
        private List<Task> tasks;
        private List<Lead> leads;

        private List<Dealer> dealers() {
            if (dealers == null) {
                dealers = dealerRepository.findAll();
            }
            return dealers;
        }

        private List<Target> targets() {
            if (targets == null) {
                targets = targetRepository.findAll();
            }
            return targets;
        }

        private List<Opportunity> opportunities() {
            if (opportunities == null) {
                opportunities = opportunityRepository.findAll();
            }
            return opportunities;
        }

        private List<Campaign> campaigns() {
            if (campaigns == null) {
                campaigns = campaignRepository.findAll();
            }
            return campaigns;
        }

        private List<Task> tasks() {
            if (tasks == null) {
                tasks = taskRepository.findAll();
            }
            return tasks;
        }

        private List<Lead> leads() {
            if (leads == null) {
                leads = leadRepository.findAll();
            }
            return leads;
        }
    }

    private enum AnalysisTopic {
        TARGET_ACHIEVEMENT("\u76ee\u6807\u8fbe\u6210\u5206\u6790", "target achievement"),
        OPPORTUNITY_FUNNEL("\u5546\u673a\u6f0f\u6597\u5206\u6790", "opportunity funnel"),
        SALES_FOLLOW_UP("\u9500\u552e\u8ddf\u8fdb\u5206\u6790", "sales follow-up"),
        CAMPAIGN_PERFORMANCE("\u6d3b\u52a8\u6548\u679c\u5206\u6790", "campaign performance"),
        LEAD_SOURCE("\u7ebf\u7d22\u6765\u6e90\u5206\u6790", "lead source analysis"),
        DEALER_BENCHMARK("\u95e8\u5e97\u5bf9\u6807\u5206\u6790", "dealer benchmark"),
        DEALER_BUSINESS_ACTIVITY("\u95e8\u5e97\u7ecf\u8425\u6d3b\u8dc3\u5ea6", "dealer business activity"),
        DATA_OVERVIEW("\u6570\u636e\u6982\u51b5", "data overview");

        private final String zhLabel;
        private final String enLabel;

        AnalysisTopic(String zhLabel, String enLabel) {
            this.zhLabel = zhLabel;
            this.enLabel = enLabel;
        }

        String label(String language) {
            return "zh".equals(language) ? zhLabel : enLabel;
        }
    }

    private enum SalesFollowUpFocus {
        BACKLOG_RISK,
        HIGH_ACTIVITY
    }

    private record AnalysisScope(
            AnalysisTimeRange timeRange,
            String city,
            String dealerCode,
            String dealerName,
            String dealerGroupName,
            String productModel,
            String leadSource
    ) {
        String summary(String language) {
            List<String> parts = new ArrayList<>();

            if (timeRange != null && timeRange.hasValue()) {
                parts.add(timeRange.label(language));
            }
            if (city != null) {
                parts.add("zh".equals(language) ? CITY_ZH.getOrDefault(city, city) : city);
            }
            if (dealerGroupName != null) {
                parts.add(dealerGroupName);
            }
            if (dealerName != null) {
                parts.add(dealerName);
            }
            if (productModel != null) {
                parts.add(productModel);
            }
            if (leadSource != null) {
                parts.add(leadSource);
            }

            if (parts.isEmpty()) {
                return "zh".equals(language) ? "\u5f53\u524d\u6837\u4f8b\u6570\u636e\u8303\u56f4" : "the current sample dataset";
            }

            return String.join(" / ", parts);
        }
    }

    private record AnalysisTimeRange(
            Kind kind,
            Integer year,
            Integer month,
            Integer quarter,
            LocalDate startDate,
            LocalDate endDate
    ) {
        static AnalysisTimeRange none() {
            return new AnalysisTimeRange(Kind.NONE, null, null, null, null, null);
        }

        static AnalysisTimeRange month(int year, int month) {
            return new AnalysisTimeRange(Kind.MONTH, year, month, null, null, null);
        }

        static AnalysisTimeRange quarter(int year, int quarter) {
            return new AnalysisTimeRange(Kind.QUARTER, year, null, quarter, null, null);
        }

        static AnalysisTimeRange year(int year) {
            return new AnalysisTimeRange(Kind.YEAR, year, null, null, null, null);
        }

        static AnalysisTimeRange recent(LocalDate startDate, LocalDate endDate) {
            return new AnalysisTimeRange(Kind.RECENT, null, null, null, startDate, endDate);
        }

        boolean hasValue() {
            return kind != Kind.NONE;
        }

        boolean matchesTarget(Integer targetYear, Integer targetMonth) {
            if (kind == Kind.NONE) {
                return true;
            }
            if (targetYear == null || targetMonth == null) {
                return false;
            }

            return switch (kind) {
                case MONTH -> Objects.equals(targetYear, year) && Objects.equals(targetMonth, month);
                case QUARTER -> Objects.equals(targetYear, year)
                        && targetMonth >= quarterStartMonth()
                        && targetMonth <= quarterEndMonth();
                case YEAR -> Objects.equals(targetYear, year);
                case RECENT -> targetYear == endDate.getYear() && targetMonth == endDate.getMonthValue();
                case NONE -> true;
            };
        }

        boolean matchesDate(LocalDate value) {
            if (kind == Kind.NONE) {
                return true;
            }
            if (value == null) {
                return false;
            }

            return switch (kind) {
                case MONTH -> value.getYear() == year && value.getMonthValue() == month;
                case QUARTER -> value.getYear() == year
                        && value.getMonthValue() >= quarterStartMonth()
                        && value.getMonthValue() <= quarterEndMonth();
                case YEAR -> value.getYear() == year;
                case RECENT -> !value.isBefore(startDate) && !value.isAfter(endDate);
                case NONE -> true;
            };
        }

        String label(String language) {
            return switch (kind) {
                case MONTH -> "zh".equals(language)
                        ? "%d年%d月".formatted(year, month)
                        : "%s %d".formatted(Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH), year);
                case QUARTER -> "zh".equals(language)
                        ? "%d年Q%d".formatted(year, quarter)
                        : "Q%d %d".formatted(quarter, year);
                case YEAR -> "zh".equals(language) ? "%d年".formatted(year) : String.valueOf(year);
                case RECENT -> "zh".equals(language) ? "近30天" : "last 30 days";
                case NONE -> "";
            };
        }

        private int quarterStartMonth() {
            return ((quarter - 1) * 3) + 1;
        }

        private int quarterEndMonth() {
            return quarterStartMonth() + 2;
        }

        private enum Kind {
            NONE,
            MONTH,
            QUARTER,
            YEAR,
            RECENT
        }
    }

    private record DealerTargetMetric(
            String dealerCode,
            String dealerName,
            String city,
            String productModel,
            int targetValue,
            int wonCount,
            double achievementRate
    ) {
    }

    private record DealerTargetKey(
            String dealerCode,
            String dealerName,
            String city
    ) {
    }

    private record TargetAggregateMetric(
            String label,
            int targetValue,
            int createCount,
            int wonCount,
            double achievementRate
    ) {
    }

    private record CountMetric(
            String label,
            long count
    ) {
    }

    private record RateMetric(
            String label,
            int total,
            long positive,
            double rate
    ) {
    }

    private record TaskBacklogMetric(
            String dealerName,
            int totalCount,
            long backlogCount,
            double backlogRate
    ) {
    }

    private record TaskActivityMetric(
            String dealerName,
            int totalCount,
            long completedCount,
            double completionRate
    ) {
    }

    private record DealerActivityScore(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            int targetMonths,
            int opportunityCreateMonths,
            int opportunityWonMonths,
            int dataMonths,
            int totalScore,
            boolean isNewStore,
            int observedMonths
    ) {
        String tier() {
            if (totalScore >= 40) return "非常活跃";
            if (totalScore >= 30) return "活跃";
            if (totalScore >= 20) return "一般";
            if (totalScore >= 10) return "低活跃";
            return "休眠";
        }

        String tierEn() {
            if (totalScore >= 40) return "Very Active";
            if (totalScore >= 30) return "Active";
            if (totalScore >= 20) return "Moderate";
            if (totalScore >= 10) return "Low Activity";
            return "Dormant";
        }
    }

    private record CampaignMetric(
            String campaignId,
            String dealerName,
            String campaignType,
            int actualOpportunityCount,
            int totalNewCustomerTarget,
            double attainmentRate
    ) {
    }

    private record LeadSourceMetric(
            String source,
            int leadCount,
            long convertedCount,
            double conversionRate
    ) {
    }

    private record CampaignDealerMetric(
            String dealerName,
            int campaignCount,
            int targetOpportunityAmount,
            int actualOpportunityCount,
            double opportunityAttainmentRate,
            int targetOrderAmount,
            int wonOpportunityCount,
            double orderAttainmentRate,
            int leadCount
    ) {
    }

    private enum ChartEntityType {
        DEALER,
        CAMPAIGN,
        STAGE,
        SOURCE,
        GENERIC
    }

    private ReportRenderer.ChartEntityType rendererChartEntityType(ChartEntityType entityType) {
        return ReportRenderer.ChartEntityType.valueOf(entityType.name());
    }

    private <T> List<T> bottomTopDistinct(List<T> sorted, int showLimit) {
        int show = Math.min(showLimit, sorted.size());
        return Stream.concat(
                sorted.subList(0, show).stream(),
                sorted.subList(Math.max(0, sorted.size() - show), sorted.size()).stream()
        ).distinct().toList();
    }

    private String buildMermaidXyChart(
            String title,
            String xLabel,
            String yLabel,
            List<String> labels,
            List<Double> values,
            Double averageLine
    ) {
        return reportRenderer.chartJsonBar(title, chartMetricName(yLabel), chartMetricType(yLabel), labels, values, averageLine);
    }

    private String buildMermaidXyChart(
            String title,
            String xLabel,
            String yLabel,
            ChartEntityType entityType,
            List<String> labels,
            List<Double> values,
            Double averageLine
    ) {
        return reportRenderer.chartJsonBar(
                title,
                chartMetricName(yLabel),
                chartMetricType(yLabel),
                rendererChartEntityType(entityType),
                labels,
                values,
                averageLine
        );
    }

    private String buildMermaidPie(String title, Map<String, Double> slices) {
        return reportRenderer.chartJsonPie(title, slices);
    }

    private String buildMermaidPie(String title, ChartEntityType entityType, Map<String, Double> slices) {
        return reportRenderer.chartJsonPie(title, rendererChartEntityType(entityType), slices);
    }

    private String chartMetricName(String yLabel) {
        return String.valueOf(yLabel == null ? "" : yLabel)
                .replaceAll("\\s*\\([^)]*\\)", "")
                .trim();
    }

    private String chartMetricType(String yLabel) {
        String label = String.valueOf(yLabel == null ? "" : yLabel).toLowerCase(Locale.ROOT);
        return label.contains("%") || label.contains("rate") || label.contains("\u7387")
                ? "percentage"
                : "absolute";
    }

    private String buildFallbackBars(List<String> labels, List<Double> values, double maxValue) {
        return reportRenderer.fallbackBars(labels, values, maxValue);
    }

    private String buildFallbackBars(ChartEntityType entityType, List<String> labels, List<Double> values, double maxValue) {
        return reportRenderer.fallbackBars(rendererChartEntityType(entityType), labels, values, maxValue);
    }

    private String buildEnrichedReply(
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

        // Section 1: Conclusion
        body.append(isZh ? "## 核心结论\n\n" : "## Conclusion\n\n");
        body.append(escapeHtml(conclusion)).append("\n");

        // Section 2: Data Support
        body.append(isZh ? "## 数据支撑\n\n" : "## Data Support\n\n");
        body.append(buildHtmlTable(dataRows, language)).append("\n");
        if (mermaid != null && !mermaid.isBlank()) {
            body.append("\n").append(mermaid).append("\n");
        }
        if (fallback != null && !fallback.isBlank()) {
            body.append("\n").append(fallback).append("\n");
        }

        // Section 3: Analysis (merged section with sub-headers)
        body.append(isZh ? "## 经营分析\n\n" : "## Short Analysis\n\n");

        if (!attributions.isEmpty()) {
            body.append(isZh ? "**数据归因：**\n\n" : "**Data Attribution:**\n\n");
            for (String attr : attributions) {
                body.append("- ").append(escapeHtml(attr)).append("\n");
            }
            body.append("\n");
        }
        if (!recommendations.isEmpty()) {
            body.append(isZh ? "**可执行建议：**\n\n" : "**Recommendations:**\n\n");
            for (String rec : recommendations) {
                body.append("- ").append(escapeHtml(rec)).append("\n");
            }
            body.append("\n");
        }

        // Section 4: Problem Diagnosis
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

        // Section 5: Improvement Suggestions
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

        // Section 6: Follow-up Questions
        body.append(isZh ? "追问：\n\n" : "FOLLOW_UP_QUESTIONS:\n\n");
        for (int i = 0; i < followUps.size(); i++) {
            body.append(i + 1).append(". ").append(escapeHtml(followUps.get(i))).append("\n");
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

    private boolean matchesScope(Target t, AnalysisScope scope) {
        if (scope.city() != null && !scope.city().equals(t.getCity())) return false;
        if (scope.dealerCode() != null && !scope.dealerCode().equals(t.getDealerCode())) return false;
        if (scope.dealerGroupName() != null && !scope.dealerGroupName().equals(t.getDealerGroupName())) return false;
        if (scope.productModel() != null && !scope.productModel().equals(t.getProductModel())) return false;
        return true;
    }

    private boolean scopeMatchesDealer(Dealer d, AnalysisScope scope) {
        if (scope.city() != null && !scope.city().equals(d.getCity())) return false;
        if (scope.dealerCode() != null && !scope.dealerCode().equals(d.getDealerCode())) return false;
        if (scope.dealerGroupName() != null && !scope.dealerGroupName().equals(d.getDealerGroupName())) return false;
        return true;
    }

    private boolean isWithinWindow(int year, int month,
                                   int startYear, int startMonth,
                                   int endYear, int endMonth) {
        int targetYm = year * 100 + month;
        int startYm = startYear * 100 + startMonth;
        int endYm = endYear * 100 + endMonth;
        return targetYm >= startYm && targetYm <= endYm;
    }

    private ScenarioResult buildDealerActivityResult(
            AnalysisScope scope, String language, List<CalcStep> traceSteps,
            List<DealerActivityScore> scores,
            int startYear, int startMonth, int endYear, int endMonth,
            boolean includeDormant, int totalTargetRows) {

        boolean isZh = "zh".equals(language);

        // Quality check
        if (scores.isEmpty()) {
            String label = isZh ? "门店经营活跃度" : "Dealer Business Activity";
            return noDataResult(language, scope, label);
        }

        int positiveScoreCount = (int) scores.stream().filter(s -> s.totalScore() > 0).count();
        DataQualityContext quality = classifyCountQuality(
                isZh ? "门店经营活跃度" : "Dealer Business Activity",
                isZh ? "活跃度总分" : "activity total score",
                scores.size(), Math.max(scores.size(), 2), 1,
                positiveScoreCount, scores.size(), false);
        if (quality.state() != DataQualityState.NORMAL) {
            return lowConfidenceResult(language, scope, quality, traceSteps);
        }

        // Tier distribution
        Map<String, Long> tierCounts = new LinkedHashMap<>();
        String[] zhTiers = {"非常活跃", "活跃", "一般", "低活跃", "休眠"};
        String[] enTiers = {"Very Active", "Active", "Moderate", "Low Activity", "Dormant"};
        for (int i = 0; i < zhTiers.length; i++) {
            String key = isZh ? zhTiers[i] : enTiers[i];
            tierCounts.put(key, 0L);
        }
        for (DealerActivityScore s : scores) {
            String t = isZh ? s.tier() : s.tierEn();
            tierCounts.merge(t, 1L, Long::sum);
        }

        // Top 1
        DealerActivityScore top1 = scores.get(0);

        // Conclusion
        String conclusion;
        if (isZh) {
            List<String> tierParts = new ArrayList<>();
            for (Map.Entry<String, Long> e : tierCounts.entrySet()) {
                if (e.getValue() > 0) tierParts.add(e.getKey() + " " + e.getValue() + " 家");
            }
            conclusion = "## 核心结论\n\n"
                    + String.format("- 共 **%d** 个门店，其中：%s\n", scores.size(), String.join("，", tierParts))
                    + String.format("- 活跃度最高：**%s**（%d 分），%d 个月有目标，%d 个月有商机，%d 个月有成交\n",
                            top1.dealerName(), top1.totalScore(),
                            top1.targetMonths(), top1.opportunityCreateMonths(), top1.opportunityWonMonths());
            if (includeDormant) {
                long dormant = tierCounts.getOrDefault("休眠", 0L);
                if (dormant > 0) {
                    conclusion += String.format("- ⚠️ %d 家休眠门店需关注，建议核实经营状态\n", dormant);
                }
            }
            for (DealerActivityScore s : scores) {
                if (s.isNewStore()) {
                    conclusion += String.format("- **%s** 观察期不足 12 个月（仅 %d 个月有数据），新店结果偏保守\n",
                            s.dealerName(), s.observedMonths());
                }
            }
        } else {
            List<String> tierParts = new ArrayList<>();
            for (Map.Entry<String, Long> e : tierCounts.entrySet()) {
                if (e.getValue() > 0) tierParts.add(e.getKey() + ": " + e.getValue());
            }
            conclusion = "## Conclusion\n\n"
                    + String.format("- **%d** dealers total. %s\n", scores.size(), String.join(", ", tierParts))
                    + String.format("- Highest activity: **%s** (%d pts), %d mo target, %d mo opportunities, %d mo won\n",
                            top1.dealerName(), top1.totalScore(),
                            top1.targetMonths(), top1.opportunityCreateMonths(), top1.opportunityWonMonths());
            if (includeDormant) {
                long dormant = tierCounts.getOrDefault("Dormant", 0L);
                if (dormant > 0) {
                    conclusion += String.format("- ⚠️ %d dormant dealers require attention\n", dormant);
                }
            }
            for (DealerActivityScore s : scores) {
                if (s.isNewStore()) {
                    conclusion += String.format("- **%s** only %d months observed — new store, results conservative\n",
                            s.dealerName(), s.observedMonths());
                }
            }
        }

        // Data table (List<String[]> dataRows for buildEnrichedReply)
        String windowLabelZh = String.format("%d年%d月 — %d年%d月", startYear, startMonth, endYear, endMonth);
        String windowLabelEn = String.format("%d-%02d — %d-%02d", startYear, startMonth, endYear, endMonth);

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{
                isZh ? "统计窗口" : "Window",
                isZh ? windowLabelZh : windowLabelEn});
        dataRows.add(new String[]{
                isZh ? "有效 Target 记录数" : "Valid Target rows",
                String.valueOf(totalTargetRows)});
        dataRows.add(new String[]{
                isZh ? "覆盖门店数" : "Dealers covered",
                String.valueOf(scores.size())});

        StringBuilder tableHtml = new StringBuilder();
        String header = isZh
                ? "<tr><th>门店代码</th><th>门店名称</th><th>有目标(月)</th><th>有商机(月)</th><th>有成交(月)</th><th>有数据(月)</th><th>总分</th><th>等级</th></tr>"
                : "<tr><th>Code</th><th>Name</th><th>Target(mo)</th><th>Oppt Created(mo)</th><th>Oppt Won(mo)</th><th>Data(mo)</th><th>Score</th><th>Tier</th></tr>";
        tableHtml.append("<table>\n").append(header).append("\n");
        for (DealerActivityScore s : scores) {
            String tierLabel = isZh ? s.tier() : s.tierEn();
            String note = "";
            if (s.isNewStore()) {
                note = isZh ? " (新店)" : " (new)";
            }
            tableHtml.append(String.format(
                    "<tr><td>%s</td><td>%s</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%s%s</td></tr>%n",
                    s.dealerCode(), s.dealerName(), s.targetMonths(), s.opportunityCreateMonths(),
                    s.opportunityWonMonths(), s.dataMonths(), s.totalScore(), tierLabel, note));
        }
        tableHtml.append("</table>\n");

        // Mermaid chart (Top 10 via existing buildMermaidXyChart)
        int showN = Math.min(10, scores.size());
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartValues = new ArrayList<>();
        for (int i = 0; i < showN; i++) {
            chartLabels.add(scores.get(i).dealerName());
            chartValues.add((double) scores.get(i).totalScore());
        }
        String chartTitle = isZh
                ? scope.summary(language) + " 门店经营活跃度 Top " + showN
                : scope.summary(language) + " Dealer Business Activity Top " + showN;
        String mermaid = buildMermaidXyChart(
                chartTitle,
                isZh ? "门店" : "Dealer",
                isZh ? "总分 (满分48)" : "Score (max 48)",
                ChartEntityType.DEALER,
                chartLabels, chartValues, null);
        String fallback = buildFallbackBars(ChartEntityType.DEALER, chartLabels, chartValues, 48);

        // Recommendations
        List<String> recommendations = new ArrayList<>();
        long dormantCount = scores.stream().filter(s -> s.totalScore() < 10).count();
        long lowCount = scores.stream().filter(s -> s.totalScore() >= 10 && s.totalScore() < 20).count();
        if (dormantCount > 0) {
            recommendations.add(isZh
                    ? String.format("休眠门店（%d 家）：核实经营状态，确认是否已停业或数据中断，安排跟进", dormantCount)
                    : String.format("Dormant dealers (%d): verify operational status, check for data gaps or closures", dormantCount));
        }
        if (lowCount > 0) {
            recommendations.add(isZh
                    ? String.format("低活跃门店（%d 家）：分析短板维度，制定针对性提升计划", lowCount)
                    : String.format("Low-activity dealers (%d): identify weak dimensions and create improvement plans", lowCount));
        }
        if (dormantCount == 0 && lowCount == 0) {
            recommendations.add(isZh
                    ? "整体活跃度良好，建议关注个别维度偏低的门店，保持稳定运营"
                    : "Overall activity is healthy. Monitor dealers with specific weak dimensions.");
        }

        // Attributions
        List<String> attributions = List.of(
                isZh
                        ? "数据来源：dealer_targets 表，含 AaKTarget__c / OpportunityCreateCount__c / OpportunityWonCount__c"
                        : "Data source: dealer_targets table, fields AaKTarget__c / OpportunityCreateCount__c / OpportunityWonCount__c");

        // Follow-ups
        List<String> followUps = new ArrayList<>();
        if (isZh) {
            followUps.add("这些休眠门店最近一次有数据是什么时候？");
            followUps.add("低活跃门店在哪个维度最弱？");
            followUps.add("非常活跃门店有哪些共同特征？");
        } else {
            followUps.add("When was the last time dormant dealers had data?");
            followUps.add("Which dimension is weakest for low-activity dealers?");
            followUps.add("What common traits do very active dealers share?");
        }

        SummaryContext activitySummary = new SummaryContext(
                isZh ? "门店经营活跃度" : "Dealer Business Activity",
                scores.size(),
                isZh ? "最高活跃度" : "Highest Activity Score",
                top1.totalScore() + (isZh ? " 分" : " pts"),
                "—",
                top1.dealerName(), top1.totalScore() + (isZh ? " 分" : " pts"),
                dormantCount > 0 ? (isZh ? "休眠门店" : "Dormant dealers") : null,
                dormantCount > 0 ? String.valueOf(dormantCount) + (isZh ? " 家" : "") : null);
        String fullReply = buildEnrichedReply(language, conclusion, dataRows, activitySummary, mermaid, fallback,
                attributions, recommendations, followUps);

        // Insert dealer detail table between data support and analysis sections
        String analysisMarker = isZh ? "## 经营分析" : "## Short Analysis";
        String detailSection = "\n" + tableHtml.toString() + "\n";
        fullReply = fullReply.replace(analysisMarker, detailSection + analysisMarker);

        return new ScenarioResult(fullReply, quality, traceSteps);
    }

    private static Map<String, Object> filterAuditMeta(AnalysisScope scope, String language, int inputCount, int outputCount) {
        Map<String, Object> meta = auditMap(
                "inputCount", inputCount,
                "outputCount", outputCount,
                "scopeSummary", scope.summary(language),
                "city", scope.city(),
                "dealerCode", scope.dealerCode(),
                "dealerName", scope.dealerName(),
                "dealerGroupName", scope.dealerGroupName(),
                "productModel", scope.productModel()
        );
        AnalysisTimeRange timeRange = scope.timeRange();
        if (timeRange != null && timeRange.hasValue()) {
            meta.put("timeRangeKind", timeRange.kind().name());
            if (timeRange.year() != null) {
                meta.put("year", timeRange.year());
            }
            if (timeRange.month() != null) {
                meta.put("month", timeRange.month());
            }
            if (timeRange.quarter() != null) {
                meta.put("quarter", timeRange.quarter());
            }
            if (timeRange.startDate() != null) {
                meta.put("startDate", timeRange.startDate().toString());
            }
            if (timeRange.endDate() != null) {
                meta.put("endDate", timeRange.endDate().toString());
            }
        }
        return meta;
    }

    private static Map<String, Object> targetMetricRow(DealerTargetMetric metric) {
        return auditMap(
                "dealerCode", metric.dealerCode(),
                "dealerName", metric.dealerName(),
                "city", metric.city(),
                "productModel", metric.productModel(),
                "wonCount", metric.wonCount(),
                "targetValue", metric.targetValue(),
                "achievementRate", metric.achievementRate()
        );
    }

    private static List<Map<String, Object>> targetMetricRows(List<DealerTargetMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(RuleBasedAnalyticsService::targetMetricRow)
                .toList();
    }

    private static Map<String, Object> campaignMetricRow(CampaignMetric metric) {
        return auditMap(
                "campaignId", metric.campaignId(),
                "dealerName", metric.dealerName(),
                "campaignType", metric.campaignType(),
                "actualOpportunityCount", metric.actualOpportunityCount(),
                "totalNewCustomerTarget", metric.totalNewCustomerTarget(),
                "attainmentRate", metric.attainmentRate()
        );
    }

    private static List<Map<String, Object>> campaignMetricRows(List<CampaignMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(RuleBasedAnalyticsService::campaignMetricRow)
                .toList();
    }

    private static Map<String, Object> leadSourceMetricRow(LeadSourceMetric metric) {
        return auditMap(
                "source", metric.source(),
                "leadCount", metric.leadCount(),
                "convertedCount", metric.convertedCount(),
                "conversionRate", metric.conversionRate()
        );
    }

    private static List<Map<String, Object>> leadSourceMetricRows(List<LeadSourceMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(RuleBasedAnalyticsService::leadSourceMetricRow)
                .toList();
    }

    private static List<Map<String, Object>> opportunityRows(List<Opportunity> opportunities, int limit) {
        return opportunities.stream()
                .limit(limit)
                .map(opportunity -> auditMap(
                        "opportunityId", opportunity.getOpportunityId(),
                        "dealerCode", opportunity.getDealerCode(),
                        "dealerName", opportunity.getDealerName(),
                        "city", opportunity.getCity(),
                        "productModel", opportunity.getProductModel(),
                        "stageName", opportunity.getStageName(),
                        "leadSource", opportunity.getLeadSource(),
                        "createdDate", opportunity.getCreatedDate(),
                        "probability", opportunity.getProbability()
                ))
                .toList();
    }

    private static List<Map<String, Object>> taskRows(List<Task> tasks, int limit) {
        return tasks.stream()
                .limit(limit)
                .map(task -> auditMap(
                        "taskId", task.getTaskId(),
                        "dealerCode", task.getDealerCode(),
                        "dealerName", task.getDealerName(),
                        "city", task.getCity(),
                        "opportunityId", task.getOpportunityId(),
                        "status", task.getStatus(),
                        "createdDate", task.getCreatedDate()
                ))
                .toList();
    }

    private static Map<String, Object> taskBacklogRow(TaskBacklogMetric metric) {
        return auditMap(
                "dealerName", metric.dealerName(),
                "totalTaskCount", metric.totalCount(),
                "openTaskCount", metric.backlogCount(),
                "backlogRate", metric.backlogRate()
        );
    }

    private static List<Map<String, Object>> taskBacklogRows(List<TaskBacklogMetric> metrics, int limit) {
        return metrics.stream()
                .limit(limit)
                .map(RuleBasedAnalyticsService::taskBacklogRow)
                .toList();
    }

    private static Map<String, Object> auditMap(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value != null) {
                values.put(String.valueOf(entries[index]), value);
            }
        }
        return values;
    }

    private static void emitStep(Consumer<StepEvent> onStep, StepEvent event) {
        if (onStep != null) {
            onStep.accept(event);
        }
    }

    private static boolean isZh(String language) {
        return "zh".equals(language);
    }
}
