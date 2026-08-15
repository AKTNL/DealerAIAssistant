# P2-4C 健康检查、告警与性能

## Goal

让运行环境能区分进程存活、应用可接流量和关键依赖降级，并对慢查询、导入/订阅失败、模型异常和容量风险发出可执行告警。

## Dependencies

* 前置：P2-4A metrics/trace 规范。
* 消费：P2-3B/P2-3C job 与投递状态、P2-4D 部署检查。

## Requirements

* liveness 只表示进程状态；readiness 检查数据库、迁移状态和关键启动依赖，不因可选模型短暂失败导致重启风暴。
* 对 PostgreSQL/PGvector、知识索引、模型 provider、导入、报告 job、投递通道定义健康/降级语义。
* 采集 HTTP/SSE 延迟、错误率、连接池、慢查询、模型耗时、job backlog/retry 和导入耗时。
* 每条告警包含影响、阈值、诊断链接和 runbook；设置去抖、恢复通知和噪声控制。
* 建立容量与性能基线，覆盖 Dashboard、数据详情、Agent 查询和报告生成的代表性负载。

## Acceptance Criteria

* [x] 编排平台能通过 liveness/readiness 正确停止流量而非循环重启。
* [x] 数据库不可用、模型降级、job 积压、投递持续失败和慢查询均能触发预期告警。
* [x] 告警恢复、抑制和重复事件不会形成通知风暴。
* [x] 性能测试给出基线、阈值和最慢路径，不泄露测试业务数据。
* [x] 每个 P1/P2 关键依赖都有诊断步骤和责任边界。

## Delivered Design

* `/livez` 只包含 `livenessState`；`/readyz` 只包含 `readinessState,db,migration,knowledge`。
* 模型、导入、报告 job 与投递失败通过指标、`DEGRADED` 综合健康和告警处理，均不触发 readiness/liveness 重启。
* repository 调用耗时、慢查询、模型调用耗时和队列状态仅使用 `app.component`、`app.outcome` 低基数标签。
* Prometheus 规则覆盖 readiness、HTTP/SSE、慢查询、模型、导入、job、投递、连接池和 JVM；Alertmanager 模板提供聚合、抑制、重复间隔和 resolved 通知。
* Node 性能工具覆盖 Dashboard、机会详情、Agent 确定性查询与报告生成，不保存响应正文、凭据、tenant 标识或报告内容。

## Verification

* 后端全量测试：519 passed；PMD passed。
* 前端：lint passed；265 tests passed；production build passed。
* 实际启动检查：`/livez`、`/readyz`、`/actuator/prometheus` 均返回 200，导出的 HTTP、repository 与 queue 指标名/标签和告警规则一致。
* 本地 H2 基线：20 秒、并发 2、全部错误率 0%；Dashboard p95 230.264ms，数据详情 46.185ms，Agent fallback 54.603ms，报告生成 533.076ms。
* 最慢路径为报告生成；该结果只用于本地回归，生产容量必须在 PostgreSQL/PGvector 预发环境复测。

## Out of Scope

* 不在本任务承诺 24x7 值班组织或采购监控产品。
* 不用健康检查执行破坏性修复。
