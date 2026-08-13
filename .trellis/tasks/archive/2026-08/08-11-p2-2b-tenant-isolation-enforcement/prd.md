# P2-2B tenant 隔离强制执行

## Goal

让每个请求、后台调用和数据访问都在受信 tenant 上下文中执行，缺少或不匹配 tenant 时默认拒绝，形成真正可验证的多租户安全边界。

## Dependencies

* 前置：P2-2A tenant/membership/schema 与 P2-1B 授权上下文完成。
* 后续：P2-2C tenant 级资源、P2-2D 发布验收、P2-3、P2-4B。

## Requirements

* 通过已认证 membership 选择 active tenant；opaque token 不携带长期权限快照。
* 每次请求解析当前用户、tenant、角色/权限、组织范围和 active batch；membership 禁用立即生效。
* 所有 repository/service 读写必须包含 tenant 条件，创建/关联对象必须验证同 tenant。
* HTTP、SSE、Agent tool、知识、报告和后台任务使用同一 tenant-aware 授权上下文。
* URL/header/body 中的 tenant 标识只作为选择意图，不能覆盖服务端 membership 校验。
* 缺 tenant、未知 tenant、跨 tenant ID 引用和混合 tenant 聚合稳定拒绝并记录安全审计。

## Acceptance Criteria

* [ ] 两个 tenant 使用相同业务 ID 时，所有列表、详情、聚合、报告和工具结果完全隔离。
* [ ] 猜测另一个 tenant 的 ID、report ID、batch ID、knowledge chunk 或组织 ID 均不能读写。
* [ ] 禁用 membership、切换 tenant 和权限变更在下一请求生效。
* [ ] 无 tenant 上下文的受保护业务路径失败关闭；仅显式全局运维端点例外。
* [ ] 跨 tenant 对抗回归覆盖 controller、service、repository 和 Agent 层。

## Implementation Plan

1. tenant-aware 授权上下文和 active tenant 选择契约。
2. 持久化/服务写入与读取隔离。
3. HTTP/SSE/Agent/后台执行传播与拒绝审计。
4. 双 tenant 对抗矩阵和性能索引验证。

## Out of Scope

* tenant 级平台资源迁移在 P2-2C。
* 不支持跨 tenant 联合查询、共享报告或超级管理员业务数据旁路。

