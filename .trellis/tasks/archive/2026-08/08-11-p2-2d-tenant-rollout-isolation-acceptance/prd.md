# P2-2D tenant 发布与隔离验收

## Goal

用升级演练、双 tenant 攻击矩阵和生产运行说明证明 P2-2 不只是 schema 带有 `tenant_id`，而是可安全发布、诊断和回滚的完整隔离能力。

## Dependencies

* 前置：P2-2A、P2-2B、P2-2C 全部完成。
* 后续：P2-3 运营能力和 P2-4D 最终生产收口。

## Requirements

* 建立从当前单租户生产备份升级到默认 tenant、再新增第二 tenant 的演练脚本。
* 建立 HTTP/SSE/Agent/知识/报告/导入/管理 API 的双 tenant 允许与攻击矩阵。
* 验证数据库约束、索引、查询计划和日志能支持定位 tenant 隔离问题。
* 明确迁移前备份、不可逆步骤、回滚兼容窗口、失败清理和恢复验证。
* 更新 OpenAPI、配置、部署、联调、威胁模型和运维手册。

## Acceptance Criteria

* [ ] 干净库和现有单租户库均能升级，重启后数据和 tenant 所有权不漂移。
* [ ] 自动攻击矩阵对所有已知跨 tenant ID/过滤/关联路径返回拒绝且不泄露存在性。
* [ ] 真实 PostgreSQL/PGvector 环境完成迁移和隔离 smoke test。
* [ ] 备份恢复后 tenant 数据计数、active batch、知识和报告一致。
* [ ] 发布/回滚 runbook 可由未参与实现的人按步骤执行。

## Implementation Plan

1. 升级数据集和双 tenant 验收夹具。
2. 跨接口攻击与查询计划测试。
3. 真实环境迁移、备份恢复和回滚演练。
4. 文档、威胁模型和上线检查单。

## Out of Scope

* 不在本任务增加新的 tenant 业务功能。
* 不以人工抽查替代自动隔离回归。
