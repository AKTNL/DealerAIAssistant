# Accuracy Workbook Regression

## Goal

Add an automated backend regression test that treats the existing accuracy workbook as a runtime contract. The test should catch data import, deterministic analytics routing, and answer-content regressions before they reach the demo.

## Requirements

* Load `mockservice/DealerAIAssistant_*.xlsx` from the repository during backend tests.
* Verify the workbook baseline: sheet `测试题库`, the expected four-column header, and 51 question rows.
* Start the backend with an isolated H2 database and import the classpath sample data through the existing startup import path.
* Verify imported baseline counts for dealers, targets, opportunities, leads, tasks, and campaigns before asking questions.
* Run the original workbook question set through `ChatService.chat(...)` with empty model settings so answers come from deterministic rule-based analytics.
* Assert key hit points from the workbook scoring column with flexible content checks, not full-answer equality.
* Include a small anti-overfit paraphrase set that asks equivalent business questions with different wording and asserts the same core facts.
* Keep boundary questions covered: unknown customer/dealer, out-of-scope questions, and assistant identity/greeting should not fabricate business data.

## Acceptance Criteria

* [ ] A new backend regression test reads the real accuracy workbook.
* [ ] The test fails if workbook row count or imported sample-data counts drift unexpectedly.
* [ ] Original workbook questions are exercised through the chat service without external model calls.
* [ ] Assertions verify required numeric/name/business-scope hits while avoiding brittle full-response matching.
* [ ] Anti-overfit paraphrases are exercised separately from the original workbook wording.
* [ ] Targeted Maven test command passes with `-Dfrontend.skip=true`.

## Definition of Done

* Tests added under the backend test tree following existing package conventions.
* No production shortcut that matches exact workbook question text to fixed answers.
* Existing analytics/import code is changed only if the regression exposes a real semantic gap.
* Backend Maven tests pass, at least for the targeted regression class and preferably for the full backend suite.
* Trellis quality check and finish workflow are followed.

## Technical Approach

Use a Spring Boot integration-style JUnit test because the contract spans workbook import, JPA repositories, analytics routing, and `ChatService` fallback behavior. The test will:

1. Run the application with a unique H2 database.
2. Let `ExcelImportService` import `backend/src/main/resources/Sample Data.xlsx`.
3. Read the mockservice accuracy workbook with Apache POI, already available through `poi-ooxml`.
4. Build per-row expectations from the workbook category, question, standard answer, and scoring reference.
5. Call `ChatService.chat(new ChatRequest(sessionId, question, "", "", ""))`.
6. Check normalized replies for expected tokens, counts, percentages, and boundary behavior.

## Decision (ADR-lite)

**Context**: Accuracy questions are a cross-layer contract. A pure mocked unit test would not catch import-count drift, repository-backed aggregations, or `ChatService` business-scope routing.

**Decision**: Add an integration regression that uses the real workbook and real imported sample data, while keeping assertions flexible enough to tolerate harmless prose changes.

**Consequences**: The test will be heavier than a unit test, but it exercises the behavior users actually see and aligns with the backend quality guideline requiring runtime workbook regression and anti-overfit paraphrases.

## Out of Scope

* Calling external LLM providers during tests.
* Recreating the workbook's manual scoring template.
* Requiring full generated replies to equal `标准答案`.
* Creating a separate CLI or CI workflow entrypoint beyond Maven tests.
* Modifying the workbook content.

## Technical Notes

* Accuracy workbook: `mockservice/DealerAIAssistant_准确率测试题库.xlsx`.
* Runtime sample data workbook: `backend/src/main/resources/Sample Data.xlsx`.
* Relevant specs:
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/backend/database-guidelines.md`
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/guides/code-reuse-thinking-guide.md`
  * `.trellis/spec/guides/cross-layer-thinking-guide.md`
* Existing related tests:
  * `backend/src/test/java/com/brand/agentpoc/AgentPocApplicationStartupTest.java`
  * `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java`
  * `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`
