# Align POC Implementation With Requirements Document

## Goal

Bring the current dealer AI analysis assistant POC closer to `docs/Agent-POC-需求文档.docx` for acceptance, while preserving useful enhancements that already exceed the original POC baseline. The intent is not to roll the project back to the old document, but to remove clear behavior mismatches and document intentional deviations.

## What I Already Know

* The requirements document allows one AI chat route only: Spring AI or LangChain4j. The current project has implemented the Spring AI route, which satisfies the two-option requirement.
* Core baseline capabilities are already present: password-style access gate, Spring AI chat endpoints, SSE streaming, H2/Excel data import, six `/api/v1/data/*` datasets, Spring AI `@Tool` wrappers, Markdown rendering, code highlighting, `<think>` parsing, follow-up buttons, and zh/en UI switching.
* Existing tests are green from the audit run:
  * Frontend: `npm test` passed with 30 test files and 205 tests.
  * Backend: `mvn "-Dfrontend.skip=true" test` passed with 269 tests.
* Strict document mismatches found during audit:
  * Example sidebar currently starts expanded and clicking a sample prompt submits immediately. The document says the sidebar should default hidden and clicking a sample question should fill the chat input.
  * Backend `application.yml` has environment-backed config for server port, access key, API key, Excel path, and model host safety, but does not expose default OpenAI-compatible `base-url`, `api-key`, or `model` as backend config. Current model settings are browser-local and sent with chat requests.
  * Analytics replies are allowed to omit follow-up questions when complete. The document says the answer should append two follow-up suggestions.
  * `/api/model-config/**` is protected by login session token rather than `X-API-Key`. This appears to be an intentional security enhancement, not necessarily a bug.
* The project has grown beyond the original POC baseline with model settings UI, session tokens, analytics metadata, chart rendering, rule-based fallback, accuracy workbook tests, and stricter model-output guardrails.

## Assumptions

* We should treat `docs/Agent-POC-需求文档.docx` as the acceptance baseline, but not remove current enhancements unless they conflict with acceptance.
* We should preserve the Spring AI route and not add LangChain4j in this task.
* We should keep the current session-token security model for chat/model settings unless the user explicitly asks to revert to the older API-key-only design.

## Requirements

* Align the example sidebar behavior with the requirement document:
  * Sidebar is hidden/collapsed by default on the main workspace.
  * Clicking a sample question populates the current chat input instead of immediately sending it.
  * User can still send manually with Enter or the send button.
* Add backend-config support for default OpenAI-compatible model settings:
  * Add backend config keys and environment variable overrides for model `base-url`, `api-key`, and `model`.
  * Preserve the existing browser-local model settings panel as an override or convenience layer.
  * Keep existing model host validation and private-host safety behavior.
* Clarify follow-up behavior:
  * Keep the current analytics guardrail: analytics reports may include 0-2 follow-up questions when useful.
  * Document this as an intentional deviation from the original POC baseline, because forcing two follow-ups can make complete answers noisier.
* Clarify security documentation:
  * Document why `/api/model-config/**` uses login session auth rather than `X-API-Key`, unless implementation is explicitly changed.
* Keep current successful Spring AI route, rule-based analytics fallback, chart rendering, and test coverage intact.

## Acceptance Criteria

* [x] On first entering the authenticated workspace, the example question sidebar is collapsed/hidden by default.
* [x] Selecting a sample question fills the input composer without auto-submitting a chat request.
* [x] Existing manual send flows still work: Enter sends, Shift+Enter inserts a newline, send button sends.
* [x] Backend config supports environment overrides for OpenAI-compatible model `base-url`, `api-key`, and `model`.
* [x] Chat requests still allow browser-local model settings to override or supply model credentials.
* [x] Follow-up behavior is documented as an intentional deviation: analytics reports use 0-2 relevant follow-ups instead of forcing exactly two.
* [x] Security behavior for `/api/model-config/**` is either changed intentionally or documented as an intentional deviation.
* [x] Frontend tests pass.
* [x] Backend tests pass.

## Definition of Done

* Tests added or updated for changed frontend and backend behavior.
* Frontend `npm test` passes.
* Backend `mvn "-Dfrontend.skip=true" test` passes.
* Documentation or notes updated for intentional deviations from the original requirements document.
* No existing enhanced POC behavior is removed accidentally.

## Decision (ADR-lite)

**Context**: The original POC document says AI replies should end with two follow-up suggestions, but the current implementation already has guardrails that allow analytics answers to omit follow-ups when the answer is complete.

**Decision**: Keep the current analytics behavior of 0-2 relevant follow-ups, document it as an intentional deviation, and do not force exactly two follow-ups in this task.

**Consequences**: This preserves answer quality and avoids noisy suggestions, but acceptance notes must explicitly call out the deviation from the Word document.

## Out of Scope

* Adding LangChain4j endpoints or a second chat route.
* Replacing the Spring AI route.
* Removing session-token authentication.
* Production-grade RBAC, SSO, data permissions, persistent chat history, or external CRM/DMS/CDP integration.
* Rewriting the whole UI to match the Word document wording exactly.

## Technical Notes

* Requirements source: `docs/Agent-POC-需求文档.docx`.
* Likely frontend files:
  * `frontend/src/views/ChatView.vue`
  * `frontend/src/components/layout/ExampleSidebar.vue`
  * `frontend/src/components/chat/ChatInput.vue`
  * `frontend/src/composables/useChat.js`
  * related tests under `frontend/src/**/__tests__/`
* Likely backend files:
  * `backend/src/main/resources/application.yml`
  * `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`
  * `backend/src/main/java/com/brand/agentpoc/service/ModelConfigService.java`
  * `backend/src/main/java/com/brand/agentpoc/service/ChatService.java`
  * related backend tests.
* Relevant existing evidence:
  * `frontend/src/views/ChatView.vue` currently sets `sidebarCollapsed = ref(false)`.
  * `frontend/src/views/ChatView.vue` currently sends sidebar prompts through `submitPrompt(prompt)`.
  * `backend/src/main/resources/application.yml` currently exposes `app.model.allowed-hosts` and `app.model.allow-private-hosts`, but not default model credentials.
  * `backend/src/main/java/com/brand/agentpoc/service/ChatReplyGuard.java` currently permits analytics replies without a follow-up block.

## Verification

* `frontend`: `npm run lint` passed.
* `frontend`: `npm test` passed with 30 test files and 207 tests.
* `backend`: `mvn "-Dfrontend.skip=true" pmd:check` passed.
* `backend`: `mvn "-Dfrontend.skip=true" test` passed with 273 tests.
