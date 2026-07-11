# Field-Aware Data Cleaning and Import Quality

## Goal

Make Excel ingestion trustworthy by cleaning values before persistence, applying field-specific missing-data rules, and exposing an import quality summary. Missing values must not be converted to zero when that would change business meaning or produce misleading KPIs.

## What I Already Know

- The application imports dealer, target, opportunity, campaign, task, and lead sheets at startup.
- Current parsing already normalizes some categorical fields to `未知` or `未分配` and skips rows missing required identifiers or dates.
- Current campaign parsing defaults several missing numeric fields to `0`, including target and actual values.
- Current target parsing defaults missing `asKTarget` and `opportunityCreateCount` to `0`.
- If workbook import fails or yields no usable rows, the application silently seeds built-in sample data.
- The user prefers cleaning first, followed by field-aware use of `0`, `未知`, or an uncomputable state.

## Assumptions

- `0` is valid only when the source/business rule establishes that blank means no event occurred.
- Missing target values must remain distinguishable from true zero targets.
- Unknown categorical values may be stored as a dedicated display bucket but must be reported as normalized data.
- Rows missing required identifiers remain excluded rather than assigned synthetic identities.
- Missing optional analytical dimensions, including opportunity expected-close dates and target denominators, do not invalidate otherwise usable observed facts.
- The MVP can remain backend-focused; a full data-quality dashboard is not required.
- Demo mode may fall back to built-in sample data only when the fallback is clearly reported.
- Strict/production mode must fail startup when the configured workbook is unavailable, invalid, or produces no usable business data.

## Open Questions

- None.

## Requirements (Evolving)

- Normalize whitespace, textual null markers, numeric formats, dates, booleans, and supported categorical aliases before entity creation.
- Classify fields as required identifiers, targets/denominators, observed counts, categories, dates, or derived values.
- Never blanket-fill numeric fields with zero.
- Preserve missing targets/denominators as unavailable so achievement and conversion metrics can report `cannot calculate` instead of false zero values.
- Fill observed counts with zero only where a documented source rule confirms blank means no occurrence.
- Map optional unknown categories to `未知` or `未分配` and count each normalization.
- Skip or quarantine rows that lack required identifiers or required analytical dimensions.
- Produce an import quality summary with processed, imported, normalized, skipped, and failed counts plus reasons by sheet/field.
- Add an explicit import mode: demo mode permits a clearly reported sample-data fallback; strict/production mode fails startup instead of silently substituting sample data.
- Preserve campaign rows with unavailable target denominators, but exclude those rows only from calculations that require the missing denominator.
- Preserve target rows with unavailable target denominators so observed create/win counts remain queryable; exclude those rows only from achievement-rate cohorts.
- Replace categorical `"0"` defaults with the explicit unknown bucket.
- Remove guessed date imputation; missing required dates cause row rejection, while optional dates such as opportunity expected-close dates remain unavailable and are excluded only from analyses that require them.
- Expose a lightweight authenticated data-status endpoint and show a compact frontend warning only when built-in fallback data is active.
- Preserve the existing sample workbook regression baseline unless a corrected data rule intentionally changes an expected result.

## Acceptance Criteria (Evolving)

- [x] Missing targets are not silently interpreted as zero targets.
- [x] Missing observed counts follow explicit per-field rules covered by tests.
- [x] Missing optional categories are grouped separately and reported as normalized.
- [x] Invalid required fields cause a row-level rejection with a reason count.
- [x] Import completion logs contain per-sheet quality totals and overall totals.
- [x] Workbook failure behavior is explicit and covered by profile/configuration tests.
- [x] Demo fallback is visibly distinguishable in logs/quality status from a successful configured-workbook import.
- [x] Strict/production mode cannot start with missing, invalid, or empty configured workbook data.
- [x] Campaign rate calculations exclude unavailable denominators without dropping otherwise useful campaign rows.
- [x] Target achievement rates pair each numerator only with rows whose target denominator is available, while total observed create/win counts still include target rows with unavailable denominators.
- [x] Opportunities with unavailable expected-close dates remain available for funnel, dealer, task-linkage, and created-date analysis.
- [x] True zero and unavailable numeric values remain distinguishable through persistence, API responses, and analytics calculations.
- [x] The chat workspace shows a warning when sample fallback data is active and remains unchanged for a successful workbook import.
- [x] Existing backend tests and accuracy workbook regression pass, with expected updates documented if semantics change.
- [x] PMD and frontend checks remain green when applicable.

## Definition of Done

- Tests added or updated for every changed missing-data rule.
- Import quality reporting has deterministic assertions.
- Lint, PMD, and backend tests pass.
- README and technical/data-flow documentation describe cleaning, fallback, and missing-value semantics.
- Rollback is possible by restoring the previous import policy configuration.

## Out of Scope

- Manual upload UI or a full data-quality dashboard.
- Automatic statistical imputation such as mean, median, interpolation, or model-based filling.
- Replacing H2 or redesigning the persistence model for production.
- Correcting arbitrary source workbooks in place.
- A detailed per-row frontend remediation interface.
- Statistical or AI-based imputation.

## Technical Notes

- Primary implementation: `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java`.
- Primary tests: `backend/src/test/java/com/brand/agentpoc/service/ExcelImportServiceTest.java` and `AccuracyWorkbookRegressionTest.java`.
- Configuration: `AppProperties.java`, `application.yml`, and `application-prod.yml` if profile-aware strictness is selected.
- Existing design history: `docs/design/2026-05-20-backend-report-import-improvements-design.md`.
- Existing campaign defaults are concentrated around `ExcelImportService.parseCampaignSheet`.
- Existing target zero defaults are concentrated around `ExcelImportService.parseTargetSheet`.
- Workbook profile: [`research/workbook-missingness.md`](research/workbook-missingness.md).

## Technical Approach

- Introduce an import-quality collector/report shared by sheet parsers and published through a small status service.
- Use nullable `Integer` values already supported by target/campaign entities to preserve unavailable numeric fields; do not add synthetic zeroes or a parallel flag for every field.
- Add null-safe aggregation helpers and valid-sample filtering in rule-based analytics and analytics API calculations.
- Treat target and campaign denominators as metric-specific optional values. Preserve their rows, aggregate observed facts independently, and calculate rates only over comparable rows containing both the denominator and its paired numerator.
- Normalize optional categories to `未知`/`未分配`; keep derived campaign name fallback to campaign ID.
- Add `app.excel.fallback-enabled`, defaulting to `true` for demo/local use and overridden to `false` in `application-prod.yml`.
- Validate presence of the five required workbook sheets (`AE Target Data`, `Opportunity`, `Lead`, `Task`, `Campaign`) before persistence.
- Publish the latest import source and quality summary through an authenticated endpoint and render a compact fallback warning in the chat workspace.

## Decision (ADR-lite)

**Context**: The POC needs convenient sample-data startup, but silent fallback is unsafe once the same build is used with real business workbooks.

**Decision**: Use profile/configuration-aware behavior. Demo mode may seed built-in sample data after a failed import and must report that source explicitly. Strict/production mode fails startup for missing, invalid, or empty workbook input.

**Consequences**: Local demonstration remains easy, while production cannot unknowingly analyze sample data. Configuration and startup tests are required for both modes.

## Expansion Sweep

- **Future evolution**: the report structure should support later import history and manual upload without building those features now.
- **Related scenarios**: API details and rule-based reports must use the same unavailable-value semantics.
- **Failure/edge cases**: missing sheets, duplicate identifiers, invalid numeric/date values, partial workbook content, and fallback persistence must be deterministic and transactional.
