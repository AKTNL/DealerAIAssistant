# P2-3A 报告订阅与调度定义

## Goal

允许有权限的用户为固定 tenant/组织范围创建、查看、启停和删除报告订阅，并把频率、时区、报告类型和收件目标保存为可审计的调度契约。

## Dependencies

* 前置：P2-2B tenant/组织隔离；现有 ReportService 继续作为生成事实源。
* 后续：P2-3B job 执行、P2-3C 投递、P2-3D 协作。

## Requirements

* 订阅记录 tenant、创建者、报告类型、组织 scope、语言、主题、频率/计划、时区、目标通道/收件人、启用状态和版本。
* 调度契约必须独立于具体 job/scheduler 引擎，并保留可扩展的 schedule kind，避免 P2-3A 提前绑定 P2-3B 的执行基础设施。
* 首版仅开放受控 `DAILY`、`WEEKLY`、`MONTHLY`：均指定本地执行时间和 IANA 时区；周频指定星期，月频指定 1–28 日，不接受 cron/RRULE 或小于一天的频率。
* 创建和修改时验证当前权限与范围；执行时重新验证用户/membership/范围，不依赖创建时权限快照。
* DST gap 顺延到该时区第一个有效时刻，DST overlap 使用较早 offset；首版错过窗口采用 `SKIP`，P2-3B 不得对超过 60 分钟宽限期的窗口补跑。
* 禁用用户/tenant、撤销 membership/权限、禁用订阅或软删除订阅时立即失去执行资格；启用或变更计划时从当前时刻重新计算下一执行时间。
* 收件目标首版保存 tenant 内用户标识和通道 key；外部地址/凭据由 P2-3C 在投递时解析，订阅记录不保存通道密钥。
* 提供管理 API/OpenAPI 和最小前端订阅入口，但不在此任务执行生成或投递。

## Acceptance Criteria

* [ ] 用户只能订阅自己有权生成的报告和组织范围。
* [ ] 无效时区、过高频率、越权收件人/范围和重复配置稳定拒绝。
* [ ] 权限或 membership 被撤销后，待执行订阅不再具备执行资格。
* [ ] 时区/DST/下次执行时间具有确定性测试。
* [ ] 所有变更有审计，API/前端/数据库回归通过。

## Decision (ADR-lite)

**Context**：首版需要可审计、可测试的日/周/月订阅，但当前尚未选择 P2-3B 的 job/scheduler 引擎。

**Decision**：采用独立于执行引擎的受控日/周/月日历契约，使用 Java `java.time` 和 IANA 时区计算下一执行时刻；首版不接受 cron 或 RRULE。

**Consequences**：首版不能表达日内多次和复杂工作日历；通过 schedule kind 保留后续新增受限 cron/RRULE 的兼容扩展点。

## Implementation Plan

1. 建立订阅领域契约、确定性调度计算、租户级持久化迁移与回归测试。
2. 建立复用实时 tenant/组织/权限校验和统一审计的管理服务、API 与 OpenAPI 契约。
3. 增加权限可见的最小前端订阅入口、状态处理和组件/API 测试。
4. 运行后端 PMD/测试与前端 lint/测试/build，并执行跨层契约检查。

## Research References

* [`research/schedule-contract-options.md`](research/schedule-contract-options.md) — 对比结构化日历重复、cron 和 RRULE，并结合现有 Java/Spring、报告、权限与审计边界建议首版采用可扩展的受控预设。

## Technical Notes

* 后端当前没有 Quartz/cron 依赖；首版可直接使用 Java 21 `java.time` 计算确定性的下一执行时刻。
* 复用 `ReportType`、`ReportService`、`AuthPrincipal`、`OrganizationAuthorizationService` 和 `AuthAuditService`，不建立旁路权限或报告生成链路。
* P2-3A 只持久化并管理调度契约；lease、重试、补偿和实际生成仍留给 P2-3B。

## Out of Scope

* job lease、重试和生成执行属于 P2-3B。
* 实际消息通道投递属于 P2-3C。
