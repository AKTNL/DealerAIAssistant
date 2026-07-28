# POC 到真实项目重构前的文档清理

## Goal

将当前项目从 POC 资料堆积状态整理为更适合后续真实项目重构的文档结构：先降低旧设计/实施文档对后续开发的误导，再基于 `docs/Agent-POC-需求文档.docx` 继续拆分新的前端、后端、数据、联调、测试需求文档。

## What I already know

* 用户希望项目从 POC 走向更贴近真实可用的项目，并准备开始重构。
* 用户明确提到后续可以在 `Agent-POC-需求文档` 基础上按“前端 / 后端 / 数据 / 联调 / 测试”继续拆分。
* `docs/Agent-POC-需求文档.docx` 明确定位为 POC 需求文档，包含前端需求、后端需求、数据查询 API、SSE、非功能要求和验收标准。
* `docs/经销商AI分析助手_完整原型设计文档.docx` 更像原型设计与系统架构说明，包含视觉基调、五层架构、工具与 API 映射等较强的原型期设定。
* `docs/design/` 与 `docs/plan/` 下有大量 2026-05 到 2026-06 的历史设计和实施计划，主要记录已完成或阶段性的 POC 迭代。
* `docs/01-功能清单.md` 到 `docs/05-技术架构.md` 是从 `Agent-POC-需求文档 v2026-04-29` 派生的 POC 总览文档。
* 当前未跟踪文件中有一个 Word 临时锁文件：`docs/~$ent-POC-需求文档.docx`，属于明显噪声。

## Assumptions

* `docs/Agent-POC-需求文档.docx` 暂时保留为需求基线，不在本轮删除。
* 后续新文档应避免继续沿用“功能清单 / 上下游 / 数据流向 / 业务架构 / 技术架构”的 POC 拆分方式，而是改为用户提出的“前端 / 后端 / 数据 / 联调 / 测试”。
* 历史实现计划如果仍有价值，Git 历史即可追溯，不需要继续留在主 `docs/` 目录中影响重构判断。

## Open Questions

* None.

## Requirements (evolving)

* 保留一个清晰的需求基线：`docs/Agent-POC-需求文档.docx`。
* 删除或移出会误导后续重构的 POC 历史设计、实施计划和临时文件。
* 为后续按“前端 / 后端 / 数据 / 联调 / 测试”拆分需求留出干净的文档空间。
* 不改动业务代码，不启动实际重构实现。

## Acceptance Criteria (evolving)

* [x] 删除策略经用户确认：采用激进清理，只保留 `docs/Agent-POC-需求文档.docx` 作为旧需求基线。
* [x] 明显无用的临时文件被删除或确认不再存在。
* [x] 历史 POC 设计/计划文档不再停留在主文档路径中误导后续开发。
* [x] 保留的文档有明确角色：需求基线、当前说明或后续拆分入口。
* [x] `git status` 能清晰显示本轮文档清理变更。

## Definition of Done

* 文档清理范围明确。
* 删除后的目录结构可解释。
* 不误删 `docs/Agent-POC-需求文档.docx`。
* 如删除跟踪文件，确认可通过 Git 历史找回。

## Out of Scope

* 本轮不重构前端、后端或数据模型代码。
* 本轮不重写完整新需求文档，只为后续拆分清出空间。
* 本轮不修改 `.trellis/spec/` 项目开发规范，除非文档清理暴露出必须记录的新约定。

## Technical Notes

### Current document inventory

* Baseline candidate: `docs/Agent-POC-需求文档.docx`
* Prototype reference candidate: `docs/经销商AI分析助手_完整原型设计文档.docx`
* Narrow example candidate: `docs/商机漏斗于转化_示例问题_修复版.docx`
* POC summary docs: `docs/01-功能清单.md`, `docs/02-上下游.md`, `docs/03-数据流向.md`, `docs/04-业务架构.md`, `docs/05-技术架构.md`
* Historical design notes: `docs/design/*.md`
* Historical implementation plans: `docs/plan/*.md`
* Old prototype HTML: `docs/yx/yx_sheji.html`
* Obvious temporary file: `docs/~$ent-POC-需求文档.docx`

### Recommended cleanup strategy

Decision: aggressive cleanup.

* Keep `docs/Agent-POC-需求文档.docx`.
* Delete `docs/~$ent-POC-需求文档.docx`.
* Delete `docs/design/`.
* Delete `docs/plan/`.
* Delete `docs/yx/`.
* Delete `docs/经销商AI分析助手_完整原型设计文档.docx`.
* Delete `docs/商机漏斗于转化_示例问题_修复版.docx`.
* Delete `docs/01-功能清单.md` through `docs/05-技术架构.md`.
* Keep `README.md` for now, then update it after the new split docs are created.

## Decision (ADR-lite)

**Context**: The project is moving from POC to a more realistic refactor. Old POC design notes, implementation plans, prototype docs, and derived summaries may bias future work toward outdated assumptions.

**Decision**: Use aggressive cleanup for the first pass. Keep only `docs/Agent-POC-需求文档.docx` under `docs/` as the old-source baseline, and delete all other current `docs/` artifacts.

**Consequences**: The main docs directory becomes intentionally sparse and easier to rebuild. Details remain recoverable from Git history if needed, but future work should not rely on the deleted POC-era documents unless explicitly restored.

## Implementation Summary

* Removed all current `docs/` content except `docs/Agent-POC-需求文档.docx`.
* The deleted content includes old POC summary markdown files, historical design notes, historical implementation plans, the old prototype HTML, and auxiliary prototype/example DOCX files.
* Verified `docs/` now contains only the retained baseline DOCX.
