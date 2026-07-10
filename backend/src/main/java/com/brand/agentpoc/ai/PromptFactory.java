package com.brand.agentpoc.ai;

import com.brand.agentpoc.service.AnalyticsScenarioCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PromptFactory {

    public String buildSystemPrompt(String language) {
        String workflowSummary = AnalyticsScenarioCatalog.workflowSummary();
        String scenarioMatrix = buildScenarioMatrix(language);
        String template = loadSystemPromptTemplate(language);
        return template.formatted(workflowSummary, scenarioMatrix);
    }

    public String buildVisibleThinking(
            String language,
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow,
            String scopeSummary
    ) {
        if ("zh".equals(language)) {
            return """
                    我会将当前问题归入【%s】场景，并以「%s」作为本次分析范围。接着按标准工具链获取事实：%s。随后结合场景逻辑「%s」区分数据事实、可支持判断和需要验证的假设，最后整理成规定结构；仅在有必要时补充 0-2 个追问。
                    """.formatted(
                    scenarioWorkflow.label(language),
                    scopeSummary,
                    scenarioWorkflow.toolChainSummary(),
                    scenarioWorkflow.logicSummary(language)
            );
        }

        return """
                I will treat this as a %s question and use %s as the working scope. I will gather the needed facts through the standard chain (%s), then apply the scenario logic (%s) to separate facts, supported judgments, and assumptions that still need validation before shaping the final report; I will add 0-2 follow-up questions only when useful.
                """.formatted(
                scenarioWorkflow.label(language),
                scopeSummary,
                scenarioWorkflow.toolChainSummary(),
                scenarioWorkflow.logicSummary(language)
        );
    }

    public String buildVisibleThinking(String language, List<CalcStep> traceSteps) {
        if (traceSteps == null || traceSteps.isEmpty()) {
            return "";
        }
        StringBuilder trace = new StringBuilder();
        boolean zh = "zh".equals(language);
        for (CalcStep step : traceSteps) {
            if (step == null) {
                continue;
            }
            String label = "zh".equals(language) ? step.labelZh() : step.labelEn();
            String detail = "zh".equals(language) ? step.detailZh() : step.detailEn();
            String fragment = formatTraceFragment(label, detail, zh);
            if (!fragment.isEmpty()) {
                if (!trace.isEmpty()) {
                    trace.append(zh ? "；" : "; ");
                }
                trace.append(fragment);
            }
        }

        if (trace.isEmpty()) {
            return zh
                    ? "我会依据已完成的计算轨迹组织分析。"
                    : "I will base the analysis on the completed calculation trace.";
        }

        return zh
                ? "我会依据已完成的计算轨迹组织分析：" + trace + "。"
                : "I will base the analysis on the completed calculation trace: " + trace + ".";
    }

    private String formatTraceFragment(String label, String detail, boolean zh) {
        String cleanLabel = cleanText(label);
        String cleanDetail = cleanText(detail);

        if (cleanLabel.isEmpty()) {
            return cleanDetail;
        }
        if (cleanDetail.isEmpty()) {
            return cleanLabel;
        }

        return zh ? cleanLabel + "（" + cleanDetail + "）" : cleanLabel + " (" + cleanDetail + ")";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public String buildGroundedModelPrompt(
            String language,
            String userMessage,
            String sessionHistory,
            String groundedReference
    ) {
        if ("zh".equals(language)) {
            return """
                    当前用户问题：
                    %s

                    最近会话上下文：
                    %s

                    参考事实（必须优先采用，不能与其冲突）：
                    %s

                    请基于以上内容输出一份信息充分、结构清晰的经营分析报告。
                    要求：
                    1. 使用中文
                    2. 段落标题必须严格使用以下中文标题，不得使用英文标题：
                       - ## 接口调用链
                       - ## 核心结论
                       - ## 数据支撑
                       - ## 经营分析
                       - ## 问题诊断与解决
                       - ## 改进建议
                       - 如果确实需要追问，在正文末尾使用 `追问：`
                       严禁修改以下二级标题的任何文字，严禁添加图标或改变 Markdown 层级，否则系统将无法识别。
                    3. 只能基于参考事实作答；不得改动任何 KPI 数值、门店名称、分析范围或表格事实，也不得引入参考事实之外的业务数据
                    4. 不要提到"参考事实"或"内部分析引擎"这类措辞
                    5. `## 接口调用链` 必须保留参考事实中的执行步骤事实，不得改写时间、范围、经销商编码或数据类别
                    6. `## 核心结论` 用 2-4 条要点写清核心发现、优先级或经营判断，引用具体数字；结论必须能从参考事实直接推出
                    7. `## 数据支撑` 段必须包含 HTML <table>，并在表格后保留参考事实中的 chart-json 或 chart-empty 图表代码块。不得把 chart-json 转换成 Mermaid。
                       除非是从参考事实中复制已经验证过的值，否则不得改写 JSON。
                       数值对比图使用 {"type":"bar"}；商机漏斗和线索来源分布使用 {"type":"pie"}。
                    8. chart-json 图表代码块必须严格包裹在 ```chart-json 和 ``` 围栏之间，JSON 必须保持单行压缩、合法双引号格式。严禁在围栏外输出任何裸 JSON 对象。
                       严禁使用 labels/datasets 字段名。bar 类型必须使用 "categories" 和 "values" 字段；pie 类型必须使用 "slices" 字段，每个 slice 含 "name" 和 "value"。
                       错误示例（禁止）：{"type":"bar","labels":["A","B"],"datasets":[{"label":"x","data":[1,2]}]}
                       正确示例：```chart-json\n{"type":"bar","title":"达成率","categories":["门店A","门店B"],"values":[80,92],"metric":"达成率","metricType":"percentage"}\n```
                       Mermaid 仅用于自由结构图，不用于固定数值分析图表。
                    9. 请将所有客观数字、表格纯粹保留在【数据支撑】中。严禁在【数据支撑】后方或报告末尾创造任何形式的“数据总览”、“总计尾注”或“摘要段落”。
                    10. `## 经营分析` 拆为两段：
                       - 数据归因：2-3 条，格式为 [指标变化] + [具体对象] + [证据支持的解释或可进一步验证方向]
                       - 可执行建议：2-3 条，格式为 [动作] + [对象] + [预期目标]
                    11. `## 问题诊断与解决` 用 1-2 条识别当前最大差距或瓶颈：
                        - 格式为 [差距/瓶颈指标] + [具体对象] + [证据状态] + [解决动作]
                        - 禁止把未证实推测写成根因；如证据不足，必须写成“可进一步验证方向”或“当前数据不足以证明原因”
                        - 如果各项指标达标（达成率 >= 80%%），说明当前表现稳定，并指出最需要关注的次弱指标
                    12. `## 改进建议` 根据达成率分支：
                        - 若达成率 >= 80%%：给 2 条建议，聚焦保持优势和验证可迁移做法，避免把单店结果包装成通用成功经验
                        - 若达成率 < 80%%：给 2-3 条分阶段建议，每条包含 [动作] + [对象] + [预期结果] + [时间范围（本月/下季度/年度）]
                    13. 追问策略：
                        - 如果当前答案已经完整，允许不输出追问
                        - 如果缺少会影响结论的关键口径，只问 1 个最关键问题
                        - 如果需要提供探索方向，最多输出 2 个编号追问，且必须与当前分析的具体门店、指标、口径或数据限制直接相关
                    """.formatted(userMessage, sessionHistory, groundedReference);
        }

        return """
                Current user question:
                %s

                Recent conversation context:
                %s

                Grounded reference facts (this is the only source of truth and must not be changed):
                %s

                Rewrite the answer into a well-structured analytics report that is informative and specific.
                Requirements:
                1. Answer in English.
                2. Section titles must be exactly:
                   - ## Interface Call Chain
                   - ## Conclusion
                   - ## Data Support
                   - ## Short Analysis
                   - ## Problem Diagnosis & Solutions
                   - ## Improvement Suggestions
                   - If useful, append `FOLLOW_UP_QUESTIONS:` at the end.
                   Do not change any required level-2 heading text, do not add icons, and do not change the Markdown heading level; otherwise the system cannot recognize the response.
                3. Use the grounded reference as the only source of truth. Do not change KPI values, dealer names, scope, or table facts, and do not introduce business data that is not in the reference.
                4. Do not mention internal planning, grounded references, tool names, or API names.
                5. `## Interface Call Chain` must preserve the factual execution steps from the grounded reference; do not change dates, scope, dealer codes, or data categories.
                6. `## Conclusion` should contain 2-4 bullets covering key findings, priority, or business takeaway with specific numbers. Each conclusion must be directly supported by the grounded reference.
                7. The `## Data Support` section must include an HTML <table> followed by the existing chart-json or chart-empty chart block from the grounded reference. Do not convert chart-json blocks into Mermaid.
                   Preserve the strict JSON exactly unless you are copying a validated value from the grounded reference.
                   Numeric comparison charts use {"type":"bar"}; opportunity funnel and lead source distribution use {"type":"pie"}.
                8. chart-json chart blocks MUST be wrapped inside ```chart-json and ``` fences. JSON must stay minified, valid, and double-quoted. Never output bare JSON objects outside a code fence.
                   Forbidden field names: labels, datasets. Bar type REQUIRES "categories" and "values" fields. Pie type REQUIRES "slices" field, each slice with "name" and "value".
                   Wrong (forbidden): {"type":"bar","labels":["A","B"],"datasets":[{"label":"x","data":[1,2]}]}
                   Correct: ```chart-json\n{"type":"bar","title":"Achievement Rate","categories":["Store A","Store B"],"values":[80,92],"metric":"Rate","metricType":"percentage"}\n```
                   Mermaid is only for free-form structural diagrams, not fixed numeric analytics charts.
                9. Keep all objective numbers and tables only in Data Support. Do not create any data overview, total note, summary block, or trailing recap after Data Support or at the end of the report.
                10. `## Short Analysis` should include:
                   - Data attribution: 2-3 points in [metric change] + [specific object] + [supported explanation or validation direction] format
                   - Actionable recommendations: 2-3 points in [action] + [target] + [expected outcome] format
                11. `## Problem Diagnosis & Solutions` should identify 1-2 largest gaps or bottlenecks:
                    - Format: [gap/bottleneck metric] + [specific object] + [evidence status] + [resolution action]
                    - Do not write an unproven assumption as a root cause. If evidence is insufficient, say what is missing or mark it as an operating assumption to validate.
                    - If all metrics are healthy (achievement rate >= 80%%), note stability and highlight the next-weakest indicator
                12. `## Improvement Suggestions` should branch by achievement level:
                    - If achievement rate >= 80%%: provide 2 suggestions on maintaining the advantage and validating transferable practices, without presenting one store's result as a universal playbook
                    - If achievement rate < 80%%: provide 2-3 phased suggestions, each with [action] + [target] + [expected outcome] + [timeframe (this month / next quarter / annual)]
                13. Follow-up strategy:
                    - If the answer is already complete, omit `FOLLOW_UP_QUESTIONS:`.
                    - If a missing business lens would change the conclusion, ask only the single most important question.
                    - If exploration would help, include at most 2 numbered questions, each tied directly to the specific dealers, metrics, lens, or data limitations analyzed. No vague generic questions.
                """.formatted(userMessage, sessionHistory, groundedReference);
    }

    public String buildConversationModelPrompt(String language, String userMessage, String sessionHistory) {
        if ("zh".equals(language)) {
            return """
                    当前用户问题：
                    %s

                    最近会话上下文：
                    %s

                    这是普通对话路径，不是带事实依据的经营分析报告路径。
                    要求：
                    1. 使用中文
                    2. 回答自然、简短、友好，优先直接回应用户当前问题
                    3. 如果用户是在问候、询问你是谁或介绍系统，请说明你是经销商 AI 分析助手，并提示可以询问目标达成、商机漏斗、销售跟进、市场活动、线索来源等业务问题
                    4. 不要使用经营分析报告的固定标题或结构，不要编造业务数据
                    5. 不要暴露隐藏推理过程
                    """.formatted(userMessage, sessionHistory);
        }

        return """
                Current user question:
                %s

                Recent conversation context:
                %s

                This is the general conversation path, not the grounded analytics report path.
                Requirements:
                1. Use English
                2. Keep the answer natural, brief, and helpful
                3. If the user greets you, asks who you are, or asks for a system introduction, say you are a dealer AI analytics assistant and suggest business questions such as target achievement, opportunity funnel, sales follow-up, campaigns, and lead sources
                4. Do not use the fixed analytics report headings or structure, and do not invent business data
                5. Do not reveal hidden reasoning
                """.formatted(userMessage, sessionHistory);
    }

    private String buildScenarioMatrix(String language) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (AnalyticsScenarioCatalog.ScenarioWorkflow workflow : AnalyticsScenarioCatalog.all()) {
            if (index > 1) {
                builder.append("\n");
            }
            builder.append(index)
                    .append(". ")
                    .append(workflow.label(language))
                    .append("\n");
            builder.append("   Example: ")
                    .append(workflow.examples(language).getFirst())
                    .append("\n");
            builder.append("   Tool chain: ")
                    .append(workflow.toolChainSummary())
                    .append("\n");
            builder.append("   Logic: ")
                    .append(workflow.logicSummary(language));
            index++;
        }
        return builder.toString();
    }

    private String loadSystemPromptTemplate(String language) {
        String resourceName = "zh".equals(language)
                ? "prompts/system-prompt-zh.txt"
                : "prompts/system-prompt-en.txt";

        try (InputStream inputStream = PromptFactory.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing system prompt resource: " + resourceName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read system prompt resource: " + resourceName, exception);
        }
    }
}
