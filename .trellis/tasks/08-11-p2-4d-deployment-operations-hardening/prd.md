# P2-4D 部署与运维加固

## Goal

作为 P2 最终收口，建立生产配置校验、迁移检查、备份恢复、发布 smoke test、灰度/回滚和安全运行手册，使完整平台可重复上线和恢复。

## Dependencies

* 前置：P2-2D tenant 验收、P2-3C/P2-3D 运营能力、P2-4B/P2-4C 成本与健康治理。

## Requirements

* 启动时验证生产数据库、Flyway、Secure Cookie、bootstrap/secret、tenant、知识、模型和投递配置，缺失关键安全配置失败关闭。
* 自动执行发布前 schema/version/backup/readiness 检查和发布后登录、Dashboard、SSE、知识、报告、订阅 smoke test。
* 明确数据库/PGvector/报告/知识/审计备份范围、恢复顺序、RPO/RTO 目标和定期恢复演练。
* 定义向前兼容迁移、应用版本回滚窗口、不可回滚变更和数据补偿流程。
* 建立 secret 轮换、管理员恢复、tenant 停用、事件响应和证据保留 runbook。
* CI 分层为快速 PR 门与完整发布门，保留 PMD、后端测试、前端 lint/test/build 和隔离攻击回归。

## Acceptance Criteria

* [ ] 从干净环境可按文档部署，从生产备份可恢复并通过 smoke test。
* [ ] 缺失关键生产配置、失败迁移或 tenant/schema 不一致时不会带病接流量。
* [ ] 至少完成一次升级、应用回滚和备份恢复演练并记录结果。
* [ ] 发布门覆盖认证、组织、tenant、Agent、知识、报告、订阅、成本和健康链路。
* [ ] 运维人员可从告警/trace 进入明确 runbook，不需要阅读源码才能开始处置。

## Implementation Plan

1. 生产配置与发布前检查。
2. 部署/升级/smoke/回滚自动化。
3. 备份恢复、secret 轮换和事件响应演练。
4. CI 发布门、运行手册和 P2 最终验收报告。

## Out of Scope

* 不在本任务切换云厂商或拆微服务。
* 不以未演练的文档声明替代恢复和回滚验证。
