# 生产化重构文档编写

## Goal

基于 `docs/Agent-POC-需求文档.docx` 和当前代码实现，编写一组用于指导后续从 POC 走向更真实项目的重构文档。文档应从“前端 / 后端 / 数据 / 联调 / 测试”五个维度拆分需求与改造边界，并补一个总览入口，避免后续开发继续依赖旧 POC 叙述。

## What I already know

* 用户希望接下来先写“要做的文档”，而不是马上动代码重构。
* 上一轮已清理 `docs/`，当前只保留 `docs/Agent-POC-需求文档.docx` 作为旧需求基线。
* README 仍以 “Agent POC - 星曜汽车 AI 分析助手” 定位项目，但包含大量当前实现事实。
* 当前工程是 Vue 3 + Vite 前端、Spring Boot 3.4 + Java 21 后端、H2 + Excel 样例数据、REST + SSE 通信。
* 当前能力包括访问密钥登录、同步/流式聊天、SSE step 与 `analysis_metadata`、规则分析引擎、外部模型润色、Markdown/图表渲染、模型连接配置、数据状态接口、原始数据查询和指标/详情 API。
* 当前分析场景包括目标达成、商机漏斗与转化、销售跟进、市场活动、经营对标、线索来源。
* 前端脚本包括 `npm run lint`、`npm run test`、`npm run build`。
* 后端脚本包括 `mvn "-Dfrontend.skip=true" pmd:check`、`mvn "-Dfrontend.skip=true" test`、`mvn "-Dfrontend.skip=true" clean install`。

## Assumptions

* 第一批文档应以 Markdown 写入 `docs/`，方便后续开发持续编辑和 diff。
* `docs/Agent-POC-需求文档.docx` 不直接改写，作为输入材料保留。
* 新文档应更像“重构需求与工程决策入口”，不只是复制 POC 原文。
* README 暂时不在本任务重写，避免同时承担项目主页更新。

## Requirements (evolving)

* 第一批采用完整骨架方案：一次性建立文档入口、重构总览、前端、后端、数据、联调、测试 7 份 Markdown 文档。
* 新建一个文档入口，说明文档体系、阅读顺序、哪些内容来自 POC、哪些是生产化重构目标。
* 按用户提出的五个维度拆分文档：
  * 前端：页面、交互、状态、SSE、图表/Markdown、模型设置、安全边界。
  * 后端：API、服务分层、规则引擎、模型接入、鉴权、配置、错误处理。
  * 数据：Excel 导入、字段清洗、业务实体、缺失值、样例数据与真实数据迁移方向。
  * 联调：本地启动、环境变量、前后端代理、SSE 协议、API 契约、模型服务连接。
  * 测试：前端 lint/test/build、后端 PMD/test/build、准确率题库、人工验收清单。
* 文档要区分“当前 POC 已有能力”“真实项目需要补齐”“暂不做/后续做”。
* 文档应该能直接指导后续拆任务，不要求一次性写成最终投产方案。

## Acceptance Criteria (evolving)

* [x] `docs/` 下出现新的 Markdown 文档入口。
* [x] 覆盖前端、后端、数据、联调、测试五个拆分维度。
* [x] 建立重构总览文档。
* [x] 每份主题文档都有清晰目标、当前状态、改造方向、待拆任务和开放问题。
* [x] 文档明确标注 POC 保留能力与生产化待补能力。
* [x] 不修改业务源码。
* [x] `docs/Agent-POC-需求文档.docx` 保留。

## Definition of Done

* 文档结构经用户确认。
* 新 Markdown 文档写入 `docs/`。
* 文档内容与 README、当前代码结构、POC 需求基线不冲突。
* `git status` 能清晰显示新增文档。

## Out of Scope

* 本任务不做前端/后端/数据模型代码重构。
* 本任务不接入真实外部系统。
* 本任务不把 Word 需求文档转换成完整 Markdown 全文。
* 本任务不重写 README，除非后续用户明确要求。

## Technical Approach

Selected first-pass document set:

* `docs/README.md` — 文档入口与阅读顺序。
* `docs/00-重构总览.md` — 从 POC 到真实项目的目标、原则、阶段与边界。
* `docs/01-前端重构需求.md` — 前端体验、状态、组件、协议消费和 UI 验收。
* `docs/02-后端重构需求.md` — 后端 API、服务边界、AI/规则链路、配置与安全。
* `docs/03-数据重构需求.md` — 数据源、导入、清洗、实体、口径、真实数据迁移。
* `docs/04-联调方案.md` — 本地/环境/接口/SSE/模型服务联调流程。
* `docs/05-测试验收方案.md` — 自动化测试、准确率题库、人工验收和回归门槛。

## Expansion Sweep

* Future evolution: 后续可能拆成多轮实际重构任务，所以文档需要留下“待拆任务”和“开放问题”栏目。
* Related scenarios: README、Trellis PRD、代码 spec 可能以后都要跟新文档对齐，但本任务先不强行同步。
* Failure/edge cases: 最大风险是把 POC 方案包装成生产方案；因此每份文档都要区分 current / target / out-of-scope。

## Decision Needed

None. User selected the complete skeleton approach.

## Decision (ADR-lite)

**Context**: The project needs a clean documentation basis before code refactoring begins.

**Decision**: Create the complete first-pass documentation skeleton in one batch: index, overview, frontend, backend, data, integration, and testing.

**Consequences**: The first docs will be intentionally broad and task-oriented rather than exhaustive. Later implementation tasks can deepen each area without waiting for the whole document set to be invented.

## Technical Notes

Inspected files:

* `docs/Agent-POC-需求文档.docx`
* `README.md`
* `frontend/package.json`
* `backend/pom.xml`
* Current frontend/backend file structure via `rg --files`
* Backend API annotations via `rg`
* Backend entity fields via `rg`
* Sample workbook sheets and headers via `openpyxl`

## Implementation Summary

Created:

* `docs/README.md`
* `docs/00-重构总览.md`
* `docs/01-前端重构需求.md`
* `docs/02-后端重构需求.md`
* `docs/03-数据重构需求.md`
* `docs/04-联调方案.md`
* `docs/05-测试验收方案.md`

Verified:

* `docs/Agent-POC-需求文档.docx` remains in place.
* `rg --files docs` shows the baseline DOCX plus the seven new Markdown documents.
* Heading scan confirms the new documents have the expected top-level structure.
* Section completeness check confirms each topic document contains `## 目标`, `## 待拆任务`, and `## 开放问题`.
* No frontend/backend source files were modified.
* Frontend/backend tests were not run because this task only adds documentation.
