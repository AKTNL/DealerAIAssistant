# P2-4B 模型使用与成本治理

## Goal

记录并归集每次模型/embedding 调用的次数、耗时、token 和估算费用，为用户、tenant、场景和模型提供可审计的用量视图与预算治理。

## Dependencies

* 前置：P2-4A 观测事件；P2-2B tenant 上下文。
* 后续：P2-4C 用量告警、P2-4D 运维收口。

## What I Already Know

* 模型调用集中在 `ChatService` 创建的 Spring AI `ChatModel`，底层 `OpenAiChatModel` 已配置统一 `RetryTemplate` 与 `ObservationRegistry`。
* Spring AI 1.0 的 `ChatResponseMetadata.getUsage()` 可提供 prompt/completion/total token；流式 provider 可能只在末尾 chunk 返回累计用量，也可能完全不返回。
* tenant 模型配置已持久化 provider 兼容端点和模型名，但目前没有价格、用量或预算数据模型。
* 报告与后台订阅首版使用确定性渲染，不应伪造模型用量；场景目录仍需预留这些未来调用类型。
* 当前没有独立平台身份模型；平台汇总首版以“默认平台 tenant + 独立权限”双重条件授权。

## Decision (ADR-lite)

**Context**: 首版既要形成可审计成本基线，又不能因 provider 元数据差异或治理组件故障中断现有模型能力。

**Decision**: 在 `ChatModel` 外层按逻辑 provider 调用归集最终响应元数据。底层自动重试仍只形成一条用量事件，Agent 多轮模型调用各自形成事件；流式响应只保留最后/最大累计 token，不逐 chunk 相加。费用在写入时绑定不可变价格版本。默认启用观测和软阈值，硬限制独立配置且默认关闭；启用时通过带过期时间的预留和数据库锁控制并发，治理故障按策略 fail-open。

**Consequences**: token 或价格缺失时事件仍保存但费用明确为未知；首版成本是目录估算而非财务账单。默认平台 tenant 的授权管理员可看跨 tenant 汇总，其他 tenant 即使拥有租户管理权限也不能进入平台汇总。

## Requirements

* 使用调用元数据记录 provider/model、场景、tenant/user、input/output token、耗时、状态和 traceId；不保存秘密或默认保存完整 prompt/output。
* 建立版本化价格目录，费用计算保留币种、价格版本和估算/实际来源。
* 区分聊天、Agent、知识 embedding/retrieval、报告和后台订阅场景。
* 提供时间范围聚合、异常用量和高成本场景 API/管理视图。
* 定义缺失 token 元数据、重试、流式调用、缓存命中和 provider 账单差异处理。
* 预算策略先支持可配置软阈值；是否启用硬拒绝/熔断作为独立开关并失败安全。
* 价格目录、预算和原始事件只保存治理元数据，不保存 prompt、completion、API key、Base URL 或工具参数。
* 租户管理入口使用独立用量权限；跨 tenant 平台汇总还必须来自默认平台 tenant，并记录审计事件。

## Acceptance Criteria

* [x] 同一次重试或流式调用不会重复计费，缺失元数据明确标记为未知。
* [x] 用户、tenant、场景、模型和日期聚合与原始事件可对账。
* [x] tenant A 无法查看 tenant B 用量，平台汇总入口独立授权并审计。
* [x] 价格更新不改写历史费用计算依据。
* [x] 软预算告警和可选硬限制具有边界/并发回归。
* [x] 租户管理员可在管理视图筛选时间范围并查看总览、高成本场景、异常和最近事件。

## Technical Approach

* 新建 `modelusage` 模块，按 domain/application/infrastructure/controller 分层；原始事件、价格版本、预算策略和短期预算预留使用独立表。
* `ModelUsageTracker` 装饰 `ChatModel`，同步调用在返回后记录一次，流式调用按 subscription 聚合元数据并在 complete/error/cancel 时只结算一次。
* 聚合 API 默认按当前 `AuthPrincipal.tenantId` 查询；平台 API 需要 `MODEL_USAGE_PLATFORM_READ` 且 tenantId 为默认平台 tenant。
* 费用使用定点小数和 ISO 货币代码；事件固化价格版本、输入/输出单价和估算来源，后续价格更新只追加版本。
* 预算按自然月和币种管理；软阈值只改变状态，硬限制在开启后用每次调用预留额控制并发，预留超时可回收。

## Research References

* [`research/model-usage-governance-design.md`](research/model-usage-governance-design.md) — Spring AI 用量元数据、调用边界、定价快照、预算并发和现有仓库接入点。

## Definition of Done

* 后端单元/集成测试覆盖 token 缺失、流式累计、价格历史、租户隔离、平台授权、软阈值和硬限制并发边界。
* 前端 API/组件测试覆盖管理视图加载、筛选、预算与价格保存。
* Maven 测试、PMD、前端 lint/test/build 通过。
* OpenAPI、配置说明和成本治理运行手册同步更新，并写明 provider 账单对账与回滚方式。

## Out of Scope

* 不实现财务开票或 provider 账单支付。
* 不记录完整用户 prompt 作为成本治理依据。
* 不在首版自动抓取 provider 价格或账单，也不把估算费用宣称为实际结算金额。
* 不把当前确定性报告/订阅渲染记为模型调用；仅保留场景枚举供未来接入。
