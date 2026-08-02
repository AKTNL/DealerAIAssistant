# P1-2 架构模块化

## Goal

将当前按技术层组织的 POC 后端逐步整理为模块化单体，让每个新功能都能明确归属，同时降低 `ChatService` 和分析服务的职责耦合。P1-2 采用增量迁移，不进行一次性全量重写，不改变现有 HTTP API、SSE 协议、active batch 语义和分析准确率回归基线。

## What I already know

* 路线图要求完成模块边界文档、包结构规划、保持 Controller 不承载业务逻辑，并规划 `ChatService` 的编排、规则、模型、SSE、守卫子职责。
* 当前后端主要按 `controller/`、`service/`、`service/analytics/`、`ai/`、`entity/`、`repository/`、`dto/` 组织。
* 当前最大的职责聚合点是 `RuleBasedAnalyticsService`、`ExcelImportService` 和 `ChatService`；`ChatService` 同时处理同步聊天、SSE、规则分析、模型调用、会话记忆、语言识别、业务范围判断、进度事件和回复守卫。
* 已有可复用的分析切片：`AnalyticsCalculator`、`AnalyticsChartRenderer`、`AnalyticsReportComposer`、`AnalyticsTopicClassifier`、`DirectQuestionMatcher`、`ReportRenderer`。
* Controller 当前总体较薄，`AnalyticsApiController` 约 138 行；本次重点是服务边界和包结构，而不是大规模重写 Controller。
* P1-1 已引入 PostgreSQL/Flyway 持久化 profile；H2 继续保留给测试和 demo，因此本任务不能破坏现有启动 profile。

## Requirements

* 采用“模块化单体 + 按业务能力分包 + 增量迁移”的目标架构。
* 新增模块边界文档，至少覆盖：`auth`、`organization`、`dataimport`、`metrics`、`dashboard`、`analytics`、`agent`、`reporting`、`knowledge` 的职责、主要入口、允许依赖和当前迁移状态。
* 新增 Java 包结构规划，明确模块内推荐的 `controller`、`application`、`domain`、`infrastructure` 分层，以及过渡期仍存在的共享包。
* 明确依赖规则：Controller 只负责协议适配；跨模块调用通过应用服务/端口；领域规则不下沉到 Controller；新功能不得继续无理由放入根 `service` 包。
* 对 `ChatService` 给出职责拆分规划：编排、规则分析、模型适配、SSE 输出、会话上下文、回复守卫分别归属何处，并记录当前类到目标模块的迁移路线。
* 将一个低风险 ChatService 切片迁移到目标 `agent` 模块：优先迁移 `ChatReplyGuard`，保持行为、构造方式和现有聊天/SSE 输出不变。
* 为迁移切片补充或调整测试，使模块包路径和回复守卫行为有回归保护。
* 兼容现有外部 API、SSE 事件类型、active batch 查询和准确率回归测试。

## Acceptance Criteria

* [x] 文档中能根据功能找到唯一目标模块，并列出当前代码位置、目标包和迁移状态。
* [x] 包结构文档包含一个具体 Java 包树示例，并说明模块间依赖方向和禁止事项。
* [x] `ChatService` 的职责被拆成可执行的后续迁移清单；本次迁移的切片边界明确。
* [x] `ChatReplyGuard` 位于 `agent` 目标包，`ChatService` 通过模块边界引用它，原有 guard 行为测试通过。
* [x] 现有聊天同步接口、SSE 流式接口和相关测试通过；未引入新的 API 或 SSE 事件格式。
* [x] 后端编译、单元测试和项目质量检查通过。

## Definition of Done

* 代码和架构文档完成，且文档与实际包路径一致。
* 相关单元测试/回归测试通过。
* 按项目规范完成质量检查，并评估是否需要更新 `.trellis/spec/`。
* 变更按确认后的提交计划提交；不推送远程。

## Technical Approach

### Target module map

