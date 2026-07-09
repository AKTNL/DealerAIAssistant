# Quality Guidelines

> Code quality standards for frontend development.

---

## Overview

The project uses Vite for building, Vitest for testing, and has no configured linter or formatter. Quality is enforced through code review conventions, consistent patterns across the codebase, and a comprehensive test suite.

---

## Forbidden Patterns

### 1. No Direct localStorage/sessionStorage Access

Always use the wrapper functions in `frontend/src/utils/storage.js`:

```js
// CORRECT
import { readStorageValue, writeStorageValue, removeStorageValue } from "../utils/storage";
const locale = readStorageValue("local", STORAGE_KEYS.locale, "zh");

// WRONG
const locale = localStorage.getItem("agentpoc.locale") || "zh";
```

The only exception is `frontend/src/composables/useModelSettings.js`, which has legacy code for migration purposes.

### 2. No Raw fetch() Outside api/

All HTTP requests must go through `frontend/src/api/client.js` (the `requestJson` function) or `frontend/src/api/chat.js` (the `streamChat` function for SSE). This ensures consistent error handling, auth header injection, and URL construction.

```js
// CORRECT
import { requestJson } from "./client";
export function verifyAccessKey(key) {
  return requestJson("/api/auth/verify", { method: "POST", body: JSON.stringify({ key }) });
}

// WRONG
const response = await fetch("/api/auth/verify", { ... });
```

### 3. No Options API Components

Every component must use `<script setup>`. The Options API (`export default { data(), methods: {} }`) is not used anywhere and must not be introduced.

### 4. No Implicit Emits

Always declare emits in `defineEmits([...])`. Even though Vue 3 allows undeclared emits, the codebase always declares them explicitly.

### 5. No Hardcoded Strings for User-Facing Text

All user-visible text must come from the `dictionary` prop (which is fed by `frontend/src/i18n/messages.js`). There are zh/en translations for every string. The only exception is purely technical console messages.

```js
// CORRECT
:placeholder="dictionary.inputPlaceholder"

// WRONG
placeholder="Type your question..."
```

### 6. No Mermaid/ECharts Initialization in Parent Scope

Heavy libraries (Mermaid, ECharts) must only be initialized inside the components that use them. `MermaidChartAdapter` is lazy-loaded via `defineAsyncComponent` to avoid bundling it in the initial chunk.

### 7. No Undeclared Script Imports Without Side-Effect Awareness

Third-party CSS imports (like `highlight.js/styles/github-dark.css`) are done in `main.js` to ensure they're available globally. Component-level CSS imports would be inconsistent with the single-global-stylesheet approach.

---

## Required Patterns

### 1. Co-located Tests

Every module must have a corresponding test file in a sibling `__tests__/` directory:

```
src/composables/useAuth.js
src/composables/__tests__/useAuth.spec.js
```

Test files are named `<ModuleName>.spec.js`.

### 2. Named Exports

Always use named exports (not default exports):

```js
// CORRECT
export function useAuth({ dictionary }) { ... }
export function createSessionId() { ... }

// WRONG
export default function useAuth({ dictionary }) { ... }
```

### 3. Internal Functions Without Export

Helper functions that are only used within a module should NOT be exported:

```js
// useModelSettings.js
function parseStoredModelSettings(raw) { ... }  // NOT exported
function migrateLegacyModelSettings(raw) { ... } // NOT exported
export function readModelSettings() { ... }       // Exported (public API)
```

### 4. use `computed()` Over `watch()` for Derived Values

When a value can be derived from other reactive state, use `computed()`, not a `watch()` that sets a separate `ref()`:

```js
// CORRECT (useAuth.js)
const loginError = computed(() =>
  hasError.value ? dictionary.value.loginError : ""
);

// WRONG
const loginError = ref("");
watch(hasError, (val) => {
  loginError.value = val ? dictionary.value.loginError : "";
});
```

### 5. Cleanup in onBeforeUnmount

All timers and AbortControllers must be cleaned up:

```js
// useChat.js lines 57-64
onBeforeUnmount(() => {
  currentAbortController.value?.abort();
  if (typeof window !== "undefined") {
    window.clearTimeout(assistantRenderTimerId);
    window.clearTimeout(toastTimerId);
  }
});
```

### 6. AbortController for Cancellable Operations

All fetch/stream operations that may need cancellation must accept and honor an AbortSignal:

```js
// chat.js lines 12-21
export async function streamChat({ signal, ... }) {
  const response = await fetch(buildUrl("/api/chat/stream"), {
    ...,
    signal
  });
  ...
}
```

### 7. Storage Keys in Constants

All storage keys must be defined in `frontend/src/constants/storageKeys.js`:
```js
export const STORAGE_KEYS = {
  auth: "agentpoc.authVerified",
  locale: "agentpoc.locale",
  modelSettings: "agentpoc.modelSettings",
  session: "brand_session_id"
};
```

---

## Testing Requirements

### Test Runner: Vitest

Configured in `vite.config.js` (test config is inline, not a separate vitest config file):

```js
// vite.config.js lines 6-10
test: {
  environment: "jsdom",
  globals: true,
  setupFiles: "./src/test/setup.js"
}
```

### Test Setup

`frontend/src/test/setup.js` enables auto-unmount for all tests:
```js
import { enableAutoUnmount } from "@vue/test-utils";
enableAutoUnmount(afterEach);
```

This means tests do NOT need to manually call `wrapper.unmount()`.

### Test Structure

Tests use `describe` / `it` (or `test`) from Vitest:

