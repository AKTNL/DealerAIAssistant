# Hook Guidelines (Composables)

> How composables are used in this project.

---

## Overview

This is a Vue 3 project, so "hooks" are called **composables**. They are functions that encapsulate reactive state and logic using the Composition API. All composables live in `frontend/src/composables/`.

No external state management library (Vuex, Pinia) is used. Shared state lives in composables.

---

## Custom Composable Patterns

### Pattern A: Factory Function Returns State + Methods

The dominant pattern. The composable is a named export function that accepts an options object and returns an object with `ref` instances and methods. The caller destructures what it needs.

Example from `frontend/src/composables/useAuth.js` (entire file):
```js
import { computed, ref } from "vue";
import { verifyAccessKey } from "../api/auth";
import { clearAuthSession, isAuthSessionValid, writeAuthSession } from "../api/sessionToken";

export function useAuth({ dictionary }) {
  const accessKey = ref("");
  const hasError = ref(false);
  const loginLoading = ref(false);
  const authVerified = ref(isAuthSessionValid());

  const loginError = computed(() =>
    hasError.value ? dictionary.value.loginError : ""
  );

  async function submitAccessKey() {
    if (!accessKey.value.trim() || loginLoading.value) return;
    hasError.value = false;
    loginLoading.value = true;
    try {
      const result = await verifyAccessKey(accessKey.value.trim());
      if (!result.success || !writeAuthSession(result)) {
        throw new Error(dictionary.value.loginError);
      }
      authVerified.value = true;
      accessKey.value = "";
    } catch {
      accessKey.value = "";
      hasError.value = true;
      clearAuthSession();
    } finally {
      loginLoading.value = false;
    }
  }

  function signOut() {
    authVerified.value = false;
    accessKey.value = "";
    hasError.value = false;
    clearAuthSession();
  }

  return {
    accessKey,
    authVerified,
    loginError,
    loginLoading,
    signOut,
    submitAccessKey
  };
}
```

**Key patterns:**
- `ref()` for mutable primitive state.
- `computed()` for derived state that depends on other reactive values.
- Async functions have try/catch/finally blocks.
- Return object exposes refs directly (caller reads `.value`).

### Pattern B: Composable That Returns a Function Factory

Less common. Used when the composable is not stateful itself but returns a utility that operates on streams.

Example from `frontend/src/composables/useSseParser.js`:
```js
export function useSseParser() {
  async function consume(stream, onEvent) {
    // SSE parsing loop...
  }
  return { consume };
}
```

### Pattern C: Module-Scoped Singleton State (useModelSettings)

`frontend/src/composables/useModelSettings.js` is unique -- it exports plain functions, not a `use*` factory. It maintains a module-level `modelSettingsState` variable. This is a transitional pattern used because model settings are needed imperatively by the chat composable, not just reactively.

```js
// useModelSettings.js line 3
let modelSettingsState = null;

export function readModelSettings() { /* ... */ }
export function writeModelSettings(settings) { /* ... */ }
export function resetModelSettings() { /* ... */ }
```

**Prefer Pattern A for new composables.** Pattern C exists for legacy model-settings wiring only.

---

## Composable Usage in Components

Components invoke composables in `<script setup>` and destructure the return value.

From `frontend/src/views/ChatView.vue` lines 43-69:
```js
const {
  closeMobileSidebar,
  handleClearSession,
  handleScroll,
  hasUnreadContent,
  isSending,
  // ... more destructured values
} = useChat({
  authVerified,
  dictionary: computed(() => props.dictionary),
  locale: computed(() => props.locale),
  modelSettings: savedModelSettings,
  openModelSettings: handleOpenSettings,
  onAuthExpired: () => emit("sign-out")
});
```

**Key patterns:**
- Options passed as a single object argument.
- Reactive values are passed as `computed(() => props.xxx)` when they come from props (keeps reactivity chain alive).
- Callbacks use arrow functions to capture the parent scope correctly.

---

## Data Fetching

There is no data-fetching library (no TanStack Query, SWR, etc.). Data fetching follows an imperative pattern:

1. The composable exposes an async function (e.g., `submitAccessKey`, `submitPrompt`).
2. The composable manages loading/error state with `ref()`.
3. Results are assigned directly to `ref` values or used to update reactive arrays.

Example from `useChat.js` -- `submitPrompt` is a ~300-line async function that:
1. Sets `isSending.value = true`.
2. Creates an `AbortController` for cancellation.
3. Calls `streamChat()` from the API layer.
4. Processes SSE events in the `onEvent` callback.
5. Updates `messages.value` array and `streamPhase.value`.
6. Handles errors and cleanup in finally block.

**Error classification** is handled by `frontend/src/utils/modelErrors.js` which maps error messages/status codes to user-friendly dictionary keys based on keyword matching.

---

## Cancellation Pattern

Long-running operations (chat streaming) use `AbortController`:

```js
// useChat.js
const currentAbortController = ref(null);

// In submitPrompt:
const controller = new AbortController();
currentAbortController.value = controller;

// In stopGenerating / handleClearSession:
currentAbortController.value?.abort();
currentAbortController.value = null;
```

---

## Naming Conventions

- **Composable files**: `use<Feature>.js` (PascalCase after `use`).
- **Export function**: same as filename: `export function useAuth(...)`.
- **Internal functions**: camelCase.
- **Constants inside composables**: `UPPER_SNAKE` (e.g., `SCROLL_LOCK_THRESHOLD`, `ASSISTANT_RENDER_DELAY_MS`, `MERMAID_RETRY_DELAY_MS`).

---

## Watcher Patterns

Watchers are used for side effects when reactive state changes:

```js
// useChat.js lines 33-50
watch(sessionId, (value) => {
  writeStorageValue("local", STORAGE_KEYS.session, value);
});

watch(() => messages.value.length, () => {
  syncViewport();
});

watch(authVerified, () => {
  syncStatus();
});
```

`onMounted` and `onBeforeUnmount` are used for lifecycle-bound setup and teardown:

```js
onMounted(() => {
  writeStorageValue("local", STORAGE_KEYS.session, sessionId.value);
  syncStatus();
});

onBeforeUnmount(() => {
  currentAbortController.value?.abort();
  window.clearTimeout(assistantRenderTimerId);
  window.clearTimeout(toastTimerId);
});
```

---

## Common Mistakes

1. **Not passing reactive props as `computed()`.** When a composable option needs to track a prop change, the caller must wrap it: `computed(() => props.dictionary)`. Passing `props.dictionary` directly will snapshot the value at call time and break reactivity.
2. **Module-scoped mutable state (like useModelSettings).** This creates hidden coupling. All new composables should use Pattern A (factory function).
3. **Not cleaning up timers/controllers in `onBeforeUnmount`.** Always clear `setTimeout` IDs and abort controllers in the teardown hook.
4. **Forgetting `.value` in template context.** In `<script setup>`, refs require `.value`. In `<template>`, they auto-unwrap. This is standard Vue 3 behavior but a common source of bugs.
5. **Overloading a single composable.** `useChat.js` is ~650 lines and handles messages, streaming, scroll, toast, clear session, and new chat. New features should consider whether they belong in a separate composable before adding to `useChat`.
