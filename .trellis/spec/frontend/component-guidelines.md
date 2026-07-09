# Component Guidelines

> How components are built in this project.

---

## Overview

All components use Vue 3 `<script setup>` syntax. There are no Options API components anywhere in the codebase. Components are SFCs (Single File Components) with `<script setup>`, `<template>`, and optionally `<style scoped>` (though most styles live in the global `style.css`).

---

## Component Structure

A standard component file follows this order:

```vue
<script setup>
// 1. Imports (Vue, other components, utils, composables)
import { computed, ref, watch } from "vue";
import ChildComponent from "./ChildComponent.vue";
import { someUtil } from "../../utils/someUtil";

// 2. Props
const props = defineProps({ ... });

// 3. Emits
const emit = defineEmits([...]);

// 4. Local state (ref, computed)
const localState = ref(false);

// 5. Lifecycle hooks & watchers
watch(() => props.someProp, (val) => { ... });

// 6. Methods (plain functions)
function handleSomething() { ... }

// 7. defineExpose (rare, only when parent needs imperative access)
defineExpose({ someMethod });
</script>

<template>
  <!-- Template -->
</template>
```

Real example from `frontend/src/components/chat/ChatInput.vue` (lines 1-65):
```js
<script setup>
import { ref } from "vue";

const props = defineProps({
  dictionary: { type: Object, required: true },
  isSending: { type: Boolean, default: false },
  locale: { type: String, default: "zh" },
  modelValue: { type: String, default: "" },
  requestError: { type: String, default: "" },
  toastMessage: { type: String, default: "" }
});

const emit = defineEmits(["submit", "update:modelValue", "stop"]);
// ...
defineExpose({ focusComposer });
</script>
```

---

## Props Conventions

Props are defined using the Options API style inside `defineProps()` with explicit `type`, `required`, and `default` fields. This provides runtime type validation even though the project is JavaScript.

**Required pattern** (from `frontend/src/components/layout/TopNav.vue` lines 2-27):
```js
const props = defineProps({
  dictionary: { type: Object, required: true },       // required Object
  locale: { type: String, required: true },            // required String
  authVerified: { type: Boolean, default: true },      // Boolean with default
  isSending: { type: Boolean, default: false },
  statusMessage: { type: String, default: "" },        // String with empty default
  streamPhase: { type: String, default: "idle" }
});
```

**Key rules observed across the codebase:**
- Always use `type: Object, required: true` for the `dictionary` prop (passed through to every component).
- Always use `type: String, required: true` for `locale`.
- Booleans always have a `default` (usually `false`).
- Strings that can be empty use `default: ""`.
- Props use camelCase in `<script setup>`, referenced as kebab-case in parent templates.

---

## Emits Conventions

Emits are declared as an array of strings in `defineEmits()`:

```js
defineEmits(["submit", "update:modelValue", "stop"]);        // ChatInput.vue line 31
defineEmits(["clear-session", "open-settings", "sign-out", "toggle-locale"]); // TopNav.vue line 29
```

**Patterns:**
- v-model uses `"update:modelValue"` event name.
- All other events use kebab-case.
- Events are emitted in templates via `$emit('event-name')` or `emit('event-name')`.
- Parent templates listen with `@event-name="handler"`.

---

## v-model Convention

Components use the standard Vue 3 v-model protocol. The parent binds `v-model="someRef"` and the child declares:

```js
const props = defineProps({
  modelValue: { type: String, default: "" }
});
const emit = defineEmits(["update:modelValue"]);
// In template or handler: emit("update:modelValue", newValue)
```

Example from `App.vue` line 36: `@update:access-key="accessKey = $event"`.

---

## Lazy-Loaded Components

Use `defineAsyncComponent` for heavy components. Only one instance in the codebase:

```js
// AssistantMessage.vue line 9
const MermaidChartAdapter = defineAsyncComponent(() => import("./MermaidChartAdapter.vue"));
```

---

## defineExpose

Used sparingly -- only when a parent component needs imperative access to a child method:

```js
// ChatInput.vue lines 62-64
defineExpose({
  focusComposer
});
```

---

## Template Patterns

- All conditionals use `v-if` / `v-else-if` / `v-else`. No `v-show` observed.
- `v-for` used for list rendering.
- `v-html` used only for rendering markdown HTML (from `renderMarkdownLite`) -- always after sanitization by markdown-it.
- Event handlers: `@click`, `@input`, `@keydown`, `@submit`.
- Attribute binding: `:disabled`, `:aria-label`, `:placeholder`, `:title`.
- Dynamic class binding not observed -- classes are static strings.
- `ref="name"` for template refs (used with `composer.value?.focus()`).

---

## Styling Patterns

- **No scoped styles in components.** All styles live in the global `frontend/src/style.css`.
- CSS custom properties define the design system (colors, shadows, spacing) in `:root` (see `style.css` lines 1-37).
- Class naming is BEM-like: `.topbar-brand`, `.topbar-logo-badge`, `.composer-shell`, `.message-card-user`.
- Material Icons web font (`https://fonts.googleapis.com/icon?family=Material+Icons`) for iconography, referenced via `<span class="material-icons">icon_name</span>`.
- Font stack: `"Avenir Next", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`.

---

## Accessibility

- `:aria-label` used on inputs where a label text needs to be explicit (see `ChatInput.vue` line 83).
- `:title` attribute used on icon buttons for tooltip descriptions.
- `:disabled` used on buttons and inputs during loading states.
- `type="button"` explicitly set on all `<button>` elements.

---

## Common Mistakes

1. **Forgetting `required: true` on the `dictionary` prop.** Every component that renders user-facing text receives `dictionary` as a required prop. Omitting `required: true` means the component can mount without translations, causing runtime errors.
2. **Using `v-show` instead of `v-if`.** The codebase exclusively uses `v-if` -- `v-show` would be inconsistent and hasn't been needed.
3. **Accidentally mutating props.** Props are treated as read-only. Changes flow up via emits.
4. **Not declaring emits.** Always declare emits in `defineEmits()` even though Vue allows undeclared emits. The codebase always declares them.
5. **Not handling the `streaming` state in message components.** The `streaming` flag on message objects controls rendering behavior -- components must check it before rendering complex content (charts, mermaid, follow-ups).