```js
// ChatInput.spec.js
import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";
import ChatInput from "../chat/ChatInput.vue";

function mountChatInput(dictionary = { inputPlaceholder: "Ask a question" }) {
  return mount(ChatInput, {
    props: { dictionary, modelValue: "" }
  });
}

describe("ChatInput accessibility", () => {
  test("uses the explicit chat input label when provided", () => {
    const wrapper = mountChatInput({
      chatInputLabel: "Message the assistant",
      inputPlaceholder: "Ask a question"
    });
    expect(wrapper.find("textarea").attributes("aria-label")).toBe("Message the assistant");
  });
});
```

### Mocking

`vi.mock()` and `vi.hoisted()` are used for module-level mocking:

```js
// useAuth.spec.js
const verifyAccessKeyMock = vi.fn();
vi.mock("../../api/auth", () => ({
  verifyAccessKey: (...args) => verifyAccessKeyMock(...args)
}));

// useChat.spec.js -- hoisted mock (available before imports)
const renderMarkdownLiteMock = vi.hoisted(() =>
  vi.fn((source) => `<p>${String(source ?? "")}</p>`)
);
vi.mock("../../utils/markdown", () => ({
  renderMarkdownLite: (source) => renderMarkdownLiteMock(source)
}));
```

`vi.hoisted()` is used when the mock needs to be referenced inside a `vi.mock()` factory AND used in assertions -- it hoists the mock function above the import level.

When a parent component lazy-loads a heavy child component with `defineAsyncComponent`, keep the parent test focused on the parent contract. If the child has its own spec file, mock the child module and assert the props/DOM contract rather than depending on the child's async library initialization timing:

```js
const { adapterInputs } = vi.hoisted(() => ({ adapterInputs: [] }));

vi.mock("../chat/MermaidChartAdapter.vue", async () => {
  const { defineComponent, h } = await import("vue");

  return {
    __esModule: true,
    default: defineComponent({
      name: "MermaidChartAdapter",
      props: { rawJson: { type: String, required: true } },
      setup(props) {
        adapterInputs.push(props.rawJson);
        return () => h("section", { class: "chart-json-panel" });
      }
    })
  };
});
```

The `__esModule: true` marker is required for mocked `.vue` modules used through dynamic `import()`, otherwise Vue Test Utils can see the module namespace object instead of the component.

### Testing Composables

Composables are tested by mounting a minimal harness component that calls the composable in its `setup()`:

```js
// useChat.spec.js lines 61-78
function mountChatHarness(modelSettings, overrides = {}) {
  const Harness = defineComponent({
    setup() {
      return useChat({
        authVerified: ref(true),
        dictionary: ref(dictionary),
        locale: ref("en"),
        modelSettings,
        openModelSettings: openModelSettingsMock,
        ...overrides
      });
    },
    template: "<div />"
  });
  return mount(Harness);
}
```

### What Must Be Tested

Based on the existing test suite coverage:

- **Components**: Props rendering, event emission, accessibility attributes, edge-case states.
- **Composables**: State transitions, API call side effects, error handling paths.
- **API modules**: Request construction, error handling, auth header injection.
- **Utils**: Pure function input/output, edge cases (null, undefined, empty strings).

The project has ~30 test files covering all major modules. New code should add tests following the same patterns.

---

## Linting and Formatting

No ESLint or Prettier configuration files exist in the `frontend/` directory. The codebase relies on:
- Consistent indentation (2-space tabs observed throughout).
- Consistent semicolons at end of statements.
- Consistent single quotes for strings (not double quotes, not template literals unless interpolation is needed).
- Consistent trailing commas in multi-line objects/arrays.
- `camelCase` for variables and functions, `PascalCase` for components, `UPPER_SNAKE` for constants.

---

## Code Review Checklist

When reviewing a frontend PR, verify:

1. [ ] All user-facing strings come from `dictionary` (i18n).
2. [ ] Props are explicitly typed with `type`, `required`/`default`.
3. [ ] Emits are declared in `defineEmits()`.
4. [ ] New API calls go through `client.js` or `chat.js`, not raw `fetch()`.
5. [ ] Storage access uses `utils/storage.js` wrappers.
6. [ ] New storage keys are added to `constants/storageKeys.js`.
7. [ ] Timers and controllers are cleaned up in `onBeforeUnmount`.
8. [ ] Test file exists in co-located `__tests__/` directory.
9. [ ] Tests use the project's mount helpers and mocking patterns.
10. [ ] `use*` composables follow the factory-function pattern.
11. [ ] Error states are handled (loading, empty, error, edge cases).
12. [ ] No hardcoded CSS values -- use CSS custom properties from `style.css` when possible.

---

## Common Mistakes

1. **Forgetting to add i18n keys.** Adding new UI text to a component without adding the corresponding keys to `frontend/src/i18n/messages.js` for both `zh` and `en`.
2. **Skipping tests for simple components.** Even simple components like `SystemMessage.vue` and `UserMessage.vue` have tests. The bar for test coverage is high.
3. **Not testing error paths.** Happy-path tests are easy; the codebase also tests auth failures, network errors, and edge cases like expired tokens.
4. **Importing UI library CSS globally in a component.** All global CSS imports belong in `main.js`. Component-specific styling that can't be done with existing CSS classes should be discussed before introducing scoped `<style>` blocks.
5. **Letting parent component tests depend on heavy lazy child internals.** Lazy components loaded with `defineAsyncComponent` can resolve more slowly in CI than locally. If the child behavior is already covered by its own spec, mock the child module in the parent test and assert the parent-to-child contract (for example, raw JSON props and `.chart-json-panel` placement) instead of waiting on ECharts/Mermaid initialization inside the parent test.
