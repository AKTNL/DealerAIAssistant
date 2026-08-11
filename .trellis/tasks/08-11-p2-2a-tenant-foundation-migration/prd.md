# P2-2A tenant 基础模型与迁移

## Goal

定义平台 tenant 所有权模型，并用可回滚的前向迁移把现有单租户身份、组织和业务数据纳入一个明确的默认 tenant，为后续强制隔离提供可靠 schema。

## Dependencies

* 前置：P2-1B 组织模型和授权上下文稳定。
* 后续：P2-2B 隔离执行、P2-2C tenant 级资源、P2-4B 成本归集。

## Requirements

* 先形成 ADR：全局身份 + tenant membership，或 tenant 本地身份；推荐全局身份、tenant membership 与 tenant 内角色/grant。
* tenant 至少具有稳定 ID、唯一 key、显示名、启用状态、创建/更新时间和版本。
* 明确 auth、organization、import batch、六类业务数据、knowledge、report、model config 的 tenant 所有权或受控全局语义。
* 为现有数据创建唯一默认 tenant 并确定性回填；不允许迁移后存在来源不明的受保护记录。
* 设计 tenant 范围的复合唯一约束和高频查询索引，不继续使用错误的全局唯一业务键。
* 迁移保持 H2 合约测试与 PostgreSQL 生产路径一致，并给出备份/回滚窗口。

## Acceptance Criteria

* [ ] ADR 说明身份、membership、角色和组织的 tenant 边界及未来多品牌扩展。
* [ ] 从当前 V4 schema 升级后，所有受保护记录都能追溯到默认 tenant。
* [ ] 同一业务 ID 可在不同 tenant 中共存，tenant 内约束仍有效。
* [ ] Flyway 前向迁移、Hibernate validate 和数据回填测试通过。
* [ ] 未执行 P2-2B 前，文档不宣称已经实现请求级隔离。

## Implementation Plan

1. tenant/identity ADR 与 schema 影响清单。
2. tenant/membership 模型和迁移脚本。
3. 现有表 tenant 所有权、回填、约束和索引。
4. 升级/回滚验证与生产数据检查脚本。

## Open Question

* 首个试点是否需要一个用户加入多个 tenant；该选择在写迁移前确认。

## Out of Scope

* 本任务不启用请求级 tenant 过滤或开放多 tenant UI。
* 不采用每 tenant 独立数据库/Schema，除非 ADR 另行批准。

