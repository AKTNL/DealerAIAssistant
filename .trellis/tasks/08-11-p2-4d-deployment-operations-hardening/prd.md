# P2-4D 部署与运维加固

## Goal

作为 P2 最终收口，将已有生产 profile、Flyway、PGvector、tenant 隔离、健康检查和可观测能力串成可重复的部署、升级、备份恢复、smoke、灰度和回滚流程，使未参与实现的运维人员也能按 runbook 安全操作。

## What I Already Know

* 当前交付物是内嵌 Vue 静态资源的单一 Spring Boot 应用，不在本任务拆微服务。
* `prod` 使用 PostgreSQL + PGvector + Flyway，Hibernate 只做 schema validate；迁移遵循前向兼容，不修改已应用 SQL。
* `/livez` 只表示进程存活，`/readyz` 包含数据库、迁移和知识索引，可直接作为流量门。
* 身份、tenant/组织、业务数据、PGvector 知识、报告、订阅/job/投递、审计和模型用量都在同一 PostgreSQL 数据库中，必须作为一致恢复单元。
* 仓库尚无容器镜像、生产编排模板、发布前检查、备份恢复脚本、认证 smoke 和完整发布门。
* 当前本机 Docker CLI 可用但 daemon 未启动，主机也未安装 PostgreSQL 客户端；在 daemon 或外部 PostgreSQL 可用前，不得宣称已完成真实恢复演练。

## Dependencies

* 前置：P2-2D tenant 验收、P2-3C/P2-3D 运营能力、P2-4B/P2-4C 成本与健康治理。

## Requirements

* 仅在 `prod` 启用生产配置验证器：要求 PostgreSQL/Flyway、PGvector、Secure Cookie、关闭样例 fallback、非空数据库密码、模型/投递加密密钥和非本地 CORS；任一关键安全条件缺失时启动失败且错误不包含 secret。
* 构建可重复、非 root 运行的单一应用镜像，并提供 PGvector + 应用的生产化 Compose 模板、healthcheck、持久化 volume 和不含真实 secret 的环境变量示例。
* 提供跨平台发布工具，使发布前可自动验证工具/配置、Flyway validate/info、schema 版本、tenant 一致性、备份存在且归档可列取；输出可审计 JSON 证据但不记录密码、token、prompt 或业务正文。
* 备份使用 PostgreSQL custom format，生成 SHA-256 和非敏感 manifest；恢复必须进入新建空库，先校验归档，再恢复全库一致单元，最后执行 schema/tenant 检查和 smoke。
* 自动 smoke 按从只读到可选写入的顺序覆盖 liveness/readiness、登录、当前 tenant、Dashboard、数据状态、SSE/知识、报告草稿和订阅列表；写入型 smoke 必须显式开启并使用隔离验收 tenant。
* 发布采用“备份 -> 前向兼容迁移 -> 新版本无流量启动 -> readiness/smoke -> 逐步切流”；回滚窗口内只回滚应用镜像且保留新 schema，不执行 Flyway clean/down。
* 不兼容迁移必须在发布 manifest 标记为不可应用回滚，需通过前向修复迁移或经审批的数据库恢复处理；不把“修改已应用 SQL”当作回滚。
* 建立 secret 轮换、一次性管理员 bootstrap/恢复、tenant 停用、事件分级、证据保留和恢复演练 runbook；轮换必须明确重加密/双读窗口与验证步骤，不盲目替换密钥导致历史密文不可读。
* CI 分为快速 PR 门和完整发布门：PR 门保留 PMD、后端测试、前端 lint/test/build；发布门增加 PGvector/Flyway、`prod` 启动、认证 smoke、tenant 隔离回归、备份归档检查、空库恢复和恢复后 smoke。

## Operational Targets

* 首版保持云厂商中立；镜像、发布检查、smoke 和证据格式可复用到 Compose、Kubernetes 或托管平台。
* 首版恢复目标为每日全量 + 每次发布前备份、RPO <= 24h、RTO <= 4h、每月至少一次空库恢复演练。

## Acceptance Criteria

