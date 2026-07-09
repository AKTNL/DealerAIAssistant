# PRD: Stabilize chart-json frontend CI test

## Problem

The GitHub Actions frontend job fails in `AssistantMessage.spec.js` while waiting for `.chart-json-panel` to appear from the lazy `MermaidChartAdapter`.

The parent component test is intended to verify that `AssistantMessage` discovers rendered `chart-json` fences and wires their raw JSON into the lazy adapter. It should not depend on the real adapter's ECharts initialization timing, which is already covered by `MermaidChartAdapter.spec.js`.

## Scope

- Make the `AssistantMessage` chart-json test deterministic in CI.
- Keep the test focused on the parent component contract:
  - the original `.chart-json-block` placeholder exists;
  - the lazy adapter receives the raw JSON from the rendered markdown block;
  - the adapter output appears after the placeholder;
  - Mermaid rendering is not triggered for chart-json fences.
- Do not change production component behavior.

## Verification

- Run the targeted `AssistantMessage.spec.js` test file locally.
- Run the full frontend test suite locally.
- Push the fix and re-check GitHub Actions until the frontend job passes.
