# Project Engineering And Security Hardening

## Goal

Improve the project hygiene found during the repository review so the dealer AI analysis assistant is safer to run outside a local demo, easier to build repeatably, and easier to maintain as analytics scenarios grow.

## What I Already Know

* The current branch is `main`.
* The working tree was clean before this task was created.
* Frontend verification is currently green: `npm test` passes with 30 test files and 202 tests.
* Backend verification is currently green when run with PowerShell-safe quoting: `mvn "-Dfrontend.skip=true" test` passes with 263 tests.
* `backend/src/main/resources/application.yml` still provides local demo credential defaults: `demo123`, `demo123-session-secret-at-least-32-chars`, and `demo123-api-key`.
* README says real credentials should be configured explicitly and should not be committed.
* `frontend/vite.config.js` builds directly into `backend/src/main/resources/static` with `emptyOutDir: true`.
* Git tracks selected backend static files under `backend/src/main/resources/static`, including `index.html`, `openapi.json`, `logo.png`, and `background.jpg`; the generated `assets/` directory is ignored.
* The biggest maintainability hotspots observed are:
  * `RuleBasedAnalyticsService.java` at roughly 5442 lines.
  * `ChatService.java` at roughly 1320 lines.
  * `frontend/src/style.css` at roughly 2481 lines.
* Backend Trellis specs are incomplete: backend directory structure, error handling, quality, and logging guidelines are still marked `To fill`.
* CI exists and runs backend tests plus frontend tests/build, but there is no visible lint/format/coverage/static-analysis gate yet.

## Assumptions

* This task should prioritize high-confidence, low-regression engineering improvements before deeper architectural refactors.
* The first implementation slice should preserve the local demo developer experience, but demo defaults should be explicit and local-only rather than the production-facing default.
* The user chose to include large service decomposition in this task, accepting a larger scope and higher regression risk.
* The user chose the aggressive decomposition depth: refactor both `RuleBasedAnalyticsService` and `ChatService` orchestration boundaries, not only pure helper extraction.

## Requirements

* Remove unsafe credential defaults from the main backend application configuration.
* Preserve an easy local demo path through documented environment variables or a local-only profile/configuration.
* Update tests that currently assert demo credentials in `application.yml`.
* Make the frontend build output strategy safe for tracked backend static files, especially `openapi.json`.
* Start decomposing the largest backend services along existing responsibility boundaries.
* Refactor both `RuleBasedAnalyticsService` and `ChatService` enough that their main orchestration responsibilities are clearer and meaningful logic is delegated to focused collaborators.
* Preserve current analytics behavior while moving code; refactors must be covered by existing regression tests and targeted new tests where behavior boundaries become explicit.
* Keep backend and frontend test suites green.
* Document the resulting startup/build expectations where needed.

## Acceptance Criteria

* [ ] `application.yml` no longer defaults access key, session secret, or internal API key to demo credentials.
* [ ] Tests assert the safer default credential contract.
* [ ] Local demo instructions still show how to run the app with explicit credentials.
* [ ] Vite build no longer risks deleting backend-owned static files such as `openapi.json`.
* [ ] `RuleBasedAnalyticsService` is smaller and delegates at least two coherent responsibilities to focused collaborators.
* [ ] `ChatService` is smaller or has clearer orchestration collaborators for streaming/model/reply-validation responsibilities.
* [ ] Refactoring preserves public service APIs used by controllers and tests unless a small signature change is fully updated across callers.
* [ ] Existing analytics and chat regression tests still pass without expected-answer changes.
* [ ] `npm test` passes.
* [ ] `mvn "-Dfrontend.skip=true" test` passes.
* [ ] Any changed docs/spec notes match the implemented behavior.

## Definition Of Done

* Tests added or updated for changed behavior.
* Frontend and backend test commands pass locally.
* CI-facing commands remain compatible with Windows and GitHub Actions.
* No unrelated refactors or generated asset churn are included.
* Follow-up work is recorded if service decomposition or static-analysis tooling is intentionally deferred.

## Technical Approach

Confirmed scope:

* Security config: change main `application.yml` defaults for `app.auth.access-key`, `app.auth.session-secret`, and `app.security.api-key` to empty values. Keep explicit local examples in README, and optionally add a Git-ignored `application-local.yml` example if needed.
* Build output: stop treating `backend/src/main/resources/static` as a disposable Vite output directory. Prefer generating frontend assets into a dedicated ignored subdirectory or staging directory while preserving backend-owned static resources.
* Backend decomposition: perform aggressive but behavior-preserving orchestration refactoring in both large services. Candidate seams from current code inspection:
  * Move chart and enriched-report rendering helpers out of `RuleBasedAnalyticsService`.
  * Move scope/time/topic detection helpers out of `RuleBasedAnalyticsService`.
  * Move stream event writing, configured model call orchestration, or reply validation helpers out of `ChatService`.
  * Prefer package-private or constructor-injected collaborators where they improve testability without expanding the public API unnecessarily.
* Tests: update startup/config tests to assert the new contract and add a regression around static build preservation if practical.

Potential follow-up:

* Continue decomposing scenario-specific analytics into dedicated classes after the first boundary-preserving extraction is stable.

## Decision (ADR-lite)

**Context**: The project already has strong regression coverage, but maintainability risk is concentrated in large services. The user wants the first hardening pass to include service decomposition rather than only config/build fixes.

**Decision**: Include aggressive service decomposition in this task, touching both `RuleBasedAnalyticsService` and `ChatService`, but require behavior-preserving steps guarded by tests.

**Consequences**: The task will take longer and require broader backend tests. To control risk, the implementation should avoid changing analytics formulas, answer wording, or SSE event contracts unless a test exposes an existing defect. If a refactor path starts requiring semantic rewrites, stop and keep the narrower extraction that is already test-green rather than forcing the whole rewrite.

## Open Questions

* None.

## Out Of Scope

* Replacing H2 with a production database.
* Introducing a full user account/SSO system.
* Changing the core analytics formulas or answer content.
* Broad UI redesign.
* Adding every possible static-analysis tool in one pass.
* Rewriting analytics formulas or changing expected workbook answers as part of refactoring.

## Technical Notes

* Relevant files:
  * `backend/src/main/resources/application.yml`
  * `backend/src/main/resources/application-prod.yml`
  * `backend/src/test/java/com/brand/agentpoc/AgentPocApplicationStartupTest.java`
  * `frontend/vite.config.js`
  * `.github/workflows/ci.yml`
  * `README.md`
* Verification commands:
  * `cd frontend && npm test`
  * `cd backend && mvn "-Dfrontend.skip=true" test`
* PowerShell note: backend Maven property arguments need quoting, as in `mvn "-Dfrontend.skip=true" test`.
