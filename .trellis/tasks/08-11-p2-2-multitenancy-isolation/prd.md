# P2-2 多租户和数据隔离

## Goal

在 P2-1 的身份、组织和授权上下文之上建立 tenant 所有权，使业务数据、配置、知识、导入和报告默认不能跨 tenant 读取或写入。

## Child Tasks

1. **P2-2A tenant 基础模型与迁移**：定义 tenant、所有权、回填和索引迁移。
2. **P2-2B tenant 隔离强制执行**：请求上下文、服务/持久化过滤、写入校验和默认拒绝。
3. **P2-2C tenant 级平台资源**：模型、知识、导入、报告和组织配置 tenant 化。
4. **P2-2D tenant 发布与隔离验收**：迁移演练、攻击矩阵和生产发布回滚。

## Dependency Order

`P2-1B -> P2-2A -> P2-2B -> P2-2C -> P2-2D`

## Acceptance Criteria

* [ ] 每个受保护记录都有明确 tenant 所有权或受审计的全局语义。
* [ ] 所有读写路径默认带 tenant 条件，缺失 tenant 上下文时失败关闭。
* [ ] 双 tenant 对抗测试覆盖 HTTP、SSE、Agent、知识、报告、导入和管理 API。
* [ ] 数据迁移、备份恢复、发布和回滚可演练。

## Out of Scope

* 不因 tenant 化拆分微服务或独立数据库；物理隔离需要另行 ADR。
* 跨 tenant 数据共享和联合分析首版不做。

