# State Management

> How state is managed in this project.

---

## Overview

This project uses **Vue 3 Composition API** exclusively for state management. There is no Vuex, no Pinia, and no external state library. State is organized into three tiers: composable-level shared state, component-level local state, and persistence-layer state (localStorage/sessionStorage).

---

## State Categories

### 1. Composable-Level State (Shared / "Global")

Composables hold the application's shared reactive state. When multiple components import the same composable, they share the same reactive instance (because composables are called once in an orchestrator component like `ChatView` and the state is passed down via props).

| Composable | File | Key State |
|---|---|---|
| `useAuth` | `src/composables/useAuth.js` | `accessKey`, `authVerified`, `loginLoading`, `loginError` |
| `useChat` | `src/composables/useChat.js` | `messages`, `isSending`, `promptInput`, `streamPhase`, `sessionId`, `requestError`, `toastMessage` |
| `useI18nState` | `src/composables/useI18nState.js` | `locale`, `dictionary` |
| `useModelSettings` | `src/composables/useModelSettings.js` | Module-level `modelSettingsState` (singleton) |

**How state flows down to child components:**

`App.vue` instantiates `useI18nState` and `useAuth`. Conditional rendering shows `LoginView` or `ChatView`. `ChatView` instantiates `useChat` and reads model settings. All child components receive state as props:

```
App.vue
  ├── useI18nState(messages) → locale, dictionary
  ├── useAuth({ dictionary }) → accessKey, authVerified, loginError, loginLoading
  │
  ├── LoginView (props: dictionary, locale, accessKey, loginError, loginLoading)
  │     └── emits: toggle-locale, update:access-key, submit
  │
  └── ChatView (props: authVerified, dictionary, locale)
        ├── useChat(...) → messages, promptInput, isSending, streamPhase, ...
        ├── useModelSettings (plain functions)
        ├── TopNav (props: authVerified, dictionary, isSending, locale, statusMessage, streamPhase)
        ├── ChatMessageList (receives messages, locale, dictionary via props)
        │     ├── AssistantMessage
        │     ├── UserMessage
        │     └── SystemMessage
        ├── ChatInput (props: dictionary, isSending, locale, modelValue)
        ├── ExampleSidebar (props via ChatView)
        └── ModelSettingsPanel (props via ChatView)
```

---

### 2. Component-Level Local State

Components use `ref()` and `computed()` for UI state that doesn't need to be shared. Examples:

```js
// ChatInput.vue
const composer = ref(null);  // template ref for the textarea

// AssistantMessage.vue
const timelineExpanded = ref(false);
const thinkingCollapsed = ref(false);
const mermaidViewState = ref(new Map());

// LoginView.vue
const isCardShaking = ref(false);
```

Local computed properties derive display values from props:

```js
// AssistantMessage.vue
const thinkingSummary = computed(() => buildThinkingSummary(props.message?.steps, props.locale));
const hasThinkingDetails = computed(() =>
  thinkingSummary.value.length > 0 || progressSteps.value.length > 0 || timelineSteps.value.length > 0
);
```

---

### 3. Persistent State (localStorage / sessionStorage)

Storage is managed through `frontend/src/utils/storage.js`, a thin wrapper with `scope` ("local" or "session") and error swallowing:

```js
// storage.js
export function readStorageValue(scope, key, fallback = "") { ... }
export function writeStorageValue(scope, key, value) { ... }
export function removeStorageValue(scope, key) { ... }
```

Storage keys are centralized in `frontend/src/constants/storageKeys.js`:
```js
export const STORAGE_KEYS = {
  auth: "agentpoc.authVerified",      // sessionStorage (user session)
  locale: "agentpoc.locale",           // localStorage (persists across sessions)
  modelSettings: "agentpoc.modelSettings", // localStorage
  session: "brand_session_id"          // localStorage (chat session)
};
```

**When to persist vs. not:**
- **Auth token**: sessionStorage (clears when tab closes).
- **Locale preference**: localStorage (persists across visits).
- **Model settings**: localStorage (persists across visits).
- **Chat session ID**: localStorage (persists across page reloads within same session).
- **Chat messages**: NOT persisted -- they live only in memory (`messages` ref). Clearing or refreshing loses message history.
- **UI state** (sidebar collapsed, panel open): NOT persisted -- resets on page load.

---

### 4. Derived State

`computed()` is the primary tool for derived state. The codebase prefers `computed()` over watchers for deriving values:

```js
// useAuth.js
const loginError = computed(() =>
  hasError.value ? dictionary.value.loginError : ""
);

// useI18nState.js
const dictionary = computed(() => messageCatalog[locale.value] ?? messageCatalog[fallbackLocale]);

// AssistantMessage.vue
const progressSteps = computed(() => {
  const steps = Array.isArray(props.message?.steps) ? props.message.steps : [];
  return steps.filter(step => step?.type === "progress" || step?.progressPlaceholder === true);
});
```

---

## When to Use Global State

Since there is no store library, "global" means "owned by a composable and passed down as props." The decision framework:

| State | Where it lives | Rationale |
|---|---|---|
| Auth status | `useAuth` composable | Shared by App.vue, ChatView, TopNav |
| Chat messages | `useChat` composable | Core data, shared by message list + input |
| Locale | `useI18nState` composable | Every component needs translations |
| Model settings | `useModelSettings` (module-level) | Needed by ChatView + useChat for API calls |
| Textarea focus state | Component `ref` | Only ChatInput needs it |
| Timeline expand/collapse | Component `ref` | Only AssistantMessage needs it |

**Rule of thumb**: If two or more sibling components need the same data, it belongs in the parent's composable. If only one component needs it, use local `ref`.

---

## Server State

There is no server-state cache. Every interaction with the backend is a fresh fetch:

- **Login**: `verifyAccessKey(key)` POST called on form submit.
- **Chat streaming**: `streamChat(...)` POST with SSE response, called on each message send.
- **Clear session**: `clearSession(sessionId)` DELETE called on button click.
- **Test model connection**: `testModelConnection(config)` POST called from settings panel.

No optimistic updates, no stale-while-revalidate, no cache invalidation. This is appropriate for a real-time chat application where freshness is critical.

---

## Reactive Arrays

The `messages` array in `useChat` is a `ref([])`. Mutations must trigger reactivity:

```js
// Push new messages
messages.value.push(userMessage, assistantMessage);

// Filter system messages
messages.value = messages.value.filter(m => m.role !== 'system');

// Update specific message (reassign from array to get Proxy)
assistantMessage = messages.value[messages.value.length - 1];
```

Key pattern from `useChat.js` line 327: after pushing an assistant message, the code re-reads it from the reactive array to get the Vue Proxy, ensuring subsequent mutations trigger re-renders.

---

## Common Mistakes

1. **Mutating a destructured object instead of the reactive proxy.** In `useChat.js` line 327, a local `assistantMessage` variable is reassigned to `messages.value[messages.value.length - 1]` after push, because the original object reference doesn't carry Vue's reactivity Proxy.
2. **Forgetting to unwrap refs in composable return values.** When a composable returns a `ref`, the caller must use `.value` in `<script setup>`. In `<template>`, refs auto-unwrap.
3. **Not clearing timers.** `toastTimerId` and `assistantRenderTimerId` in `useChat.js` are cleared in `onBeforeUnmount` -- always pair timers with cleanup.
4. **Using `reactive()` instead of `ref()`.** The codebase uses `ref()` exclusively. `reactive()` is not observed anywhere. Stick with `ref()` for consistency.
5. **Passing raw values to composable options that need reactivity.** Always wrap prop-derived values in `computed()`: `computed(() => props.dictionary)`.
