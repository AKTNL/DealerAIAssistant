## Bug Analysis: chart-json frontend CI flake

### 1. Root Cause Category
- **Category**: D/E - Test Coverage Gap plus Implicit Assumption
- **Specific Cause**: The parent `AssistantMessage` test depended on the real lazy `MermaidChartAdapter` resolving and initializing ECharts quickly enough in CI. The contract under test was only that chart-json markdown blocks are discovered and passed to the lazy adapter.

### 2. Why Fixes Failed
1. Waiting longer for `.chart-json-panel`: this still tested the real child adapter's async timing from the parent test.
2. Keeping the ECharts assertion in the parent test: this mixed the parent wiring contract with child rendering behavior that is already covered by `MermaidChartAdapter.spec.js`.

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Documentation | Document parent-test module mocks for `defineAsyncComponent` children, including `__esModule: true` for mocked `.vue` dynamic imports. | DONE |
| P1 | Test boundary | Keep heavy child rendering behavior in the child's own spec and assert parent-to-child props/DOM placement in parent tests. | DONE |

### 4. Systematic Expansion
- **Similar Issues**: Parent tests for lazy Mermaid/ECharts or future heavy adapters can hit the same CI timing gap.
- **Design Improvement**: No production change needed; the component boundary is already good.
- **Process Improvement**: When stabilizing CI tests, first decide whether the failing assertion belongs to the parent or child component contract.

### 5. Knowledge Capture
- [x] Updated `.trellis/spec/frontend/quality-guidelines.md` with the lazy child mock pattern.
