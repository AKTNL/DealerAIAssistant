# Refactor RuleBasedAnalyticsService

## Goal

Reduce the size and maintenance risk of `RuleBasedAnalyticsService` without changing analytics behavior. The first MVP pass should extract a low-risk, pure helper from the large service so future scenario-level refactors can be done in smaller, safer steps.

## What I Already Know

* The user agreed to start with splitting `RuleBasedAnalyticsService`.
* `RuleBasedAnalyticsService.java` is over 5,600 lines and mixes orchestration, topic detection, scope detection, deterministic scenario analysis, direct question matching, data quality handling, aggregation helpers, and report rendering glue.
* Backend tests currently pass: `mvn "-Dfrontend.skip=true" test` ran 254 tests with 0 failures before this task.
* Frontend tests also passed before this task, but this refactor is backend-only.
* The backend spec says large service helpers should live under `service/analytics/`; this package already contains `AnalyticsCalculator` and `ReportRenderer`.
* A low-risk first extraction is the direct-question matching logic: `isDirectTargetQuestion`, `isDirectOpportunityQuestion`, `isDirectLeadQuestion`, `isDirectTaskQuestion`, `isDirectCampaignQuestion`, `asksTopSalesVolume`, `asksHighestRate`, `asksLowestRate`, `asksWinRate`, `mentionsProductDimension`, `mentionsDealerDimension`, `asksBreakdown`, `asksStatusBreakdown`, `asksOpportunityStageBreakdown`, `asksOpportunitySourceBreakdown`, `asksTaskSubjectBreakdown`, and `requestedTopLimit`.
* These helpers are pure string predicates and do not depend on repositories, Spring state, `AnalysisScope`, report rendering, or database data.

## Assumptions

* The first pass should prioritize behavior preservation over maximum line-count reduction.
* The public entry point `RuleBasedAnalyticsService.plan(...)` should remain unchanged.
* Existing tests should continue to validate the end-to-end analytics behavior after extraction.

## Requirements

* Extract the direct-question matching helpers into a dedicated class under `backend/src/main/java/com/brand/agentpoc/service/analytics/`.
* Keep `RuleBasedAnalyticsService` as the Spring service and preserve all current method signatures used by controllers and tests.
* Preserve exact routing semantics for target, opportunity, lead, task, campaign, and data-overview questions.
* Add focused unit tests for the extracted matcher where useful, using the existing JUnit 5 and AssertJ style.
* Do not change user-facing Markdown output, scenario mapping, data quality behavior, SSE step behavior, or repository queries in this first pass.

## Acceptance Criteria

* [x] `RuleBasedAnalyticsService.java` no longer contains the direct-question matching predicate block.
* [x] A new analytics helper owns the extracted predicate logic.
* [x] Existing `RuleBasedAnalyticsServiceTest` still passes without weakening assertions.
* [x] Any new helper tests pass.
* [x] Full backend verification passes with `mvn "-Dfrontend.skip=true" test`.
* [x] Git working tree only contains changes related to this refactor and Trellis task metadata.

## Definition of Done

* Tests added or updated where appropriate.
* Backend test suite green.
* No behavior changes outside the extraction.
* Docs or Trellis notes updated if the refactor reveals a convention worth keeping.

## Out of Scope

* Splitting scenario calculators such as target achievement, opportunity funnel, lead source, campaign performance, dealer benchmark, or dealer activity.
* Changing analytics output wording, follow-up questions, Markdown sections, or chart behavior.
* Reworking the data cache, scope detection, data quality model, or report renderer in this first pass.
* Adding CI, lint, deployment changes, or frontend work.

## Technical Approach

Start with the smallest useful extraction:

* Create `DirectQuestionMatcher` in `service.analytics` as a stateless helper with static methods.
* Move the pure direct-question predicate logic into that helper.
* Update `RuleBasedAnalyticsService` call sites to use the helper.
* Add tests for representative positive cases around target, opportunity, lead, task, campaign, and top-N detection.

## Decision (ADR-lite)

**Context**: `RuleBasedAnalyticsService` is too large, but it has many behavior-sensitive paths covered by regression tests.

**Decision**: Extract pure direct-question matching first, rather than scenario computation or data quality handling.

**Consequences**: This yields a modest line-count reduction but creates a safe pattern for later extractions. It avoids moving repository-dependent or record-heavy logic before the service boundaries are clearer.

## Technical Notes

* Read `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `logging-guidelines.md`, and `error-handling.md`.
* Inspected `RuleBasedAnalyticsService.java`, `AnalyticsCalculator.java`, `ReportRenderer.java`, and `RuleBasedAnalyticsServiceTest.java`.
* Relevant spec constraints:
  * Use `service/analytics/` for helper classes when a service grows large.
  * Keep semantic routing; do not replace it with exact-string answer shortcuts.
  * Use JUnit 5, AssertJ, and tests mirroring main source structure.
  * Prefer no behavior changes during refactors.
