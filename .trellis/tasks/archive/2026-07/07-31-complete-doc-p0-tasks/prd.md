# 完成文档 P0 任务

## Goal

按 `docs/07-生产化升级路线图.md` 和 `docs/08-MVP产品需求文档.md` 的当前优先级，先交付 P0 MVP 中尚未落地的 Dashboard 首版和现有分析链路串联，让用户登录后能先看到经营状态、数据质量和可点击的分析入口，而不是只能从聊天开始。

## What I Already Know

* 用户要求“按照对应文档先完成 P0 任务”。
* `docs/07-生产化升级路线图.md` 将 P0 收敛为 MVP：模拟业务数据生成、MVP PRD、最小业务/KPI 口径、Dashboard 首版、现有分析链路串联。
* `docs/08-MVP产品需求文档.md` 要求 Dashboard 展示至少 5 类核心经营指标，并提供至少 3 类可进入聊天分析的问题入口。
* `docs/06-数据导入MVP实施说明.md` 显示中型 MVP workbook、启动导入、active batch 和 `/api/data-status` 已有基础。
* 当前 repo 已存在聊天工作台、SSE、规则分析、Excel 导入、数据状态 API、指标 API 和前端测试体系。

## Assumptions

* 本轮优先补 P0 中对用户可见、尚未实现的 Dashboard MVP，不重做已完成的文档和数据导入底座。
* Dashboard MVP 采用现有 Vue 单页应用和 Spring Boot 模块化单体，不引入新前端状态库、不切换数据库。
* Dashboard 指标应尽量复用既有指标/规则口径，避免产生另一套业务算法。

## Requirements

* 新增后端 Dashboard 汇总 API，返回经营概览、数据状态摘要、目标达成、商机漏斗、线索来源、跟进任务、活动效果和分析入口。
* Dashboard 汇总需基于 active batch 数据，与聊天/规则分析共用现有实体和指标口径。
* 前端登录后优先进入 Dashboard 或清晰展示 Dashboard 入口。
* Dashboard 首版展示至少 5 类核心经营指标：目标达成率、商机数/赢单数、线索数、任务数、活动数，并包含排名、漏斗、来源、任务和活动模块。
* Dashboard 显示 loading、empty、error、low confidence、simulated data/active batch/data quality 等状态提示。
* Dashboard 卡片提供至少 3 个可点击分析问题，并能进入现有聊天分析流程。
* 更新必要的 API 客户端、i18n 文案、测试和 OpenAPI/文档。

## Acceptance Criteria

* [ ] 后端提供 Dashboard 汇总 API，受现有 session/API key 保护，并默认读取 active batch。
* [ ] Dashboard 能展示至少 5 类核心经营指标和数据状态/质量提示。
* [ ] Dashboard 指标与聊天分析核心 KPI 口径一致，避免重复计算口径分叉。
* [ ] 至少 3 类 Dashboard 分析入口能把预置问题送入聊天分析。
* [ ] 前端 Dashboard 具备 loading、empty、error 状态。
* [ ] 未配置外部模型时，规则 fallback 仍可通过现有聊天链路完成分析。
* [ ] 相关前端 Vitest、后端 JUnit/PMD、构建命令通过或记录不能运行的原因。

## Definition of Done

* Tests added/updated for backend API and frontend Dashboard behavior.
* Frontend lint/test/build and backend PMD/test run for touched layers.
* API or docs updated if behavior changes.
* Out-of-scope production platform work is not included in this task.

## Out of Scope

* 不做完整数据库替换、Flyway/Liquibase 迁移或 PostgreSQL 切换。
* 不做完整用户、角色、组织树、审计后台、多租户或订阅推送。
* 不做自由行动 Agent、完整 RAG 知识库、PDF/Word 报告导出。
* 不重写全部前端架构，不引入复杂前端状态库。
* 不实现上传文件分析 UI/API。

## Technical Notes

* Core docs: `docs/07-生产化升级路线图.md`, `docs/08-MVP产品需求文档.md`, `docs/06-数据导入MVP实施说明.md`.
* Likely backend entry points: `AnalyticsApiController`, `AnalyticsApiService`, `DataStatusController`, `ImportBatchService`, existing entities and repositories.
* Likely frontend entry points: `App.vue`, `ChatView.vue`, `frontend/src/api/dataStatus.js`, existing chat composables and workspace components.
* Code inspection confirmed current frontend has no router; login gates directly into `ChatView`.
* Existing browser-facing APIs are whitelisted from `ApiKeyFilter` and protected by `SessionTokenFilter`; `/api/dashboard` must follow the same pattern.
* Existing `AnalyticsApiService` already implements active-batch filtering and comparable-rate handling; Dashboard should reuse the same semantics and avoid hard-coded answers.

## Technical Approach

* Backend: add `DashboardController`, `DashboardService`, and response records under `dto/response` for one `GET /api/dashboard` aggregate endpoint.
* Backend security: add `/api/dashboard` to `ApiKeyFilter` whitelist and `SessionTokenFilter` protected paths.
* Frontend API/composable: add `api/dashboard.js` and `useDashboard.js` with loading/error/reload state and auth-expiry handling.
* Frontend UI: add `DashboardView.vue` and wire it into `ChatView` as the default workspace tab; clicking analysis questions switches to chat and calls the existing `submitPrompt(question)` path.
* Tests: add backend controller/service/filter coverage and frontend API/composable/view coverage.

## Decision (ADR-lite)

**Context**: P0 requires a visible Dashboard MVP, but the project already has entity repositories, metrics services, active batch, data status, chat, and SSE.

**Decision**: Build a thin Dashboard aggregate surface on top of the existing data layer and existing chat submission flow. Do not introduce router/state-library/database changes in this task.

**Consequences**: The MVP becomes visible quickly and keeps Dashboard/chat metrics aligned. Some ranking/detail fields remain intentionally shallow until later P1 metric-definition work.
