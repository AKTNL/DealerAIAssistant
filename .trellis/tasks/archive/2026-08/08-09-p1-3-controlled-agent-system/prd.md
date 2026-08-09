# P1-3 受控 Agent 系统

## Goal

在 P1-2 架构模块化成果之上，按照既定路线图实现受控 Agent 系统，使对话能力通过清晰边界、显式策略和可验证约束安全地执行工具或业务动作。

## Requirements

- 与现有对话链路和模块化架构集成。
- 采用“业务级受控工具门面 + 模型工具选择 + 统一策略校验 + 规则分析 fallback”，不引入独立 Agent 框架。
- 首批只注册四个只读业务工具：`getDashboardSummary`、`queryMetric`、`queryDetails`、`runScenarioAnalysis`。
- 新增业务级受控能力门面；不得把现有底层 `raw` 数据查询工具直接暴露给 Agent。
- 每次请求只暴露固定白名单内的只读工具，并统一执行工具名、参数、active-batch scope、分页/结果上限和调用预算校验。
- 当前授权边界为：请求已认证、token subject 拥有当前 session，所有业务数据只来自 active import batch。完整组织/角色数据隔离不在本任务伪实现。
- 明细工具必须使用分页接口并限制 page size；工具不得接受 SQL、repository 名称、任意数据集或 import batch 参数。
- 模型不可用、工具执行失败、调用超预算或最终回复守卫失败时，返回现有规则分析结果。
- 回答包含结论、数据支撑、限制、置信度和相关追问，并通过现有分析元数据/服务端 trace 保留最小可追溯信息。
- 同步聊天与 SSE 流式聊天使用同一受控能力和降级语义。
- 不改变现有 HTTP API、ChatRequest/ChatResponse 或 SSE 事件协议。

## Acceptance Criteria

- [x] 四个业务工具均通过 Agent 业务门面调用现有应用服务，不直接访问 repository 或拼接 SQL。
- [x] 每个模型请求仅获得四个白名单工具；现有 `searchDealers`、`queryOpportunities` 等底层工具不注册到 Agent 链路。
- [x] 未知工具、无效指标/明细类型、非法参数、越界 page size 和超预算调用被明确拒绝并有测试覆盖。
- [x] Agent 无法指定 import batch，查询结果继续遵守 active batch 语义；session ownership 回归测试通过。
- [x] 模型不可用、工具失败、超预算或回复不合规时仍返回现有规则分析结果。
- [x] 回答保留数据源、限制、置信度和追问；服务端 trace 至少记录工具名、成功/拒绝状态和不含敏感参数的原因。
- [x] 同步接口、SSE 事件类型/顺序、现有分析准确率与 fallback 回归测试保持通过。
- [x] 后端编译、PMD、单元/集成测试通过；若前端无变更，则验证现有契约无需前端修改。

## Definition of Done (team quality bar)

- Tests added/updated (unit/integration where appropriate)
- Lint / typecheck / CI green
- Docs/notes updated if behavior changes
- Rollout/rollback considered if risky

## Out of Scope (explicit)

- 写操作或可产生业务副作用的 Agent 工具。
- 自由 SQL、任意 repository/Bean 调用或跨 active batch 查询。
- 完整账号、角色、组织树和多租户授权模型。
- `retrieveKnowledge` 与完整 RAG；仅保留后续可增加工具的扩展边界。
- `generateReportDraft`、报告导出和历史；仅保留后续可增加工具的扩展边界。
- 一次性重写 `ChatService`、更改 HTTP/SSE 协议或引入独立 Agent 框架。

## Technical Approach

1. 在 `agent.domain` 定义工具标识、请求 scope、调用预算和拒绝原因等不依赖 Spring 的领域约束。
2. 在 `agent.application` 建立四个业务能力的注册表/执行器，通过公开应用服务复用 Dashboard、指标、分页明细和规则分析。
3. 在 `agent.infrastructure` 适配 Spring AI request-scoped `ToolCallback`，只发布白名单工具，并把 session、active-batch scope、预算与 trace 传入工具上下文。
4. 将受控执行作为 `ChatService` 的增量依赖切片；同步与 SSE 共享相同策略，保留当前规则分析计划作为统一 fallback。
5. 以单元测试覆盖策略与工具映射，以聊天/Controller 回归覆盖 session ownership、fallback、回复元数据和协议兼容。

## Decision (ADR-lite)

**Context**: 路线图建议六个 Agent 工具，但知识检索和报告草稿分别依赖后续 RAG、报告阶段；当前仓库已有底层查询工具，却缺少 request scope、预算和统一审计边界。

**Decision**: 采用受控业务工具门面方案。P1-3 只实现四个已有业务服务支撑的只读工具；知识检索与报告草稿不注册、不伪实现，只保留可扩展注册边界。

**Consequences**:

- P1-3 可以独立达到 M5 的白名单、可追溯和 fallback 基线，不吞并后续里程碑。
- 当前 scope 只能保证 session ownership 与 active batch，不能宣称已具备组织/角色权限；这一限制会进入回复元数据和文档。
- 未来新增 RAG、报告或组织权限时，需要实现新的 application port/policy，但无需改变外部聊天协议。

## Implementation Plan

- 切片 1：领域策略、白名单、预算、scope/trace 模型及单元测试。
- 切片 2：四个业务工具门面、参数/分页限制和现有应用服务适配测试。
- 切片 3：Spring AI 与 `ChatService` 同步/SSE 集成、fallback 和回归测试。
- 切片 4：架构文档/规范更新、全量质量门禁和提交计划。

## Technical Notes

- 任务目录：`.trellis/tasks/08-09-p1-3-controlled-agent-system/`
- 推荐架构：业务级受控工具门面 + 模型工具选择 + 统一策略校验 + 现有规则分析 fallback。
- 目标代码包：`com.brand.agentpoc.agent.application`、`agent.domain`、`agent.infrastructure`。
- 复用入口：`DashboardService.getSummary()`、`AnalyticsApiService` 指标/明细方法、`RuleBasedAnalyticsService.plan(...)`。
- Spring AI 1.0.0 支持 request-scoped tool callbacks/context，但默认内部工具调用循环没有显式最大步数 API，必须由受控层补充预算。

## Research References

- [`research/controlled-agent-architecture.md`](research/controlled-agent-architecture.md) — 对比三种 Agent 接入方式并推荐受控能力门面方案。
