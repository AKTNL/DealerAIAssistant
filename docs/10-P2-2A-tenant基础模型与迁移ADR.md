# P2-2A tenant 基础模型与迁移 ADR

## Context

P2-1 已建立全局用户、角色、组织树和组织范围授权，但业务记录仍来自单租户 schema。后续请求级隔离需要一个稳定的所有权字段、membership 关系和可回滚的前向迁移。

## Decision

采用“全局身份 + tenant membership + tenant 内角色关联”的模型：

* `tenants` 保存稳定的 `tenant_key`、展示名、启用状态、时间戳和乐观锁版本。
* `tenant_memberships` 把全局 `auth_users` 映射到 tenant；禁用 membership 在下一次请求生效。
* `tenant_membership_roles` 保存 tenant 内角色 grant，不把 tenant 权限快照写入 opaque session token。
* 现有身份、组织节点、组织 grant、import batch 和六类业务表统一回填到 `default` tenant（ID 1）。
* tenant 复合唯一约束和高频查询索引优先于全局业务键；同一业务 ID 可以在不同 tenant 中重复。

P2-2A 只建立 schema 和所有权模型。请求解析、repository/service 强制过滤、SSE/Agent 传播和平台资源 tenant 化由 P2-2B/P2-2C 完成。

## Consequences

* 单租户现有数据保持可读且可追溯，迁移失败可以在应用发布前停止。
* 物理数据库仍为共享 schema；物理隔离需要单独 ADR。
* 迁移是前向的 V6，不能修改已应用 SQL。回滚应用时保留 tenant 列和迁移历史，避免恢复到会忽略所有权的旧二进制。
