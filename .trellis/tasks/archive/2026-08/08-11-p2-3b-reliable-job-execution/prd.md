# P2-3B 可靠任务执行

## Goal

把到期订阅转换为可持久化、幂等、可重试和可诊断的报告生成任务，在进程重启或多实例并发下不重复生成。

## Dependencies

* 前置：P2-3A 订阅/调度契约；P2-4A trace/job 事件规范最好先完成。
* 后续：P2-3C 投递和 P2-4C 作业告警。

## Requirements

* job 持久化 subscription、scheduledAt、tenant/scope、状态、attempt、lease、nextRetry、错误代码、traceId 和生成结果 draft ID。
* 使用 `subscription_id + scheduled_at` 作为唯一幂等键；重复扫描通过数据库唯一约束安全跳过，成功后的重复领取返回已有 job/result，不再次调用报告生成。
* 多实例通过 PostgreSQL/H2 行级锁和短 lease 保证单执行者；进程崩溃后由 lease 超时恢复为可领取状态，不能依赖 JVM 内存锁。
* 到期物化和订阅 `next_run_at` 推进在同一事务内完成；使用 P2-3A 的 `SKIP` 与 60 分钟 grace，超过 grace 创建可审计的 `SKIPPED/MISSED_WINDOW` job。
* 执行前重新解析 tenant 成员、租户启用状态、创建者 `REPORT_GENERATE`、组织范围和收件人资格，调用现有 `ReportService.generate(ReportGenerationRequest, OrganizationDataScope)`，不复制 KPI/报告逻辑。
* 首次执行失败后最多重试 3 次，固定退避为 5 分钟、30 分钟、2 小时；临时失败进入 `RETRY_WAIT`，达到上限或确定性权限/校验失败进入 `PERMANENT_FAILURE`，都可查看和人工重放。
* 禁用订阅会取消尚未领取的 job；执行前再次发现订阅/租户停用时安全跳过并记录固定错误码；已领取的 job 不绕过动态权限检查。
* 错误字段只保存受控错误代码和安全 trace ID，不保存原始异常、token、收件人地址、提示词或报告正文；所有状态迁移写 tenant-scoped 审计事件。

## Acceptance Criteria

* [ ] 重复扫描、并发实例和进程崩溃不会重复创建 job 或报告。
* [ ] 临时失败最多重试 3 次并按 5m/30m/2h 退避，永久失败可诊断、可人工重放且不会无限循环。
* [ ] 权限/范围、收件人、订阅或租户已失效的 job 在生成前拒绝并审计。
* [ ] 时钟推进、重启、lease 超时、错过窗口和幂等具有集成回归。
* [ ] 作业状态、traceId、subscription 和生成报告 draft ID 可相互关联。

## Decision (ADR-lite)

**Context**：P2-3A 已定义确定性的日/周/月订阅，但项目尚未引入 Quartz 或消息队列；P2-3B 需要跨进程单执行和崩溃恢复。

**Decision**：采用 PostgreSQL/H2 持久化 job + `(subscription_id, scheduled_at)` 唯一幂等键 + JPA 行级锁/lease。使用显式 service runner 进行扫描、领取和执行，保留后续接入外部 scheduler 的边界；不在本任务引入新基础设施。

**Consequences**：数据库承担调度状态和短期协调，吞吐受批量和锁竞争限制；后续可把扫描触发器替换为 Quartz/队列而不改变 job 状态和幂等契约。

## Implementation Plan

1. 增加 V9 job/状态/索引/幂等约束和 JPA entity/repository，补充启动迁移回归。
2. 实现到期订阅物化、错过窗口处理、lease 领取/恢复、有限退避和安全错误分类。
3. 实现动态授权执行、报告生成结果关联、审计、取消和人工重放服务；必要时提供只读/重放管理 API。
4. 添加并发、时钟、重启、权限撤销和跨层集成测试，运行后端质量门禁。

## Research References

* [`research/reliable-job-execution-options.md`](research/reliable-job-execution-options.md) — 对比数据库 job+lease、Quartz 集群和消息队列，结合当前依赖与 P2-3A 契约选择数据库方案。

## Technical Decision

* 采用研究结论中的 PostgreSQL/H2 job + lease；默认 lease 5 分钟，首次执行后最多重试 3 次，退避为 5 分钟、30 分钟、2 小时。
* 任务 runner 与触发器解耦，P2-3B 不引入 Quartz、消息队列或通用编排平台。

## Acceptance Status

- [x] Duplicate scans, concurrent workers, and lease recovery are covered by database uniqueness, row locks, and regression tests.
- [x] Transient failures use bounded 5m/30m/2h backoff with a four-attempt ceiling; terminal jobs support manual replay.
- [x] Tenant, subscription, creator permission, organization scope, and recipient eligibility are reloaded before generation and audited with fixed codes.
- [x] Clock advancement, restart recovery, misfire skipping, and cross-layer authorization are covered by migration, service, runner, controller, and HTTP tests.
- [x] Job state, trace ID, subscription, and generated draft ID are persisted and exposed without sensitive payloads.

## Out of Scope

* 通道发送和回执属于 P2-3C。
* 不构建通用任务编排平台。
