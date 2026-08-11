# P2-3B 可靠任务执行

## Goal

把到期订阅转换为可持久化、幂等、可重试和可诊断的报告生成任务，在进程重启或多实例并发下不重复生成。

## Dependencies

* 前置：P2-3A 订阅/调度契约；P2-4A trace/job 事件规范最好先完成。
* 后续：P2-3C 投递和 P2-4C 作业告警。

## Requirements

* job 记录 subscription、scheduledAt、tenant/scope、状态、attempt、lease、nextRetry、错误代码和 traceId。
* 使用 `subscription + scheduled window` 幂等键，生成成功后重复领取返回同一结果或安全跳过。
* 多实例通过数据库 lease/锁或经 ADR 选择的调度设施保证单执行者；崩溃后 lease 可恢复。
* 执行前重新解析 tenant 授权上下文，调用现有 ReportService，不复制 KPI/报告逻辑。
* 失败采用有界退避重试，永久失败进入可查看/可重放状态；原始秘密和敏感内容不写错误字段。
* 定义暂停、取消、手工重试、错过窗口和 tenant 停用行为。

## Acceptance Criteria

* [ ] 重复扫描、并发实例和进程崩溃不会重复创建报告。
* [ ] 临时失败按策略重试，永久失败可诊断且不会无限循环。
* [ ] 权限/范围已失效的 job 在生成前拒绝并审计。
* [ ] 时钟、重启、lease 超时和幂等具有集成回归。
* [ ] 作业状态与 traceId 能关联订阅和生成报告。

## Technical Decision

* 优先评估 PostgreSQL 持久化 job + lease；只有吞吐/可靠性证据不足时再引入独立消息队列。

## Out of Scope

* 通道发送和回执属于 P2-3C。
* 不构建通用任务编排平台。

