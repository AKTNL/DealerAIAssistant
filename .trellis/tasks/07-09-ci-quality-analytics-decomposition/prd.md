# CI Quality Gate And Analytics Service Decomposition

## Goal

Add a stronger, repeatable quality gate around the existing backend/frontend checks and continue reducing the maintainability risk in `RuleBasedAnalyticsService` without changing analytics behavior.

## What I Already Know

* The user approved the recommended next slice: CI quality gate plus continued `RuleBasedAnalyticsService` decomposition.
* The user selected the broader scope option: frontend ESLint, backend static analysis, and analytics decomposition in the same task.
* The user selected the Checkstyle/PMD family for backend static analysis instead of SpotBugs.
* The working tree was clean before this task was created.
* Existing CI has two jobs:
  * Backend: `mvn -B -ntp -Dfrontend.skip=true test`
  * Frontend: `npm ci --no-audit --no-fund`, `npm test`, `npm run build`
* Frontend has no configured linter or formatter today; this is also recorded in `.trellis/spec/frontend/quality-guidelines.md`.
* Backend `pom.xml` has no Checkstyle, SpotBugs, PMD, JaCoCo, or Spotless plugins today.
* `RuleBasedAnalyticsService.java` is still very large after the previous extraction, at about 5900 lines.
* Current analytics responsibilities still concentrated in `RuleBasedAnalyticsService` include:
  * topic/scope/time detection
  * scenario orchestration
  * target/opportunity/task/campaign/lead/data-overview/dealer-benchmark analysis
  * direct-question answers
  * low-confidence and fallback reply construction
* Previous task already extracted `AnalyticsChartRenderer` and `AnalyticsReportComposer`, so the next safe seam should build on existing `service.analytics` patterns.
* `backend/src/main/resources/static/assets/` is ignored by `.gitignore` and not tracked by git.

## Assumptions

* Behavior preservation is more important than line-count reduction.
* The first quality-gate slice should avoid creating a noisy formatting-only diff across the whole codebase unless the user explicitly wants that.
* The service decomposition should move one coherent scenario/helper boundary at a time and rely on existing regression tests.

## Requirements

* Add a frontend ESLint quality gate.
* Add a backend static-analysis quality gate using PMD only.
* Keep the new quality gate compatible with GitHub Actions and local Windows/PowerShell usage.
* Continue decomposing `RuleBasedAnalyticsService` along an existing responsibility boundary.
* Preserve public API contracts and analytics expected answers.
* Add or update tests only where the extracted boundary needs explicit coverage.
* Keep frontend and backend test suites green.

## Acceptance Criteria

* [x] CI runs the selected new quality command(s) before tests or as a dedicated step.
* [x] The selected quality command(s) are also exposed as local package/build scripts or Maven goals.
* [x] `RuleBasedAnalyticsService` delegates at least one additional coherent responsibility to a focused collaborator.
* [x] Existing analytics and chat regression tests pass without expected-answer changes.
* [x] `npm test` passes.
* [x] `npm run build` passes.
* [x] `mvn "-Dfrontend.skip=true" test` passes.
* [x] Changed Trellis specs/docs accurately describe any new quality command or decomposition convention.

## Definition Of Done

* Tests added or updated where behavior boundaries become explicit.
* Local verification commands pass.
* GitHub Actions workflow is still readable and fails fast on quality problems.
* No broad formatting churn unless it is intentionally in scope.
* Any deferred quality/decomposition work is captured as follow-up.

## Technical Approach

Candidate quality-gate options:

* Frontend lint-only gate: add ESLint for Vue/JS, script it as `npm run lint`, and call it in CI.
* Backend static-analysis gate: add a Maven plugin such as Checkstyle, SpotBugs, PMD, or JaCoCo, then call it in CI.
* Hybrid conservative gate: add a lightweight frontend lint gate first and keep backend static analysis as follow-up to avoid a noisy first pass.

Candidate decomposition seams:

* Extract topic/scope/time detection from `RuleBasedAnalyticsService` into `service.analytics`.
* Extract one scenario family, likely campaign or lead source, if the dependent data/cache surface can stay small.
* Extract direct-question answer helpers if they can reuse `DirectQuestionMatcher` cleanly without changing routing order.

Recommended MVP:

* Add frontend ESLint with flat config and Vue rules.
* Add a backend PMD gate as the selected static-analysis tool.
* Do not add Checkstyle in this task.
* Extract topic/scope/time detection or another low-risk helper boundary from `RuleBasedAnalyticsService`, not a full scenario rewrite.

## Open Questions

* None.

## Research References

* [`research-static-analysis.md`](research-static-analysis.md) - ESLint flat config plus PMD/Checkstyle trade-offs for backend static analysis.

## Decision (ADR-lite)

**Context**: The project currently relies on tests/builds only. Frontend has no lint command; backend has no static-analysis plugin. The user selected the broader quality-gate scope, chose the Checkstyle/PMD family instead of SpotBugs, then selected PMD only.

**Decision**: Add frontend ESLint using flat config. Add backend PMD only as the Maven static-analysis gate. Avoid broad formatting churn and do not add Checkstyle in this task.

**Consequences**: CI becomes stricter and can catch issues earlier. PMD may surface existing findings that require focused fixes, rule tuning, or explicit exclusions. Checkstyle remains a future follow-up if the team later wants style enforcement.

## Out Of Scope

* Rewriting analytics formulas or expected workbook answers.
* Broad UI redesign.
* Whole-codebase formatting churn.
* Migrating production data storage.
* Introducing multiple backend static-analysis tools at once unless explicitly selected.

## Technical Notes

* Relevant files:
  * `.github/workflows/ci.yml`
  * `frontend/package.json`
  * `frontend/package-lock.json`
  * `frontend/vite.config.js`
  * `backend/pom.xml`
  * `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
  * `backend/src/main/java/com/brand/agentpoc/service/analytics/`
  * `.trellis/spec/frontend/quality-guidelines.md`
  * `.trellis/spec/backend/quality-guidelines.md`
* Verification commands:
  * `cd frontend && npm test`
  * `cd frontend && npm run build`
  * `cd backend && mvn "-Dfrontend.skip=true" test`
* PowerShell note: quote Maven system properties, e.g. `mvn "-Dfrontend.skip=true" test`.

## Implementation Summary

* Added frontend ESLint flat config and `npm run lint`.
* Added backend PMD-only gate with a project-owned ruleset at `backend/config/pmd-ruleset.xml`.
* Updated CI so frontend lint and backend PMD run before tests.
* Extracted analytics topic classification into `service.analytics.AnalyticsTopicClassifier`.
* Added focused topic-classifier tests for priority-sensitive routes.
* Updated frontend/backend Trellis quality specs with executable lint/PMD contracts.
