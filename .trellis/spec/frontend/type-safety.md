# Type Safety

> Type safety patterns in this JavaScript project.

---

## Overview

This is a **JavaScript** project (not TypeScript). There are no `.ts` or `.tsx` files. The project relies on runtime type checking, defensive programming, and Vue's built-in prop validation rather than static type analysis.

---

## Vue Prop Type Validation

`defineProps()` is the primary mechanism for runtime type enforcement. Every component declares prop types using Vue's Options API syntax:

```js
// ChatInput.vue lines 4-29
const props = defineProps({
  dictionary: { type: Object, required: true },
  isSending: { type: Boolean, default: false },
  locale: { type: String, default: "zh" },
  modelValue: { type: String, default: "" },
  requestError: { type: String, default: "" },
  toastMessage: { type: String, default: "" }
});
```

**Supported types in props**: `String`, `Boolean`, `Object` (used consistently). No `Number`, `Array`, `Function` props observed.

**Never omit** the `type` field in prop definitions. Every prop in the codebase has an explicit `type`.

---

## Defensive Type Guards

The codebase uses standard JavaScript guards extensively. Common patterns:

### Array checks
```js
// AssistantMessage.vue line 35
const steps = Array.isArray(props.message?.steps) ? props.message.steps : [];
```

### Object shape validation
```js
// useModelSettings.js lines 129-141
function isModelSettingsRecord(settings) {
  return Boolean(
    settings &&
      typeof settings === "object" &&
      !Array.isArray(settings) &&
      typeof settings.baseUrl === "string" &&
      typeof settings.apiKey === "string" &&
      typeof settings.model === "string" &&
      settings.baseUrl.trim() &&
      settings.apiKey.trim() &&
      settings.model.trim()
  );
}
```

### Auth session validation
```js
// sessionToken.js lines 49-66
function normalizeAuthSession(session) {
  if (
    !session ||
    typeof session !== "object" ||
    Array.isArray(session) ||
    typeof session.sessionToken !== "string" ||
    typeof session.expiresAt !== "string" ||
    !session.sessionToken.trim() ||
    !isFiniteDate(session.expiresAt)
  ) {
    return null;
  }
  return {
    sessionToken: session.sessionToken.trim(),
    expiresAt: session.expiresAt
  };
}
```

### Boolean coercion for existence checks
```js
// useModelSettings.js line 56
if (!normalized) { ... }

// useAuth.js line 16
if (!accessKey.value.trim() || loginLoading.value) return;
```

---

## Optional Chaining and Nullish Coalescing

Optional chaining (`?.`) is used to safely access nested properties:

```js
// AssistantMessage.vue line 51
if (!props.message?.html || props.message?.streaming || props.message?.rendered === false) {
  return EMPTY_ANALYSIS_ENHANCEMENTS;
}

// useChat.js line 655
return error?.status === 401 || ...

// AssistantMessage.vue line 59
console.warn("Analysis enhancement parsing failed", {
  message: error?.message
});
```

Nullish coalescing (`??`) is used to provide fallbacks:

```js
// client.js line 37
return parsed?.message ?? parsed?.error ?? body;

// useChat.js line 408
?? (locale.value === "zh" ? "模型思考中" : "Model reasoning");

// client.js line 20
...(headers ?? {})
```

---

## The normalize-First Pattern

A recurring pattern: external data (API responses, localStorage reads) is immediately normalized through a validation function that returns either a clean object or `null`. The caller then branches on the result:

```js
// useModelSettings.js
const normalized = normalizeModelSettings(settings);
if (!normalized) {
  resetModelSettings();
  return false;
}
// Use normalized safely...

// chat.js
const normalized = normalizeAssistantPayload(rawText);
message.content = normalized.content;
message.followUps = normalized.followUps;
```

All API response parsing and storage reads follow this pattern.

---

## Error Handling Types

The custom `ApiError` class extends `Error` with additional fields:

```js
// client.js lines 3-10
export class ApiError extends Error {
  constructor(message, { status = 0, body = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}
```

Error handling checks for specific fields:
```js
// useChat.js line 600
if (error.name === "AbortError") { ... }

// useChat.js line 654
return error?.status === 401 || String(error?.message ?? "").toLowerCase().includes("login session expired");
```

---

## Environment Variables

`import.meta.env` is used for environment-specific values:

```js
// client.js line 1
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

// AssistantMessage.vue line 58 (conditionally logging only in dev)
if (import.meta.env.DEV && import.meta.env.MODE !== "test") {
  console.warn(...);
}
```

---

## Forbidden Patterns

1. **No `any` equivalent.** Since this is JavaScript, there is no `any` type to avoid, but the equivalent anti-pattern is **accessing deeply nested properties without guards**. Always use `?.` and null checks.
2. **No loose equality (`==`).** The codebase consistently uses strict equality (`===`, `!==`), except for one case in storage.js (`value == null` to catch both `null` and `undefined`).
3. **No prototype pollution.** Object property checks use `Object.prototype.hasOwnProperty.call(obj, key)` rather than `obj.hasOwnProperty(key)` (see `useI18nState.js` line 31).
4. **No implicit type coercion in conditionals.** Use `Boolean()`, `!!`, or explicit comparisons rather than relying on truthiness of complex objects.
5. **No `eval()` or `new Function()`.** Not present anywhere in the codebase.
6. **No direct `localStorage`/`sessionStorage` access outside `utils/storage.js`.** Always use the wrapper functions. The only exception is `useModelSettings.js` which deals with legacy migration and directly accesses storage -- new code must use `utils/storage.js`.

---

## Common Mistakes

1. **Assuming API responses have a shape without validation.** Always normalize/validate before accessing properties.
2. **Using `Array.isArray()` on `null`/`undefined` without a guard.** The codebase pattern is `Array.isArray(value) ? value : []`.
3. **Not trimming strings before comparison.** `accessKey.value.trim()`, `settings.baseUrl.trim()`, etc.
4. **Forgetting `typeof window === "undefined"` guard in SSR contexts.** Although the project is SPA-only, some utility functions (storage.js, useModelSettings.js) include SSR guards defensively. New code in `utils/` and `composables/` that touches `window`/`document` should follow this pattern.
