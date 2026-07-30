# Production Upgrade Roadmap Documentation

## Goal

Create a stable product and engineering roadmap document for upgrading DealerAIAssistant from its current POC shape into a usable dealer operations analytics product. The document should become the reference for later implementation tasks and should preserve the agreed P0/P1/P2 priority model.

## What I Already Know

* The project currently has a Vue 3 frontend, Spring Boot backend, REST/SSE chat flow, Excel import, rule-based analytics fallback, optional model polishing, data status reporting, and automated tests.
* The current POC constraints include H2 in-memory storage, startup Excel import, weak access-key authentication, browser-local model settings, and limited deployment/operations guidance.
* The user agreed with a priority model:
  * P0: architecture redesign, database, business data model, Dashboard.
  * P1: Agent system, RAG knowledge base, automatic report generation.
  * P2: user permissions.
* We refined the priority model so minimal data-scope and role modeling should be considered during P0 design, even if the full permission product is implemented later.
* The user later asked to shrink P0 and do an MVP first, add simulated business data generation, and add a PRD document connecting technical direction with user value.

## Requirements

* Add a formal roadmap document under `docs/`.
* The document must be actionable enough to guide future Trellis tasks.
* The document must distinguish P0, P1, and P2 work.
* P0 must now define a smaller MVP: MVP PRD, simulated business data generation, minimal business/KPI model, Dashboard MVP, and reuse of the existing analytics flow.
* P1 must define engineering and intelligent-analysis upgrades: persistent database, migrations, modular architecture, controlled Agent system, RAG knowledge base, automatic report generation, and evaluation loop.
* P2 must define productization capabilities: full permissions, multi-tenant isolation, report subscriptions, observability, deployment, and operations.
* The document should include execution phases, deliverables, acceptance criteria, recommended task split, and items explicitly out of scope.
* The document should align with existing docs in `docs/00-重构总览.md`, `docs/01-前端重构需求.md`, `docs/02-后端重构需求.md`, `docs/03-数据重构需求.md`, `docs/04-联调方案.md`, and `docs/05-测试验收方案.md`.
* Add a product-facing MVP PRD under `docs/` that connects user value to the technical route.

## Acceptance Criteria

* [x] A new roadmap document exists under `docs/`.
* [x] The roadmap contains P0/P1/P2 priorities and explains why each belongs there.
* [x] The roadmap includes architecture, data, Dashboard, Agent, RAG, reports, permissions, testing, rollout, and risk sections.
* [x] The roadmap gives a recommended implementation sequence that can be converted into later tasks.
* [x] Existing docs are not contradicted.
* [x] P0 is narrowed to MVP instead of the full production platform.
* [x] Simulated business data generation is included as a core MVP capability.
* [x] A docs-level MVP PRD connects user value, product scope, technical route, and acceptance criteria.

## Definition of Done

* Documentation is written in UTF-8 Markdown.
* The task PRD captures the agreed scope.
* No code behavior changes are made.
* `git status` is inspected before final response.

## Technical Approach

Create and maintain `docs/07-生产化升级路线图.md`. Add `docs/08-MVP产品需求文档.md` as the product-facing PRD. Treat simulated business data, Dashboard, and data-backed analysis as the first usable MVP milestone; treat persistent database, Agent/RAG/reporting, and full permissions as staged extensions.

## Decision (ADR-lite)

**Context**: The project has passed the empty POC stage but still lacks production data, durable architecture, and product operating boundaries.

**Decision**: Use a phased roadmap centered on an MVP first: simulated business data, Dashboard, KPI semantics, and existing analysis flow. Move heavier production foundation work into P1, then controlled AI and broader productization.

**Consequences**: This avoids premature platform work before user value is validated. Some scope concepts must still be preserved early because they affect metrics and Dashboard interpretation, while full user-management UX can remain later.

## Out of Scope

* No database migration implementation in this task.
* No frontend Dashboard implementation in this task.
* No Agent/RAG/report-generation implementation in this task.
* No permissions implementation in this task.

## Technical Notes

* Existing roadmap-adjacent docs live under `docs/`.
* Current test gates are frontend lint/test/build and backend PMD/test.
* Current local working tree was clean before the task was created, with `main` ahead of `origin/main` by three commits.
* Implementation produced only documentation changes: `docs/07-生产化升级路线图.md`, `docs/README.md`, and this Trellis task PRD.
* `git diff --check` passed. Full frontend/backend tests were not rerun because no code behavior changed.
* Code-spec update reviewed via `trellis-update-spec`; no `.trellis/spec/` update is needed because this task did not add executable contracts, APIs, schema, env wiring, or code conventions.
* Follow-up edit added `docs/08-MVP产品需求文档.md`, narrowed the roadmap P0 to MVP, updated `docs/06-数据导入MVP实施说明.md` to describe simulated business data generation and business-story requirements, and added a latest-priority note to `docs/00-重构总览.md`.