| Module | MVP responsibility | Current/target entry points |
| --- | --- | --- |
| `auth` | 登录、session、token、用户范围上下文 | `AuthController`、认证/session services |
| `organization` | 集团、区域、城市、经销商、门店范围 | dealer/organization entities and ports |
| `dataimport` | Excel/真实源导入、批次、质量报告 | `ExcelImportService`、`ImportBatchService` |
| `metrics` | KPI 定义、聚合、详情查询 | `DataQueryService`、指标 DTO/查询端口 |
| `dashboard` | Dashboard 汇总和入口 | `DashboardController`、`DashboardService` |
| `analytics` | 意图识别、分析计划、规则事实、分析报告 | `RuleBasedAnalyticsService`、`service.analytics` |
| `agent` | 聊天编排、模型适配、会话上下文、SSE、输出守卫 | `ChatService`、`ai`、`ChatReplyGuard` |
| `reporting` | 报告模板、生成、导出、历史 | 现有 renderer/composer 的后续归属 |
| `knowledge` | SOP/政策/口径/产品知识的检索接口 | P1 预留，不在本次实现完整 RAG |

### Incremental migration rules

1. 先建边界和目标包，再按低风险切片移动代码；每个切片保持可编译、可回滚。
2. 目标模块优先拥有自己的应用入口和内部实现；遗留根包仅作为过渡区，不扩展新职责。
3. DTO、实体和 repository 的全量归属迁移延后处理，避免在本任务同时改变持久化映射和数据库行为。
4. `ChatService` 本次只迁移输出守卫；规则分析、模型适配、SSE writer 和会话记忆保留原行为，后续按独立切片推进。
5. 不引入 Spring Modulith 或微服务拆分作为本次的运行时依赖；先用包边界和测试建立模块化约束，必要时后续再引入架构验证工具。

## Decision (ADR-lite)

**Context**: 当前代码规模已经出现超大服务类，但系统仍是单体、API 和测试稳定性要求较高。一次性重排全部包会同时放大编译、依赖、持久化和回归风险。

**Decision**: 采用按业务能力分包的模块化单体；模块内部保留清晰的应用/领域/基础设施分层；以 `agent.ChatReplyGuard` 作为第一个低风险迁移切片。

**Consequences**:

* 新功能归属更清晰，后续可逐步收紧跨模块依赖。
* 过渡期会同时存在目标模块包和遗留技术层包，需要文档标注迁移状态。
* 当前不获得运行时模块隔离；如果未来需要强边界，可在稳定后引入 Spring Modulith 的模块验证/事件能力。
* `ChatService` 仍会暂时偏大，但本次提供了可验证的拆分路线，避免将多个高风险变化混在一起。

## Out of Scope

* 不做一次性全量包迁移或微服务拆分。
* 不改变数据库 schema、Flyway 迁移、实体映射或 active batch 逻辑。
* 不改变现有 HTTP 路径、请求/响应 DTO、SSE 事件协议和模型 fallback 行为。
* 不在本任务实现完整组织权限、受控 Agent、RAG、报告导出或知识库。
* 不重写 `RuleBasedAnalyticsService`；仅记录后续拆分路线。

## Research References

* [`research/package-by-feature-modular-monolith.md`](research/package-by-feature-modular-monolith.md) — 对比技术分层、按业务能力分包和 Spring Modulith，并映射到本仓库约束。

## Technical Notes

* 主要代码根目录：`backend/src/main/java/com/brand/agentpoc/`。
* 主要入口：`controller/ChatController.java`、`service/ChatService.java`、`agent/ChatReplyGuard.java`。
* 相关规范入口：`.trellis/spec/backend/index.md`、`.trellis/spec/guides/cross-layer-thinking-guide.md`、`.trellis/spec/guides/code-reuse-thinking-guide.md`。
* 参考路线图：`docs/07-生产化升级路线图.md` 的“架构模块化”章节。
