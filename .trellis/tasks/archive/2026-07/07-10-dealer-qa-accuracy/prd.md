# 改进经销商问答准确性

## Goal

提升经销商 AI 分析助手在样本数据问答中的准确性和可信度，重点解决规则模板过度确定、prompt 证据约束不足、业务口径缺失，以及固定两个追问导致的机械感。目标不是让回答更花哨，而是让答案明确区分数据事实、可支持判断、推测和需要补充的信息。

## What I Already Know

* 用户认为当前问题主要集中在三类：规则模板把答案包装得过于确定；prompt 证据约束弱，证据不足时模型仍可能硬答；业务口径缺失，系统不知道经销商指标应按什么规则解释。
* 用户也怀疑“固定追问两个问题”需要改进。初步判断成立：追问应由证据充分度和用户问题缺口驱动，而不是固定输出两个。
* `backend/src/main/resources/Sample Data.xlsx` 包含 5 张 Salesforce 风格业务表：
  * `AE Target Data`：5,088 行，目标数据覆盖 2024-01 到 2026-12，40 个经销商。
  * `Opportunity`：6,198 行，商机覆盖 2025-01-03 到 2026-05-31，赢单数 678。
  * `Lead`：1,898 行，线索覆盖 2026-01-01 到 2026-05-31；438 条没有经销商，866 条没有车型。
  * `Task`：57,582 行；其中 12,528 条任务无法关联到当前 `Opportunity` 表。
  * `Campaign`：715 行；多个目标/车型字段为空，`NumberOfLeads` 和 `NumberOfConvertedLeads` 汇总为 0。
* 同一个业务短语在数据中有多种合理口径，例如“表现最好”可能按目标、商机数、赢单数、活动数、跟进任务数或转化率判断。若不说明口径，直接给唯一结论容易误导。
* `mockservice/DealerAIAssistant_准确率测试题库.xlsx` 有 51 条测试题，评分重点包括关键数字、排名、经销商名称、异常处理。任何改动都必须保住题库回归。
* 当前架构已是“规则优先 + 可选外部模型润色”：
  * `RuleBasedAnalyticsService` 先生成确定性 `fallbackReply` 和 `groundedReference`。
  * `PromptFactory.buildGroundedModelPrompt(...)` 要求模型基于参考事实重写。
  * `ChatReplyGuard` 校验模型回复结构和数据表，不通过则回退规则报告。
* 当前固定追问约束分布在多处：
  * `PromptFactory` 明确要求 `追问：` / `FOLLOW_UP_QUESTIONS:` 必须且只包含 2 个编号追问。
  * `ChatReplyGuard.hasExactlyTwoFollowUpQuestions(...)` 要求恰好两行，并在缺失时自动补两个。
  * `AnalyticsReportComposer` 和多个规则分析分支都会固定追加追问。
* 前端追问 UI 比后端宽松：
  * `FollowUpButtons.vue` 按数组长度渲染，不强制两个。
  * `frontend/src/utils/chat.js` 可返回空数组，也会去重；目前最多截取两个。

## Assumptions

* 这轮优先改后端问答可信度，不重做前端界面。
* 外部模型仍只作为润色层，事实仍来自规则分析和数据聚合。
* 不引入新的 LLM 供应商或向量检索系统。
* 业务口径先覆盖现有 6 大分析场景和准确率题库，不追求一次性覆盖所有自然语言表达。

## Requirements

* 本轮采用 Approach C：后端可信度改造 + 前端展示“口径/限制”信息。
* 建立或内置一层业务口径约束，至少覆盖：
  * 目标达成率：`won / target`，必须说明目标、赢单、时间范围和聚合维度。
  * 商机转化：必须说明分子分母和阶段范围，避免把概率或阶段名当成真实转化原因。
  * 线索转化：必须处理缺失经销商、缺失车型、固定购买周期等数据限制。
  * 市场活动效果：若转化字段全 0 或目标字段缺失，不能下“活动无效”结论，只能说明当前字段不足。
  * 销售跟进：必须说明 `Task` 与 `Opportunity` 的关联覆盖限制。
  * “表现最好/最差”：必须根据明确指标作答；若用户未给指标，应使用默认口径并说明，或追问一个最关键口径。