* [ ] 在干净环境中能用文档化命令构建镜像、启动 PGvector/应用、到达 readiness 并通过认证 smoke。
* [x] 缺失关键生产配置、Flyway 校验/迁移失败、备份不可列取或 tenant/schema 不一致时，发布工具非零退出且应用不接流量。
* [ ] 从 custom-format 备份恢复到新建空库后，schema/Flyway、tenant/membership、active batch、知识、报告、订阅/作业/投递、审计和模型用量检查通过，并能重跑 smoke。
* [x] 至少有一份仓库级升级、应用回滚和空库备份恢复演练记录；若当前环境不具备 Docker/PostgreSQL，必须明确标记未执行而不伪造通过。
* [x] 发布门覆盖认证、组织/tenant、Dashboard/Agent/SSE、知识、报告/订阅、成本和健康链路，并保留原隔离攻击回归。
* [x] 发布/smoke/备份证据不含密码、Bearer/refresh token、API key、加密密钥、prompt 或业务正文。
* [x] 运维人员可从告警/trace 进入部署、备份恢复、回滚、secret 轮换、管理员恢复、tenant 停用和事件响应 runbook，不需要阅读源码才能开始处置。

## Definition Of Done

* 生产配置校验和发布工具具有失败/脱敏回归。
* PMD、后端全量测试、前端 lint/test/build 通过。
* 容器构建、Compose 配置解析和完整发布门在可用环境验证；不可用项明确列为待环境演练。
* README、文档索引、运行手册、配置清单、回滚约束和演练记录同步。
* 检查本任务产生的部署/运维约定是否需要写入 `.trellis/spec/`。

## Technical Approach

* 采用云厂商中立的“容器镜像 + Compose 可重复参考环境 + 仓库自带发布工具 + 平台负责 TLS/流量/持久化 secret”方案。
* 发布工具优先使用 Python 标准库实现，保持 Windows/Linux 一致、可单元测且不引入运行依赖；PostgreSQL 实操通过 `pg_dump`/`pg_restore`/`psql` 官方客户端。
* 镜像使用 Node/Maven 分阶段构建前后端，最终层只保留 JRE 和应用 jar，使用非 root UID 运行。
* smoke 默认只读；报告草稿等写入项需显式 `--allow-writes`，并记录创建资源 ID 供验收环境清理。
* 备份 manifest 只记录时间、应用/迁移版本、归档大小、SHA-256、工具版本和非敏感记录计数；连接串和 secret 不落盘。

## Decision (ADR-lite)

**Context**: 需要完成生产收口，但项目没有选定云厂商、Kubernetes 或托管数据库。

**Decision**: 选择仓库自给的云中立容器/发布工具方案，以 PostgreSQL custom-format 备份和 expand/contract 前向迁移为恢复/回滚基线。

**Consequences**: 仓库能验证镜像、数据库、迁移、smoke 和恢复流程；首版以 RPO <= 24h / RTO <= 4h 为试点目标。TLS、负载均衡、持久 secret store、定时保留策略、WAL/PITR 和真实 SLA 仍由目标部署平台实现与演练。

## Implementation Plan

1. 生产配置失败关闭与单元回归。
2. 非 root 镜像、Compose/环境模板和发布前检查。
3. 认证 smoke、备份/manifest/空库恢复与工具测试。
4. 分层 CI 发布门、部署/回滚/secret/事件 runbook 和演练证据。

## Research References

* [`research/deployment-recovery-approach.md`](research/deployment-recovery-approach.md) - 推荐云中立容器与仓库自带发布工具，保留平台级 PITR/流量/secret 集成边界。

## Out of Scope

* 不在本任务切换云厂商或拆微服务。
* 不在首版新增 Kubernetes/Helm、云厂商负载均衡、托管 secret store 或备份产品集成。
* 除非用户选择严格恢复等级，不在本任务实现 PostgreSQL WAL 归档/PITR；文档会保留升级路径。
* 不在 smoke 中向真实客户发送邮件/协作消息，不重放 UNKNOWN 投递。
* 不自动执行破坏性 Flyway clean/down、审计删除或未审批的生产库覆盖恢复。
* 不以未演练的文档声明替代真实恢复、灰度和回滚验证。
