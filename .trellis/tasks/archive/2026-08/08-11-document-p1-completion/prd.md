# 记录 P1 完成状态并推送

## Goal

将已完成的 P1-1 至 P1-5 首版能力同步到项目入口文档和生产化路线图，明确当前已交付范围、仍待手工验证的环境项和属于 P2 的后续能力，然后将当前 `main` 上未推送的 P1-5、归档、日志及本次文档提交一并推送到 `origin/main`。

## Requirements

* 在根 `README.md` 增加简洁的当前交付状态，说明 P0 与 P1 首版已完成，并链接路线图。
* 在 `docs/README.md` 修正根 README 仍是 POC 视角的过时描述，使文档导航与当前实现一致。
* 在 `docs/07-生产化升级路线图.md` 增加 P1 完成摘要，覆盖持久化与迁移、模块化、受控 Agent、RAG 和报告草稿。
* 文档必须明确“P1 首版完成”的边界：真实 PostgreSQL 凭据环境启动仍需手工验收；完整组织权限、多租户、PDF/Word 导出、任意文档上传等不得表述为已完成。
* 检查 Markdown 差异及链接，提交本次文档更新，并将 `main` 推送到 `origin/main`。

## Acceptance Criteria

* [x] 根 README 能直接看到 P1-1 至 P1-5 的完成状态与后续边界。
* [x] 生产化路线图有统一的 P1 完成摘要，与各小节已记录的实施状态一致。
* [x] 文档索引不再将根 README 标记为仅 POC 视角。
* [x] 文档不宣称 P2 能力或未完成手工环境验收已交付。
* [x] 工作区提交完成，`origin/main` 包含本地 `main` 的全部预期提交。

## Definition of Done

* 文档内容与代码、已归档任务和开发日志一致。
* Markdown 格式、链接和 Git 差异已检查。
* 本次文档更新已提交，当前分支已成功推送。

## Technical Approach

以 `docs/07-生产化升级路线图.md` 为 P1 范围和边界的权威来源，以根 `README.md` 作为面向使用者的简短状态入口，以 `docs/README.md` 作为文档导航状态。不改动功能代码或 API 契约。推送前显式核对未推送提交、远端、认证和仓库差异。

## Decision (ADR-lite)

**Context**: P1-3 和 P1-4/P1-5 小节已有实施状态，但路线图缺少 P1 整体完成摘要，文档索引仍将根 README 标记为 POC 视角。

**Decision**: 保留原有路线规划作为历史和验收依据，另增统一的“P1 完成状态”摘要；入口文档只做简要引导，不重复全部技术细节。

**Consequences**: 读者能快速判断当前阶段，同时保留首版范围与 P2/手工验收项的明确边界；后续 P2 交付时需继续更新同一状态摘要。

## Out of Scope

* 不修改功能代码、测试、API 或数据库迁移。
* 不在本任务执行需要真实凭据的 PostgreSQL 手工环境验收。
* 不将 P2 权限、多租户、订阅推送和运维治理记为已完成。
* 不创建新分支或 PR；用户明确要求将当前 `main` 更新后直接 push。

## Technical Notes

* P1 范围权威来源：`docs/07-生产化升级路线图.md`。
* 实施日志：`.trellis/workspace/kevin/journal-1.md` 的 Session 22–25。
* 本任务创建时 `main` 相对 `origin/main` ahead 3，包含 P1-5 报告草稿、P1-4 任务归档和日志提交。
* 相关入口文档：`README.md`、`docs/README.md`。
