package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ChatReplyGuard {

    private static final Pattern SECOND_LEVEL_HEADING_LINE_PATTERN = Pattern.compile("(?m)^##[^\\r\\n]*$");
    private static final Pattern DATA_SUPPORT_HEADING_PATTERN = Pattern.compile(
            "^##[\\s\\h]*(?:\\d+[\\s\\h\\.、]*)?(?:Data Support|数据支撑)[\\s\\h]*$",
            Pattern.MULTILINE
    );
    private static final Pattern FORBIDDEN_SUMMARY_HEADING_PATTERN = Pattern.compile(
            "^##[\\s\\h]*(?:\\d+[\\s\\h\\.、]*)?(?:数据汇总|Data Summary|数据总览|Data Overview|摘要段落|Summary)[\\s\\h]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, List<String>> METRIC_TERMS = Map.of(
            "zh", List.of("达成率", "转化率", "商机", "线索", "任务", "活动", "ROI", "销量",
                    "赢单", "成交", "畅销", "流失", "漏斗", "目标", "活跃度", "参与度", "跟进", "时效", "逾期",
                    "购买周期", "购车周期"),
            "en", List.of("achievement", "conversion", "opportunity", "lead", "task", "campaign",
                    "ROI", "sales", "win", "drop-off", "funnel", "target", "activity", "participation",
                    "follow-up", "turnaround", "overdue")
    );
    private static final Map<String, List<String>> SCENARIO_TERMS = Map.of(
            "zh", List.of("目标达成", "商机漏斗", "转化分析", "销售跟进", "活动效果", "市场活动",
                    "经营对标", "对标分析", "线索来源", "自然流量", "经营活跃度", "门店活跃"),
            "en", List.of("target achievement", "opportunity funnel", "conversion analysis",
                    "sales follow-up", "campaign performance", "dealer benchmark", "lead source",
                    "organic traffic", "business activity", "dealer activity")
    );

    private final LanguageDetector languageDetector;

    ChatReplyGuard(LanguageDetector languageDetector) {
        this.languageDetector = languageDetector;
    }

    boolean isValidAnalyticsReply(String reply, String fallbackReply) {
        String normalized = reply == null ? "" : reply.trim();
        if (normalized.isBlank()) {
            return false;
        }

        if (containsForbiddenSummarySection(normalized)) {
            return false;
        }

        String language = languageDetector.detectLanguage(fallbackReply);
        if (!matchesExpectedHeadingSequence(normalized, language)) {
            return false;
        }

        String replyTable = extractDataSupportTable(normalized);
        String fallbackTable = extractDataSupportTable(fallbackReply);
        if (!hasText(replyTable) || !hasText(fallbackTable)) {
            return false;
        }

        return normalizeBlock(replyTable).equals(normalizeBlock(fallbackTable))
                && hasValidAnalyticsFollowUpQuestions(normalized);
    }

    String ensureFollowUpQuestions(String reply, String language, boolean analyticsRequested) {
        String trimmed = reply == null ? "" : reply.trim();
        if (trimmed.isBlank()) {
            throw new IllegalStateException("Reply is blank after model generation.");
        }

        List<String> contextDefaults = buildContextualFollowUps(language, trimmed);

        if (analyticsRequested) {
            return ensureAnalyticsFollowUpQuestions(trimmed, language, contextDefaults);
        }

        String repaired;
        if (hasExactlyTwoFollowUpQuestions(trimmed)) {
            repaired = trimmed;
        } else if (trimmed.contains("FOLLOW_UP_QUESTIONS:") || trimmed.contains("追问：")) {
            repaired = repairPartialFollowUpQuestions(trimmed, contextDefaults, true);
        } else {
            return """
                    %s

                    FOLLOW_UP_QUESTIONS:
                    1. %s
                    2. %s
                    """.formatted(trimmed, contextDefaults.getFirst(), contextDefaults.getLast()).trim();
        }

        List<String> extracted = extractFollowUpsFromReply(repaired);
        if (extracted.size() == 2) {
            List<String> validated = validateFollowUpRelevance(repaired, extracted, language, contextDefaults);
            return rebuildReplyWithFollowUps(repaired, validated);
        }

        return repaired;
    }

    private String ensureAnalyticsFollowUpQuestions(
            String reply,
            String language,
            List<String> contextDefaults
    ) {
        if (findFollowUpMarker(reply) == null) {
            return reply;
        }

        String repaired = repairPartialFollowUpQuestions(reply, contextDefaults, false);
        List<String> extracted = extractFollowUpsFromReply(repaired);
        if (extracted.isEmpty()) {
            return removeFollowUpBlock(repaired);
        }

        List<String> validated = validateAnalyticsFollowUpRelevance(repaired, extracted, language);
        return rebuildReplyWithFollowUps(repaired, validated);
    }

    List<String> buildContextualFollowUps(String language, String reply) {
        if (reply == null || reply.isBlank()) {
            return defaultGeneralFollowUps(language);
        }
        boolean isZh = "zh".equals(language);
        String haystack = isZh ? reply : reply.toLowerCase();

        if (haystack.contains("目标达成") || haystack.contains("达成率") || haystack.contains("target achievement")) {
            return isZh
                    ? List.of("达成短板主要在哪个车型？", "要不要对比同城市其他店的达成率？")
                    : List.of("Which model drags down achievement the most?",
                            "Compare achievement rates across other city dealers?");
        }
        if (haystack.contains("商机漏斗") || haystack.contains("商机转化") || haystack.contains("opportunity funnel")) {
            return isZh
                    ? List.of("哪个阶段的商机流失最严重？", "要不要按销售顾问拆分转化率？")
                    : List.of("Which funnel stage has the highest drop-off?",
                            "Break down conversion by sales consultant?");
        }
        if (haystack.contains("销售跟进") || haystack.contains("逾期") || haystack.contains("sales follow-up")) {
            return isZh
                    ? List.of("逾期任务集中在哪些门店？", "要不要查看任务完成率的月度趋势？")
                    : List.of("Which dealers have the most overdue tasks?",
                            "Check monthly task completion trends?");
        }
        if (haystack.contains("活动效果") || haystack.contains("市场活动") || haystack.contains("campaign")) {
            return isZh
                    ? List.of("本次活动ROI和去年同期比如何？", "要不要看各门店的活动参与度排名？")
                    : List.of("How does this campaign ROI compare to last year?",
                            "Rank dealers by campaign participation?");
        }
        if (haystack.contains("经营对标") || haystack.contains("门店对标") || haystack.contains("dealer benchmark")) {
            return isZh
                    ? List.of("要不要下钻到车型维度对比？", "这些门店的线索跟进时效如何？")
                    : List.of("Drill down by model dimension?",
                            "How is lead follow-up turnaround at these dealers?");
        }
        if (haystack.contains("线索来源") || haystack.contains("自然流量") || haystack.contains("lead source")) {
            return isZh
                    ? List.of("高意向线索主要来自哪个渠道？", "要不要对比各门店的线索跟进速度？")
                    : List.of("Which channel generates the highest-intent leads?",
                            "Compare lead follow-up speed across dealers?");
        }
        if (haystack.contains("经营活跃度") || haystack.contains("门店活跃") || haystack.contains("business activity")) {
            return isZh
                    ? List.of("活跃度最低的门店在哪个维度失分最多？", "要不要对比活跃度和目标达成率的关系？")
                    : List.of("Which dimension drags down the lowest-activity dealers?",
                            "Correlate activity score with target achievement?");
        }

        return defaultGeneralFollowUps(language);
    }

    List<String> validateFollowUpRelevance(
            String reply,
            List<String> followUps,
            String language,
            List<String> fallbackDefaults
    ) {
        List<String> topicKeywords = extractTopicKeywords(reply, language);

        if (topicKeywords.isEmpty()) {
            return followUps;
        }

        List<String> validated = new ArrayList<>();
        for (String followUp : followUps) {
            if (isStronglyRelevant(followUp, topicKeywords)) {
                validated.add(followUp);
            }
        }

        if (validated.isEmpty()) {
            return fallbackDefaults;
        }

        if (validated.size() == 1) {
            for (String candidate : fallbackDefaults) {
                if (validated.size() >= 2) break;
                if (!validated.contains(candidate)) {
                    validated.add(candidate);
                }
            }
        }

        return validated.subList(0, Math.min(validated.size(), 2));
    }

    List<String> extractFollowUpsFromReply(String reply) {
        return extractRawFollowUpLines(reply).stream()
                .map(ChatReplyGuard::cleanFollowUpLine)
                .filter(ChatReplyGuard::hasText)
                .limit(2)
                .toList();
    }

    List<String> extractTopicKeywords(String reply, String language) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = reply == null ? "" : reply;
        boolean isZh = "zh".equals(language);

        for (String term : METRIC_TERMS.getOrDefault(language, METRIC_TERMS.get("en"))) {
            String haystack = isZh ? normalized : normalized.toLowerCase();
            String needle = isZh ? term : term.toLowerCase();
            if (haystack.contains(needle)) {
                keywords.add(term);
            }
        }

        for (String term : SCENARIO_TERMS.getOrDefault(language, SCENARIO_TERMS.get("en"))) {
            String haystack = isZh ? normalized : normalized.toLowerCase();
            String needle = isZh ? term : term.toLowerCase();
            if (haystack.contains(needle)) {
                keywords.add(term);
            }
        }

        return List.copyOf(keywords);
    }

    boolean isStronglyRelevant(String followUp, List<String> topicKeywords) {
        if (followUp == null || topicKeywords == null) {
            return false;
        }
        for (String keyword : topicKeywords) {
            if (followUp.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExpectedHeadingSequence(String reply, String language) {
        List<String> headings = extractSecondLevelHeadingLines(reply);
        List<Pattern> expected = "zh".equals(language) ? zhHeadingPatterns() : enHeadingPatterns();
        if (headings.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).matcher(headings.get(index)).matches()) {
                return false;
            }
        }
        return true;
    }

    private List<String> extractSecondLevelHeadingLines(String reply) {
        Matcher matcher = SECOND_LEVEL_HEADING_LINE_PATTERN.matcher(reply);
        List<String> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(matcher.group().trim());
        }
        return headings;
    }

    private List<Pattern> zhHeadingPatterns() {
        return List.of(
                headingPattern(1, "接口调用链"),
                headingPattern(2, "核心结论"),
                headingPattern(3, "数据支撑"),
                headingPattern(4, "经营分析"),
                headingPattern(5, "问题诊断与解决"),
                headingPattern(6, "改进建议")
        );
    }

    private List<Pattern> enHeadingPatterns() {
        return List.of(
                headingPattern(1, "Interface Call Chain"),
                headingPattern(2, "Conclusion"),
                headingPattern(3, "Data Support"),
                headingPattern(4, "Short Analysis"),
                headingPattern(5, "Problem Diagnosis & Solutions"),
                headingPattern(6, "Improvement Suggestions")
        );
    }

    private Pattern headingPattern(int optionalNumber, String headingText) {
        return Pattern.compile(
                "^##[\\s\\h]*(?:%d[\\s\\h\\.、]*)?%s[\\s\\h]*$"
                        .formatted(optionalNumber, Pattern.quote(headingText)),
                Pattern.MULTILINE
        );
    }

    private boolean containsForbiddenSummarySection(String reply) {
        return reply != null && FORBIDDEN_SUMMARY_HEADING_PATTERN.matcher(reply).find();
    }

    private String extractDataSupportTable(String reply) {
        Matcher supportHeading = DATA_SUPPORT_HEADING_PATTERN.matcher(reply);
        if (!supportHeading.find()) {
            return null;
        }

        int start = supportHeading.end();
        Matcher nextHeading = SECOND_LEVEL_HEADING_LINE_PATTERN.matcher(reply);
        nextHeading.region(start, reply.length());
        int end = nextHeading.find() ? nextHeading.start() : reply.length();
        if (end <= start) {
            return null;
        }

        String section = reply.substring(start, end);
        Matcher matcher = Pattern.compile("(?is)<table\\b.*?</table>").matcher(section);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean hasExactlyTwoFollowUpQuestions(String reply) {
        List<String> lines = extractRawFollowUpLines(reply);
        return lines.size() == 2
                && lines.getFirst().matches("1\\.\\s+.+")
                && lines.getLast().matches("2\\.\\s+.+");
    }

    private boolean hasValidAnalyticsFollowUpQuestions(String reply) {
        if (findFollowUpMarker(reply) == null) {
            return true;
        }

        List<String> lines = extractRawFollowUpLines(reply);
        if (lines.isEmpty()) {
            return false;
        }

        if (lines.size() > 2) {
            return false;
        }

        for (int index = 0; index < lines.size(); index++) {
            if (!lines.get(index).matches("%d\\.\\s+.+".formatted(index + 1))) {
                return false;
            }
        }
        return true;
    }

    private String normalizeBlock(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String rebuildReplyWithFollowUps(String reply, List<String> followUps) {
        FollowUpMarker marker = findLastFollowUpMarker(reply);
        if (marker == null) {
            return reply;
        }
        if (followUps == null || followUps.isEmpty()) {
            return reply.substring(0, marker.index()).stripTrailing();
        }

        StringBuilder rebuilt = new StringBuilder(reply.substring(0, marker.index() + marker.marker().length()));
        int count = Math.min(followUps.size(), 2);
        for (int index = 0; index < count; index++) {
            rebuilt.append("\n").append(index + 1).append(". ").append(followUps.get(index));
        }
        return rebuilt.toString().trim();
    }

    private String repairPartialFollowUpQuestions(String reply, List<String> defaults, boolean requireTwo) {
        FollowUpMarker marker = findFollowUpMarker(reply);
        if (marker == null) {
            return reply;
        }

        List<String> candidates = extractRawFollowUpLines(reply).stream()
                .map(ChatReplyGuard::cleanFollowUpLine)
                .filter(ChatReplyGuard::hasText)
                .limit(2)
                .toList();

        List<String> repaired = new ArrayList<>(candidates);
        if (requireTwo) {
            if (repaired.size() == 1 && defaults.size() > 1 && !repaired.contains(defaults.getLast())) {
                repaired.add(defaults.getLast());
            }
            for (String defaultQuestion : defaults) {
                if (repaired.size() >= 2) {
                    break;
                }
                if (!repaired.contains(defaultQuestion)) {
                    repaired.add(defaultQuestion);
                }
            }
        }

        String prefix = reply.substring(0, marker.index() + marker.marker().length());
        int count = Math.min(repaired.size(), requireTwo ? 2 : repaired.size());
        StringBuilder rebuilt = new StringBuilder(prefix);

        for (int index = 0; index < count; index++) {
            rebuilt.append("\n").append(index + 1).append(". ").append(repaired.get(index));
        }

        return rebuilt.toString().trim();
    }

    private List<String> validateAnalyticsFollowUpRelevance(
            String reply,
            List<String> followUps,
            String language
    ) {
        List<String> topicKeywords = extractTopicKeywords(reply, language);
        if (topicKeywords.isEmpty()) {
            return followUps.subList(0, Math.min(followUps.size(), 2));
        }

        List<String> validated = followUps.stream()
                .filter(followUp -> isStronglyRelevant(followUp, topicKeywords))
                .limit(2)
                .toList();
        return validated;
    }

    private String removeFollowUpBlock(String reply) {
        FollowUpMarker marker = findLastFollowUpMarker(reply);
        return marker == null ? reply : reply.substring(0, marker.index()).stripTrailing();
    }

    private List<String> extractRawFollowUpLines(String reply) {
        FollowUpMarker marker = findFollowUpMarker(reply);
        if (marker == null) {
            return List.of();
        }

        return reply.substring(marker.index() + marker.marker().length()).trim().lines()
                .map(String::trim)
                .filter(ChatReplyGuard::hasText)
                .toList();
    }

    private FollowUpMarker findFollowUpMarker(String reply) {
        String normalized = reply == null ? "" : reply;
        FollowUpMarker best = null;
        for (String marker : List.of("FOLLOW_UP_QUESTIONS:", "追问：")) {
            int index = normalized.indexOf(marker);
            if (index >= 0 && (best == null || index < best.index())) {
                best = new FollowUpMarker(index, marker);
            }
        }
        return best;
    }

    private FollowUpMarker findLastFollowUpMarker(String reply) {
        String normalized = reply == null ? "" : reply;
        FollowUpMarker best = null;
        for (String marker : List.of("FOLLOW_UP_QUESTIONS:", "追问：")) {
            int index = normalized.lastIndexOf(marker);
            if (index >= 0 && (best == null || index > best.index())) {
                best = new FollowUpMarker(index, marker);
            }
        }
        return best;
    }

    private static String cleanFollowUpLine(String line) {
        return line == null ? "" : line.replaceFirst("^\\s*(?:\\d+\\.\\s*|[-*·•]\\s*)", "")
                .replaceAll("[*_~]+", "")
                .trim();
    }

    private List<String> defaultGeneralFollowUps(String language) {
        if ("zh".equals(language)) {
            return List.of(
                    "你想先继续配置模型连接，还是先验证现有聊天链路？",
                    "要不要我顺手把这 7 个配置项整理成可直接复制的模板？"
            );
        }

        return List.of(
                "Do you want to finish the model connection first or validate the current chat flow first?",
                "Should I turn those seven configuration items into a copy-ready template?"
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FollowUpMarker(int index, String marker) {
    }
}
