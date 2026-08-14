# P2-3B 可靠任务执行方案研究

## 研究范围

本任务需要把 P2-3A 的到期订阅转换为可恢复、幂等、可诊断的报告生成 job。当前后端已有 Spring Data JPA、Flyway、PostgreSQL/H2、`ReportService`、组织授权和审计服务，但没有 Quartz、消息队列或通用任务编排依赖。

## 可行方案

### 方案 A：数据库 job + 行级 lease（推荐）

使用 `report_generation_jobs` 持久化任务和唯一幂等键 `(subscription_id, scheduled_at)`。扫描器在事务内锁定到期订阅，创建 READY/SKIPPED job，并把订阅的 `next_run_at` 推进到下一个窗口。worker 使用 `PESSIMISTIC_WRITE` 锁领取 READY 或到期 RETRY_WAIT job，将状态改为 RUNNING 并写入 `lease_owner`/`lease_expires_at`；执行结束后原子地转为 SUCCEEDED、RETRY_WAIT 或 PERMANENT_FAILURE。

优点：复用当前迁移和 JPA 基础设施，唯一约束直接提供跨实例幂等，lease 超时可由下一次扫描恢复，测试可以在 H2 中覆盖并发和重启语义。缺点：扫描和领取需要严格控制事务边界，吞吐上限由数据库锁和批量大小决定。

### 方案 B：Quartz 集群调度

引入 Quartz JDBC JobStore，由 Quartz 负责触发器、集群锁和 misfire，再在业务表中保存报告 job 结果。

优点：成熟的 cron/misfire/集群调度能力。缺点：当前只有受控日/周/月计划，Quartz 会引入新的 schema、配置和运行时状态；P2-3A 已明确调度契约应独立于执行引擎，当前收益不足以覆盖额外复杂度。

### 方案 C：消息队列 + 消费者幂等

扫描器向外部队列发布任务，消费者使用业务幂等键和结果表去重。

优点：适合高吞吐和异步扩展。缺点：项目没有队列依赖或运维契约，消息投递、重复消息、消费 offset 和回执会把 P2-3C/P2-4C 的问题提前带入本任务。

## 结论

采用方案 A。P2-3B 只实现数据库 job、唯一幂等、短 lease、有限退避和可重放状态；通道投递留给 P2-3C，通用编排平台留在范围之外。

## 固定行为

- 幂等键：`subscription_id + scheduled_at`，数据库唯一约束是最终并发保障。
- Lease：默认 5 分钟；RUNNING 且 lease 已过期的 job 可恢复为 READY。
- 重试：首次执行失败后最多重试 3 次，退避为 5 分钟、30 分钟、2 小时；第 4 次执行仍失败时进入 PERMANENT_FAILURE。
- 错过窗口：使用 P2-3A 的 60 分钟 grace；超过 grace 创建 `SKIPPED/MISSED_WINDOW` job 并推进订阅，不生成报告。
- 暂停/取消：订阅禁用时未领取 job 进入 `CANCELLED/SUBSCRIPTION_DISABLED`；已领取 job 在真正生成前再次检查订阅和租户状态。
- 权限/范围：执行前通过 `TenantMemberDirectory` 和 `OrganizationAuthorizationService` 重新加载创建者上下文；失败只保存固定错误代码，不保存原始异常消息、token、收件人地址或报告正文。
- Trace：每个 job 持久化安全的 trace ID，并通过审计事件关联订阅、job 和最终 report draft ID。

## 代码约束

- 生成复用 `ReportService.generate(ReportGenerationRequest, OrganizationDataScope)`，不复制 Dashboard/KPI/Markdown 逻辑。
- 任务状态和错误代码使用受控枚举/常量；controller（如增加管理读取/重放入口）返回标准 `ApiResult`。
- Repository 对领取/恢复操作使用 JPA lock，所有 mutation 在事务内完成；不要使用 JVM synchronized 作为跨实例互斥。
