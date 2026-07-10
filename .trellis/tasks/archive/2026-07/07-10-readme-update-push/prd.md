# Update README And Push Remote

## Goal

Update the project README so it reflects the latest dealer analytics answer-quality work, especially evidence-bound wording, analysis metadata, confidence/limitation display, and variable follow-up questions. Commit the documentation update and push it to the remote repository.

## Requirements

- Document that analytics streaming can emit structured `analysis_metadata` before the Markdown body.
- Mention the frontend analysis-lens banner with scope, metric lens, data sources, limitations, and confidence.
- Replace fixed "two follow-up questions" wording with the current 0-2 follow-up behavior.
- Keep README implementation references aligned with the current backend and frontend files.
- Push the final commits to `origin/main`.

## Acceptance Criteria

- [ ] README lists `analysis_metadata` in the SSE event table.
- [ ] README describes evidence boundaries, limitations, confidence, and 0-2 follow-ups.
- [ ] README key file table includes the metadata-related backend/frontend implementation files.
- [ ] Documentation sanity checks pass.
- [ ] Changes are committed and pushed to the remote repository.

## Definition Of Done

- README updated in UTF-8.
- No unrelated files are changed in the work commit.
- Trellis task is archived and the session journal records the work.
- Remote `origin/main` contains the completed commits.

## Technical Approach

This is a docs-only task. Update README text to describe existing behavior from the already-implemented analytics metadata feature; do not change runtime code.

## Out Of Scope

- Changing backend or frontend behavior.
- Re-running full backend/frontend test suites unless a docs change unexpectedly touches code.
- Adding new product capabilities beyond documentation.

## Technical Notes

- Current README still documents fixed two follow-ups and omits the `analysis_metadata` SSE event.
- Backend contract is captured in `.trellis/spec/backend/quality-guidelines.md` under "Analysis Metadata SSE Contract".
- Relevant files include `ChatService`, `SseEventWriter`, `AnalyticsMetadata`, `useChat`, and `AssistantMessage`.