* 调整模型 prompt：
  * 强化“只能基于 grounded reference 回答”。
  * 禁止把推测写成事实。
  * 允许使用“可能”时必须标明证据不足或仅为运营假设。
  * 遇到字段缺失、分母为 0、关联不足、样本量过小，应直接说明限制。
* 调整规则报告模板：
  * 减少固定“根因”“诊断”“复制打法”等过度确定措辞。
  * 把无法证实的原因改为“可进一步验证的方向”。
  * 对低置信度场景优先输出数据质量说明，而不是完整经营建议。
* 调整追问策略：
  * 不再固定要求所有回复必须有且只有两个追问。
  * 分析类回答可输出 0-2 个追问。
  * 如果缺少关键口径且会影响结论，最多追问 1 个最关键问题。
  * 如果当前答案已经完整，允许不追问。
  * 如果需要提供探索方向，最多给 2 个且必须与当前数据/口径直接相关。
* 保持现有图表和 Markdown 契约不破坏：
  * 结构化分析报告仍保留前端可解析的标题、HTML table、chart-json/chart-empty。
  * 模型润色失败仍必须回退到规则报告。
* 前端需要在助手消息中展示本次分析的口径和数据限制：
  * UI 采用消息顶部提示条，显示在每条分析回复正文上方。
  * 至少展示分析范围、主指标口径、数据来源、关键限制。
  * 对低置信度或字段不足场景，限制说明必须比经营建议更醒目。
  * 展示内容应来自后端结构化输出或可稳定解析的报告区块，避免前端自行推断业务含义。

## Acceptance Criteria

* [ ] “表现最好/最差”类问题的回答明确说明口径，不能把单一指标包装成综合表现。
* [ ] 对活动、线索、任务关联等数据不足场景，回答明确说明字段或关联限制，不输出未经证实的原因归因。
* [ ] 分析 prompt 明确要求区分事实、可支持判断、运营假设和数据不足。
* [ ] 后端允许分析回复包含 0、1 或 2 个追问，并保持前端解析正常。
* [ ] 如果需要追问，只问一个最影响结论的问题；不再为了凑数补两个泛泛问题。
* [ ] 前端助手消息能展示“口径/限制”信息，用户无需读完整报告即可看到本次分析依据。
* [ ] 当活动转化、线索车型、任务关联等字段不足时，前端能明确显示限制提示。
* [ ] 准确率题库回归继续通过，关键数字和排名不退化。
* [ ] 相关单元测试覆盖：prompt 文案约束、追问数量策略、低证据场景的保守回答、模型润色校验回退。

## Definition of Done

* Tests added/updated for backend prompt, guard, rule report, and regression workbook where applicable.
* Backend tests pass for affected service tests and accuracy workbook regression.
* Frontend tests only在改到解析逻辑时才需要更新。
* 不引入新的外部依赖。
* 若形成稳定业务口径，记录到项目 spec 或文档，便于后续维护。

## Out of Scope

* 不接入真实生产 CRM 或外部数据库。
* 不新增向量检索、RAG 文档库或多模型评测平台。
* 不重做聊天 UI。
* 不改变 Excel 样本数据本身。
* 不追求解释所有汽车行业业务术语，只覆盖当前数据和题库支持的口径。

## Technical Notes

