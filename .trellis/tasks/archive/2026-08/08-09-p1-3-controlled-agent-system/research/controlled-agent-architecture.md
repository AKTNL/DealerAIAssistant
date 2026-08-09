# 受控 Agent 架构调研

## 调研问题

如何在当前 Spring Boot 3.4.5、Spring AI 1.0.0 和 P1-2 模块化单体约束下，实现可审计、可降级、不可越权的受控 Agent，同时复用既有 Dashboard、指标查询和规则分析能力。

## 仓库现状

- 路线图 `docs/07-生产化升级路线图.md` 将 M5 定义为：Agent 只能调用受控工具、回答可追溯、模型失败可降级；P1-3 建议的能力工具包括 Dashboard 摘要、指标、明细、规则场景、知识检索和报告草稿。
- P1-2 已规定 `agent -> analytics application ports / auth scope ports`，并要求新功能进入业务模块，不能继续扩展遗留根 `service` / `ai` 包。
- `ChatService` 当前先运行 `RuleBasedAnalyticsService.plan(...)` 生成 grounded facts 与 fallback，再由模型润色；模型失败或守卫失败时回退规则结果。这个链路已经满足确定性降级的核心条件。
- `AiConfig` 注册了 7 组底层 `@Tool` 查询对象，但 `ChatService` 的两处 `ChatClient` 调用都没有附加 `tools`、`toolCallbacks` 或 `toolNames`，所以现有聊天运行时并没有真正执行这些工具。
- 现有底层工具直接暴露 `DataQueryService` 的数据集查询，并允许 `raw=true`；它们只有必填参数校验，没有身份 scope、调用预算、分页上限或审计约束，不适合作为 P1-3 的公开 Agent 能力面。
- 当前认证只签发不透明 token subject，并在 `ChatController` 中做 session ownership；尚无角色、组织树或 subject-to-dealer 授权映射。业务查询服务均自动限定到 active import batch，这是当前可实际执行的数据 scope。完整账号/角色/组织权限属于路线图后续产品化阶段。
- `DashboardService.getSummary()`、`AnalyticsApiService` 的指标/明细接口、`RuleBasedAnalyticsService.plan(...)` 已提供 P1-3 所需的结构化业务入口，且都复用 active batch 语义。

## Spring AI 1.0.0 能力与约束

- 本地依赖 API 显示 `ChatClient.ChatClientRequestSpec` 支持按请求传入 `tools(...)`、`toolCallbacks(...)`、`toolNames(...)` 和 `toolContext(...)`，因此不必把所有 Bean 全局暴露给模型。
- `ToolCallback` 同时支持 `call(arguments)` 和 `call(arguments, ToolContext)`，可以在工具边界读取请求上下文并实现白名单、scope、预算、参数和审计策略。
- 对本地 `OpenAiChatModel` 1.0.0 字节码检查显示，内部工具执行会在模型继续返回 tool call 时递归调用 `internalCall(...)`；API 没有暴露明确的最大步数选项。因此仅依赖框架默认循环，不足以构成“受控”。

## 可行方案

### 方案 A：直接暴露现有底层 `@Tool`

模型通过 Spring AI 原生工具调用选择 `searchDealers`、`queryOpportunities` 等底层数据工具。

优点：改动少，能快速看到模型工具调用。

缺点：工具粒度与路线图能力不一致；`raw=true`、自由过滤和缺少 request scope 会放大数据暴露面；没有统一预算、审计与降级边界。不能满足 P1-3 验收。

### 方案 B：确定性路由，应用代码选择并执行工具

沿用当前 `RuleBasedAnalyticsService` 分类和规划，应用代码根据场景调用固定服务；模型只负责最终表达，不参与工具选择。

优点：最可控，降级成熟，测试稳定。

缺点：Agent 的自主工具选择能力很弱；新增组合问题需要不断扩展硬编码规则，不能充分体现路线图中的受控 Agent。

### 方案 C：受控能力门面 + 模型工具选择 + 确定性降级（推荐）

在 `agent` 模块建立业务级工具注册表和执行策略。每个请求只向模型暴露获准的业务能力；工具执行前统一验证工具名、参数、active-batch scope、分页/结果上限和调用预算，执行后记录最小可追溯信息。模型或工具链失败时，立即回到现有规则分析结果。

优点：兼顾 Agent 组合能力和确定性安全；工具面与路线图一致；未来 RAG、报告和组织权限只需新增 port/policy，不需要重写 ChatController 或底层查询。

缺点：比直接接入 `@Tool` 多一层门面和策略测试；Spring AI 默认递归工具循环仍需通过包装回调/执行上下文显式限制。

## 建议的 MVP 边界

- 首批可执行工具：`getDashboardSummary`、`queryMetric`、`queryDetails`、`runScenarioAnalysis`。
- `retrieveKnowledge` 与 `generateReportDraft` 只保留扩展位，不向模型注册；它们分别依赖后续 RAG 和报告阶段，提前伪实现会模糊失败语义。
- P1-3 不新增写工具；所有能力只读，不直接访问 repository，不接受 SQL，不允许选择 import batch。
- 当前 scope 定义为“已认证且拥有当前 session 的请求 + active import batch”。PRD 和回复限制必须明确说明尚未实现组织/角色级数据隔离。
- 每次请求使用固定白名单与小调用预算；明细必须强制分页并限制 page size；无效工具名、参数、越界页大小或超预算调用均拒绝。
- 保留现有规则分析作为 model unavailable、tool failure、empty/invalid answer 的统一 fallback；回复继续携带 `AnalyticsMetadata` 中的数据源、限制和置信度。

## 影响路径

- 新代码应归属 `backend/src/main/java/com/brand/agentpoc/agent/{application,domain,infrastructure}/`。
- 复用入口：`DashboardService`、`AnalyticsApiService`、`RuleBasedAnalyticsService`；若跨模块边界需要收紧，应以 application port 适配，不在 Agent 内直接注入 repository。
- 兼容入口：`ChatService` 与 `ChatController` 的 HTTP/SSE 协议保持不变；优先把受控执行作为 `ChatService` 的内部依赖切片。
- 关键测试：白名单、scope、预算、分页上限、工具参数、fallback、同步/SSE 回归，以及旧底层工具未被 Agent 注册。

## 结论

采用方案 C。它符合 P1-2 模块边界与路线图 M5，同时最大化复用当前确定性分析和降级能力。P1-3 应先实现四个只读业务工具；知识检索和报告草稿保留为后续可插拔能力。
