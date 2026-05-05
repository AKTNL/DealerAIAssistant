package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.PromptFactory;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedAnalyticsService {

    private static final Map<String, List<String>> CITY_ALIASES = Map.of(
            "Beijing", List.of("beijing", "北京"),
            "Shanghai", List.of("shanghai", "上海"),
            "Hangzhou", List.of("hangzhou", "杭州"),
            "Guangzhou", List.of("guangzhou", "广州"),
            "Chengdu", List.of("chengdu", "成都")
    );

    private static final Map<String, String> CITY_ZH = Map.of(
            "Beijing", "北京",
            "Shanghai", "上海",
            "Hangzhou", "杭州",
            "Guangzhou", "广州",
            "Chengdu", "成都"
    );

    private final PromptFactory promptFactory;
    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;

    public RuleBasedAnalyticsService(
            PromptFactory promptFactory,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository
    ) {
        this.promptFactory = promptFactory;
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
    }

    public AnalyticsResponse analyze(String message, String language) {
        AnalysisTopic topic = detectTopic(message);
        AnalysisScope scope = detectScope(message);
        String topicLabel = topic.label(language);
        String scopeSummary = scope.summary(language);

        List<String> progressMessages = buildProgressMessages(language, topicLabel);
        String visibleThinking = promptFactory.buildVisibleThinking(language, topicLabel, scopeSummary);
        String reply = switch (topic) {
            case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language);
            case OPPORTUNITY_FUNNEL -> analyzeOpportunityFunnel(scope, language);
            case SALES_FOLLOW_UP -> analyzeSalesFollowUp(scope, language);
            case CAMPAIGN_PERFORMANCE -> analyzeCampaignPerformance(scope, language);
            case LEAD_SOURCE -> analyzeLeadSource(scope, language);
            case DEALER_BENCHMARK -> analyzeDealerBenchmark(scope, language);
        };

        return new AnalyticsResponse(reply, progressMessages, visibleThinking);
    }

    private AnalysisTopic detectTopic(String message) {
        String normalized = normalize(message);

        if (containsAny(normalized, "活动", "campaign", "marketing", "event")) {
            return AnalysisTopic.CAMPAIGN_PERFORMANCE;
        }
        if (containsAny(normalized, "跟进", "任务", "活跃度", "task", "follow-up", "follow up")) {
            return AnalysisTopic.SALES_FOLLOW_UP;
        }
        if (containsAny(normalized, "线索", "来源", "流量", "lead", "source", "organic", "trend")) {
            return AnalysisTopic.LEAD_SOURCE;
        }
        if (containsAny(normalized, "对标", "比较", "表现最好", "outperform", "benchmark", "compare", "best")) {
            return AnalysisTopic.DEALER_BENCHMARK;
        }
        if (containsAny(normalized, "目标", "达成", "完成率", "achievement", "target")) {
            return AnalysisTopic.TARGET_ACHIEVEMENT;
        }
        if (containsAny(normalized, "漏斗", "商机", "转化", "opportunity", "funnel", "conversion", "stage")) {
            return AnalysisTopic.OPPORTUNITY_FUNNEL;
        }
        return AnalysisTopic.DEALER_BENCHMARK;
    }

    private AnalysisScope detectScope(String message) {
        String normalizedMessage = normalize(message);
        List<Dealer> dealers = dealerRepository.findAll();

        Dealer matchedDealer = dealers.stream()
                .filter(dealer -> contains(normalizedMessage, dealer.getDealerCode())
                        || contains(normalizedMessage, dealer.getDealerName()))
                .findFirst()
                .orElse(null);

        String city = CITY_ALIASES.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalizedMessage::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(matchedDealer != null ? matchedDealer.getCity() : null);

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
        productModels.addAll(targetRepository.findAll().stream().map(Target::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(opportunityRepository.findAll().stream().map(Opportunity::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(campaignRepository.findAll().stream().map(Campaign::getProductModel).collect(Collectors.toSet()));
        productModels.addAll(leadRepository.findAll().stream().map(Lead::getProductModel).collect(Collectors.toSet()));

        String productModel = productModels.stream()
                .filter(Objects::nonNull)
                .filter(model -> contains(normalizedMessage, model))
                .findFirst()
                .orElse(null);

        return new AnalysisScope(city, dealerCode, dealerName, dealerGroupName, productModel);
    }

    private String analyzeTargetAchievement(AnalysisScope scope, String language) {
        List<Target> filtered = targetRepository.findAll().stream()
                .filter(target -> matchesScope(target.getDealerCode(), target.getDealerName(), target.getCity(),
                        target.getDealerGroupName(), target.getProductModel(), scope))
                .toList();

        if (filtered.isEmpty()) {
            return buildNoDataReply(language, scope, "target achievement");
        }

        List<DealerTargetMetric> metrics = filtered.stream()
                .map(target -> new DealerTargetMetric(
                        target.getDealerCode(),
                        target.getDealerName(),
                        target.getCity(),
                        target.getProductModel(),
                        target.getAsKTarget(),
                        target.getOpportunityWonCount(),
                        percentage(target.getOpportunityWonCount(), target.getAsKTarget())
                ))
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
                .toList();

        DealerTargetMetric lowest = metrics.getFirst();
        DealerTargetMetric highest = metrics.getLast();
        double averageRate = metrics.stream().mapToDouble(DealerTargetMetric::achievementRate).average().orElse(0.0);

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内，目标达成率最低的是 **%s**，当前达成率为 **%s**；整体平均达成率为 **%s**。

                    ## 数据支撑
                    - 覆盖门店数：%d
                    - 最低达成率：%s（%d / %d）
                    - 最高达成率：%s（%d / %d）
                    - 平均达成率：%s

                    ## 分析
                    - 低位门店与高位门店之间的达成率差距为 **%s**。
                    - 当前最低门店距离目标仍有 **%d** 台差额，适合优先结合在手商机和线索跟进一起复盘。

                    ## 建议
                    - 优先跟进 %s 的待成交商机与高意向线索，缩小目标缺口。
                    - 再按城市或集团维度查看哪些门店的目标完成节奏明显偏慢。

                    FOLLOW_UP_QUESTIONS:
                    1. 这个范围内哪些门店的高概率商机最多？
                    2. 目标达成率最低的门店近期跟进任务状态如何？
                    """.formatted(
                    scope.summary(language),
                    lowest.dealerName(),
                    formatPercent(lowest.achievementRate()),
                    formatPercent(averageRate),
                    metrics.size(),
                    lowest.dealerName(),
                    lowest.wonCount(),
                    lowest.targetValue(),
                    highest.dealerName(),
                    highest.wonCount(),
                    highest.targetValue(),
                    formatPercent(averageRate),
                    formatPercent(highest.achievementRate() - lowest.achievementRate()),
                    Math.max(lowest.targetValue() - lowest.wonCount(), 0),
                    lowest.dealerName()
            );
        }

        return """
                ## Conclusion
                Within %s, **%s** has the lowest target achievement at **%s**. The overall average achievement rate is **%s**.

                ## Data Support
                - Dealers covered: %d
                - Lowest achievement: %s (%d / %d)
                - Highest achievement: %s (%d / %d)
                - Average achievement: %s

                ## Analysis
                - The gap between the lowest and highest dealers is **%s**.
                - The lowest dealer is still **%d** units behind its target, so pipeline follow-up should be prioritized.

                ## Recommendation
                - Focus first on the open opportunities and high-intent leads for %s.
                - Then compare slower-performing dealers by city or dealer group.

                FOLLOW_UP_QUESTIONS:
                1. Which dealers in this scope have the strongest high-probability opportunity pipeline?
                2. What is the recent follow-up task status for the lowest-achievement dealer?
                """.formatted(
                scope.summary(language),
                lowest.dealerName(),
                formatPercent(lowest.achievementRate()),
                formatPercent(averageRate),
                metrics.size(),
                lowest.dealerName(),
                lowest.wonCount(),
                lowest.targetValue(),
                highest.dealerName(),
                highest.wonCount(),
                highest.targetValue(),
                formatPercent(averageRate),
                formatPercent(highest.achievementRate() - lowest.achievementRate()),
                Math.max(lowest.targetValue() - lowest.wonCount(), 0),
                lowest.dealerName()
        );
    }

    private String analyzeOpportunityFunnel(AnalysisScope scope, String language) {
        List<Opportunity> filtered = opportunityRepository.findAll().stream()
                .filter(opportunity -> matchesScope(opportunity.getDealerCode(), opportunity.getDealerName(), opportunity.getCity(),
                        opportunity.getDealerGroupName(), opportunity.getProductModel(), scope))
                .toList();

        if (filtered.isEmpty()) {
            return buildNoDataReply(language, scope, "opportunity funnel");
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
        String topLeadSource = filtered.stream()
                .collect(Collectors.groupingBy(Opportunity::getLeadSource, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内当前共有 **%d** 条商机，主力阶段集中在 **%s**，已赢单 **%d** 条，仍有 **%d** 条高概率商机值得重点推动。

                    ## 数据支撑
                    - 商机总数：%d
                    - 赢单数：%d
                    - 丢单数：%d
                    - 高概率待推进商机：%d
                    - 主要线索来源：%s

                    ## 分析
                    - 当前漏斗顶部到中后段仍有一定积压，说明中间阶段的推进效率还有提升空间。
                    - 如果继续优先跟进高概率商机，短期内更容易转化为新增赢单。

                    ## 建议
                    - 先按阶段盘点 %s 的商机卡点，优先解决推进瓶颈。
                    - 再追踪来自 %s 的商机转化效率，看是否值得继续加大投入。

                    FOLLOW_UP_QUESTIONS:
                    1. 这个范围内高概率商机主要集中在哪些门店？
                    2. 当前漏斗里丢单最多的阶段是什么？
                    """.formatted(
                    scope.summary(language),
                    filtered.size(),
                    topStage,
                    wonCount,
                    highProbabilityCount,
                    filtered.size(),
                    wonCount,
                    lostCount,
                    highProbabilityCount,
                    topLeadSource,
                    topStage,
                    topLeadSource
            );
        }

        return """
                ## Conclusion
                Within %s, there are **%d** opportunities in total. The main stage concentration is **%s**, with **%d** deals already won and **%d** high-probability opportunities still worth pushing.

                ## Data Support
                - Total opportunities: %d
                - Won opportunities: %d
                - Lost opportunities: %d
                - High-probability open opportunities: %d
                - Primary lead source: %s

                ## Analysis
                - The funnel still shows some accumulation in the middle and late stages, which suggests room to improve stage progression efficiency.
                - Prioritizing the high-probability set should provide the fastest short-term win uplift.

                ## Recommendation
                - Review the blocking points around the **%s** stage first.
                - Then evaluate whether the opportunities from **%s** justify additional investment.

                FOLLOW_UP_QUESTIONS:
                1. Which dealers hold most of the high-probability pipeline in this scope?
                2. Which stage currently contributes the most lost opportunities?
                """.formatted(
                scope.summary(language),
                filtered.size(),
                topStage,
                wonCount,
                highProbabilityCount,
                filtered.size(),
                wonCount,
                lostCount,
                highProbabilityCount,
                topLeadSource,
                topStage,
                topLeadSource
        );
    }

    private String analyzeSalesFollowUp(AnalysisScope scope, String language) {
        List<Task> filtered = taskRepository.findAll().stream()
                .filter(task -> matchesScope(task.getDealerCode(), task.getDealerName(), task.getCity(),
                        task.getDealerGroupName(), null, scope))
                .toList();

        if (filtered.isEmpty()) {
            return buildNoDataReply(language, scope, "sales follow-up");
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
        long overdueCount = filtered.stream().filter(task -> "Overdue".equalsIgnoreCase(task.getStatus())).count();

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内，销售跟进压力最大的门店是 **%s**，未完成任务占比达到 **%s**。

                    ## 数据支撑
                    - 任务总数：%d
                    - 未完成任务数：%d
                    - 逾期任务数：%d
                    - 未完成占比最高门店：%s（%d / %d）

                    ## 分析
                    - 如果未完成任务长期积压，通常会直接影响商机推进节奏和目标达成。
                    - 当前逾期任务已经出现，说明部分门店的销售动作需要更强的过程管理。

                    ## 建议
                    - 优先清理 %s 的待办与逾期任务，确保高意向商机不掉队。
                    - 再按门店维度对比已完成率，找出需要日常督导的销售团队。

                    FOLLOW_UP_QUESTIONS:
                    1. 这个范围内逾期任务主要集中在哪些门店？
                    2. 未完成任务较多的门店目标达成率怎么样？
                    """.formatted(
                    scope.summary(language),
                    highestBacklog.dealerName(),
                    formatPercent(highestBacklog.backlogRate()),
                    filtered.size(),
                    filtered.size() - filtered.stream().filter(task -> "Completed".equalsIgnoreCase(task.getStatus())).count(),
                    overdueCount,
                    highestBacklog.dealerName(),
                    highestBacklog.backlogCount(),
                    highestBacklog.totalCount(),
                    highestBacklog.dealerName()
            );
        }

        return """
                ## Conclusion
                Within %s, **%s** has the heaviest follow-up backlog, with **%s** of tasks still not completed.

                ## Data Support
                - Total tasks: %d
                - Open tasks: %d
                - Overdue tasks: %d
                - Highest backlog dealer: %s (%d / %d)

                ## Analysis
                - A persistent task backlog usually slows opportunity progression and affects target achievement.
                - The presence of overdue tasks suggests that stronger execution management is needed for some stores.

                ## Recommendation
                - Clear the pending and overdue tasks for %s first, especially around high-intent opportunities.
                - Then compare completion rates by dealer to identify teams that need closer supervision.

                FOLLOW_UP_QUESTIONS:
                1. Which dealers account for most of the overdue tasks in this scope?
                2. How does the target achievement of backlog-heavy dealers compare with others?
                """.formatted(
                scope.summary(language),
                highestBacklog.dealerName(),
                formatPercent(highestBacklog.backlogRate()),
                filtered.size(),
                filtered.size() - filtered.stream().filter(task -> "Completed".equalsIgnoreCase(task.getStatus())).count(),
                overdueCount,
                highestBacklog.dealerName(),
                highestBacklog.backlogCount(),
                highestBacklog.totalCount(),
                highestBacklog.dealerName()
        );
    }

    private String analyzeCampaignPerformance(AnalysisScope scope, String language) {
        List<Campaign> filtered = campaignRepository.findAll().stream()
                .filter(campaign -> matchesScope(campaign.getDealerCode(), campaign.getDealerName(), campaign.getCity(),
                        campaign.getDealerGroupName(), campaign.getProductModel(), scope))
                .toList();

        if (filtered.isEmpty()) {
            return buildNoDataReply(language, scope, "campaign performance");
        }

        List<CampaignMetric> metrics = filtered.stream()
                .map(campaign -> new CampaignMetric(
                        campaign.getCampaignId(),
                        campaign.getDealerName(),
                        campaign.getCampaignType(),
                        campaign.getActualOpportunityCount(),
                        campaign.getTotalNewCustomerTarget(),
                        percentage(campaign.getActualOpportunityCount(), campaign.getTotalNewCustomerTarget())
                ))
                .sorted(Comparator.comparingDouble(CampaignMetric::attainmentRate).reversed())
                .toList();

        CampaignMetric best = metrics.getFirst();
        double averageAttainment = metrics.stream().mapToDouble(CampaignMetric::attainmentRate).average().orElse(0.0);

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内，当前活动效果最好的是 **%s** 的 **%s**，活动达成率为 **%s**；整体平均达成率为 **%s**。

                    ## 数据支撑
                    - 活动数量：%d
                    - 最佳活动：%s（%d / %d）
                    - 平均活动达成率：%s

                    ## 分析
                    - 达成率更高的活动通常意味着线索组织、邀约和现场转化流程更顺畅。
                    - 如果同类活动在不同门店表现差异明显，就值得复盘执行动作与资源投入。

                    ## 建议
                    - 复用 %s 的活动打法，优先复制到相近城市或同车型门店。
                    - 再对比低于平均线的活动，找出线索获取和现场转化的主要短板。

                    FOLLOW_UP_QUESTIONS:
                    1. 哪些活动达成率低于平均水平？
                    2. 表现最好的活动主要带来了哪些车型的商机？
                    """.formatted(
                    scope.summary(language),
                    best.dealerName(),
                    best.campaignType(),
                    formatPercent(best.attainmentRate()),
                    formatPercent(averageAttainment),
                    metrics.size(),
                    best.campaignId(),
                    best.actualOpportunityCount(),
                    best.totalNewCustomerTarget(),
                    formatPercent(averageAttainment),
                    best.dealerName()
            );
        }

        return """
                ## Conclusion
                Within %s, the strongest campaign is **%s** by **%s**, with an attainment rate of **%s**. The overall campaign average is **%s**.

                ## Data Support
                - Campaign count: %d
                - Best campaign: %s (%d / %d)
                - Average attainment: %s

                ## Analysis
                - Higher attainment usually indicates smoother lead organization, invitation quality, and on-site conversion.
                - Large gaps between similar campaigns suggest execution and allocation differences worth reviewing.

                ## Recommendation
                - Reuse the playbook from %s and replicate it first to similar cities or product lines.
                - Then compare the campaigns below average to identify the biggest gaps in lead acquisition and conversion.

                FOLLOW_UP_QUESTIONS:
                1. Which campaigns are currently below the average attainment rate?
                2. Which product models benefited most from the best-performing campaign?
                """.formatted(
                scope.summary(language),
                best.campaignType(),
                best.dealerName(),
                formatPercent(best.attainmentRate()),
                formatPercent(averageAttainment),
                metrics.size(),
                best.campaignId(),
                best.actualOpportunityCount(),
                best.totalNewCustomerTarget(),
                formatPercent(averageAttainment),
                best.dealerName()
        );
    }

    private String analyzeLeadSource(AnalysisScope scope, String language) {
        List<Lead> filtered = leadRepository.findAll().stream()
                .filter(lead -> matchesScope(lead.getDealerCode(), lead.getDealerName(), lead.getCity(),
                        lead.getDealerGroupName(), lead.getProductModel(), scope))
                .toList();

        if (filtered.isEmpty()) {
            return buildNoDataReply(language, scope, "lead source analysis");
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

        LeadSourceMetric topVolume = sourceMetrics.getFirst();
        LeadSourceMetric topConversion = sourceMetrics.stream()
                .max(Comparator.comparingDouble(LeadSourceMetric::conversionRate))
                .orElse(topVolume);

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内，当前线索量最大的来源是 **%s**，而转化率最高的来源是 **%s**。

                    ## 数据支撑
                    - 线索总数：%d
                    - 最大来源：%s（%d 条）
                    - 最高转化来源：%s（转化率 %s）

                    ## 分析
                    - 量大并不一定代表转化最好，需要同时看来源规模和转化效率。
                    - 如果网站或自然流量类来源线索较少，可以继续观察是否需要补强内容或投放入口。

                    ## 建议
                    - 继续放大 %s 的获客规模，同时跟踪其后续转化质量。
                    - 优先复盘 %s 的转化路径，提炼成可复制的线索跟进打法。

                    FOLLOW_UP_QUESTIONS:
                    1. 网站来源线索在当前范围内的转化表现如何？
                    2. 哪些门店最依赖单一线索来源？
                    """.formatted(
                    scope.summary(language),
                    topVolume.source(),
                    topConversion.source(),
                    filtered.size(),
                    topVolume.source(),
                    topVolume.leadCount(),
                    topConversion.source(),
                    formatPercent(topConversion.conversionRate()),
                    topVolume.source(),
                    topConversion.source()
            );
        }

        return """
                ## Conclusion
                Within %s, **%s** delivers the highest lead volume, while **%s** has the strongest conversion rate.

                ## Data Support
                - Total leads: %d
                - Highest-volume source: %s (%d leads)
                - Best conversion source: %s (conversion rate %s)

                ## Analysis
                - Volume does not always equal quality, so both scale and conversion efficiency matter.
                - If website or organic-style sources stay small, it may be worth reviewing content and acquisition entry points.

                ## Recommendation
                - Keep scaling %s while monitoring downstream conversion quality.
                - Review the path behind %s and turn that conversion flow into a repeatable follow-up playbook.

                FOLLOW_UP_QUESTIONS:
                1. How does website-sourced lead conversion perform in this scope?
                2. Which dealers rely too heavily on a single lead source?
                """.formatted(
                scope.summary(language),
                topVolume.source(),
                topConversion.source(),
                filtered.size(),
                topVolume.source(),
                topVolume.leadCount(),
                topConversion.source(),
                formatPercent(topConversion.conversionRate()),
                topVolume.source(),
                topConversion.source()
        );
    }

    private String analyzeDealerBenchmark(AnalysisScope scope, String language) {
        List<Target> filteredTargets = targetRepository.findAll().stream()
                .filter(target -> matchesScope(target.getDealerCode(), target.getDealerName(), target.getCity(),
                        target.getDealerGroupName(), target.getProductModel(), scope))
                .toList();

        if (filteredTargets.isEmpty()) {
            return buildNoDataReply(language, scope, "dealer benchmark");
        }

        List<DealerTargetMetric> metrics = filteredTargets.stream()
                .map(target -> new DealerTargetMetric(
                        target.getDealerCode(),
                        target.getDealerName(),
                        target.getCity(),
                        target.getProductModel(),
                        target.getAsKTarget(),
                        target.getOpportunityWonCount(),
                        percentage(target.getOpportunityWonCount(), target.getAsKTarget())
                ))
                .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate).reversed())
                .toList();

        DealerTargetMetric best = metrics.getFirst();
        DealerTargetMetric lowest = metrics.getLast();

        if ("zh".equals(language)) {
            return """
                    ## 结论
                    %s范围内，当前经营表现最好的是 **%s**，而最需要关注的是 **%s**。

                    ## 数据支撑
                    - 最优门店达成率：%s
                    - 待关注门店达成率：%s
                    - 对标门店数量：%d

                    ## 分析
                    - 高表现门店通常在目标推进和成交转化上更均衡，适合作为横向对标对象。
                    - 低表现门店当前目标缺口更大，需要结合商机和任务状态一起看执行问题。

                    ## 建议
                    - 先复用 %s 的经营打法和活动节奏。
                    - 再针对 %s 做专项跟进，优先缩小目标与成交差距。

                    FOLLOW_UP_QUESTIONS:
                    1. 最优门店和最低门店的商机结构差别在哪里？
                    2. 需要关注的门店近期活动效果怎么样？
                    """.formatted(
                    scope.summary(language),
                    best.dealerName(),
                    lowest.dealerName(),
                    formatPercent(best.achievementRate()),
                    formatPercent(lowest.achievementRate()),
                    metrics.size(),
                    best.dealerName(),
                    lowest.dealerName()
            );
        }

        return """
                ## Conclusion
                Within %s, **%s** is currently the top-performing dealer, while **%s** needs the most attention.

                ## Data Support
                - Best dealer achievement rate: %s
                - Lowest dealer achievement rate: %s
                - Dealers compared: %d

                ## Analysis
                - The stronger dealer appears more balanced across target execution and conversion outcomes.
                - The weaker dealer has a larger target gap and should be reviewed alongside its opportunity and task execution.

                ## Recommendation
                - Reuse the operating rhythm and campaign approach from %s first.
                - Then run a focused improvement plan for %s to close the target and win-gap.

                FOLLOW_UP_QUESTIONS:
                1. How does the opportunity mix differ between the best and lowest dealer?
                2. What has recent campaign performance looked like for the dealer that needs attention?
                """.formatted(
                scope.summary(language),
                best.dealerName(),
                lowest.dealerName(),
                formatPercent(best.achievementRate()),
                formatPercent(lowest.achievementRate()),
                metrics.size(),
                best.dealerName(),
                lowest.dealerName()
        );
    }

    private List<String> buildProgressMessages(String language, String topicLabel) {
        if ("zh".equals(language)) {
            return List.of(
                    "正在识别分析主题",
                    "正在筛选" + topicLabel + "相关数据",
                    "正在生成结构化结论"
            );
        }

        return List.of(
                "Identifying the analysis theme",
                "Filtering data related to " + topicLabel,
                "Generating a structured response"
        );
    }

    private String buildNoDataReply(String language, AnalysisScope scope, String topicLabel) {
        if ("zh".equals(language)) {
            return """
                    ## 结论
                    当前在%s范围内，暂时没有足够的数据支撑%s。

                    ## 建议
                    - 请尝试放宽筛选范围，例如改为按城市或全量样例数据查看。
                    - 也可以换一个问题方向，例如目标达成、活动效果或线索来源。

                    FOLLOW_UP_QUESTIONS:
                    1. 放大到全量样例数据后，这个问题的整体情况如何？
                    2. 这个范围内还有哪些与经营表现相关的数据可以先看？
                    """.formatted(scope.summary(language), topicLabel);
        }

        return """
                ## Conclusion
                There is not enough data within %s to support a reliable %s answer.

                ## Recommendation
                - Try widening the scope to a city-level or the full sample dataset.
                - Or switch to another angle such as target achievement, campaign performance, or lead sources.

                FOLLOW_UP_QUESTIONS:
                1. What does this look like across the full sample dataset?
                2. Which other operating metrics are available in this scope?
                """.formatted(scope.summary(language), topicLabel);
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
        return matchesField(dealerCode, scope.dealerCode())
                && matchesField(dealerName, scope.dealerName())
                && matchesField(city, scope.city())
                && matchesField(dealerGroupName, scope.dealerGroupName())
                && matchesField(productModel, scope.productModel());
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

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    public record AnalyticsResponse(String reply, List<String> progressMessages, String visibleThinking) {
    }

    private enum AnalysisTopic {
        TARGET_ACHIEVEMENT("目标达成分析", "target achievement"),
        OPPORTUNITY_FUNNEL("商机漏斗分析", "opportunity funnel"),
        SALES_FOLLOW_UP("销售跟进分析", "sales follow-up"),
        CAMPAIGN_PERFORMANCE("活动效果分析", "campaign performance"),
        LEAD_SOURCE("线索来源分析", "lead source analysis"),
        DEALER_BENCHMARK("门店对标分析", "dealer benchmark");

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

    private record AnalysisScope(
            String city,
            String dealerCode,
            String dealerName,
            String dealerGroupName,
            String productModel
    ) {
        String summary(String language) {
            List<String> parts = new ArrayList<>();

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

            if (parts.isEmpty()) {
                return "zh".equals(language) ? "当前样例数据范围" : "the current sample dataset";
            }

            return String.join(" / ", parts);
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

    private record TaskBacklogMetric(
            String dealerName,
            int totalCount,
            long backlogCount,
            double backlogRate
    ) {
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
}