* 当前核心后端文件：
  * `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
  * `backend/src/main/java/com/brand/agentpoc/service/analytics/AnalyticsReportComposer.java`
  * `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java`
  * `backend/src/main/java/com/brand/agentpoc/service/ChatReplyGuard.java`
  * `backend/src/main/java/com/brand/agentpoc/service/ChatService.java`
* 当前关键测试文件：
  * `backend/src/test/java/com/brand/agentpoc/service/AccuracyWorkbookRegressionTest.java`
  * `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`
  * `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java`
  * `backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java`
* 前端追问解析相关：
  * `frontend/src/utils/chat.js`
  * `frontend/src/components/chat/FollowUpButtons.vue`
* SSE/消息状态观察：
  * `SseEventWriter` 已支持任意 `event` 名称和多行 `data`。
  * `useSseParser` 已支持 JSON 事件数据。
  * `useChat` 当前处理 `step` / `progress` / `message` / `error` / `done`，可扩展处理 `analysis_metadata`。
  * `AssistantMessage.vue` 可基于 `message.analysisMetadata` 渲染顶部提示条。
* 已检查的样本数据：
  * `backend/src/main/resources/Sample Data.xlsx`
  * `mockservice/SampleData/Sample Data - 星曜汽车.xlsx` 与主样本大小一致。
  * `mockservice/DealerAIAssistant_准确率测试题库.xlsx`

## Feasible Approaches

### Approach A: 后端可信度 MVP（Recommended）

只改后端 prompt、规则报告措辞、追问 guard 和测试。前端不动或只补非常小的解析测试。优点是范围可控，直接解决当前不准确和机械追问问题；缺点是业务口径仍主要散落在代码常量和规则逻辑中。

### Approach B: 业务口径注册表 + 后端可信度

新增一个集中式业务口径注册表，让每个场景的指标、公式、限制、默认口径、追问策略都有统一配置。优点是长期维护更清楚；缺点是改动更大，可能需要重构 `RuleBasedAnalyticsService` 多个分支。

### Approach C: 后端可信度 + 前端展示“口径/限制”标签

在回答内容之外，前端显式展示口径、数据限制、置信度标签。优点是用户体验更透明；缺点是涉及 SSE/消息结构/UI，超出当前主要痛点。

## Decision (ADR-lite)

**Context**: 当前回答准确性问题不仅来自后端规则和 prompt，也来自用户无法快速判断“答案按什么口径算、哪些数据不完整”。如果只改报告正文，用户仍需要阅读长报告才能发现限制条件。

**Decision**: 采用 Approach C。本轮同时改后端可信度策略和前端口径/限制展示。

**Consequences**: 改动范围会覆盖 backend + frontend，需要维护 SSE/消息解析契约和更多测试；收益是回答透明度更强，能减少“模板看起来很确定但证据不够”的体验问题。

## UI Decision

前端采用消息顶部提示条展示口径和限制。提示条出现在分析回复正文上方，优先显示简短、可扫读的信息，例如：

* 口径：按赢单数排名 / 目标达成率 = 赢单数 ÷ 目标数
* 范围：全量数据 / 2026 年 5 月 / 指定经销商
* 数据来源：AE Target Data、Opportunity、Lead、Task、Campaign
* 限制：活动转化字段不足、部分线索缺经销商、部分任务无法关联当前商机

## Transport Decision

后端到前端采用结构化 SSE 事件，而不是解析 Markdown 正文。

* 新增事件名：`analysis_metadata`
* 数据格式：JSON
* 建议字段：
  * `scopeLabel`：本次分析范围
  * `metricLens`：主指标/公式口径
  * `dataSources`：涉及的数据表或业务实体
  * `limitations`：数据限制或质量提示
  * `confidence`：`high` / `medium` / `low`
* 触发时机：分析计划生成后、正文 `message` 事件前发送。
* 适用范围：仅分析类回复展示；普通寒暄、越界问题、模型配置提示不展示。
* 降级行为：如果前端未收到该事件，仍正常渲染 Markdown 正文和追问。

## Open Questions

* 等待用户确认 PRD 是否可以进入实现阶段。

## Implementation Plan

* Backend 1: 增加分析元信息模型和 SSE `analysis_metadata` 输出，把场景、范围、指标口径、数据源、限制、置信度从规则分析计划传给前端。
* Backend 2: 调整 prompt、规则报告和 guard，使回答保守、可证据化，并允许 0-2 个追问。
* Frontend 1: 在 `useChat` 中接收 `analysis_metadata`，挂到当前助手消息。
* Frontend 2: 在 `AssistantMessage.vue` 中渲染消息顶部口径/限制提示条，并补充样式和测试。
* Quality: 更新后端服务测试、prompt 测试、准确率题库回归，以及前端消息渲染/解析测试。
