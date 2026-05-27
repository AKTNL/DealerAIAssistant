# Frontend Blue Gray UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the frontend from a black-and-white editorial theme to a professional blue-gray enterprise analytics theme without changing workflows or component structure.

**Architecture:** Keep the implementation CSS-only except for test changes. `frontend/src/__tests__/styleTokens.spec.js` defines the visual contract first, then `frontend/src/style.css` implements the new semantic tokens and retunes existing selectors to use them.

**Tech Stack:** Vue 3, Vite, Vitest, CSS custom properties, existing global stylesheet.

---

## File Structure

- Modify: `frontend/src/__tests__/styleTokens.spec.js`
  - Responsibility: enforce the new blue-gray token contract, preserve responsive alias rules, and prevent regressions back to pure-black primary states.
- Modify: `frontend/src/style.css`
  - Responsibility: define all theme tokens in `:root` and apply them to login, sidebar, top navigation, chat messages, composer, markdown, Mermaid, loading, and state styles.

No component template changes are planned. If a visual state cannot be targeted from existing classes, stop and inspect the relevant component before adding markup.

---

## Task 1: Update The Theme Contract Tests

**Files:**
- Modify: `frontend/src/__tests__/styleTokens.spec.js`

- [ ] **Step 1: Replace the grayscale token test with blue-gray token assertions**

In `frontend/src/__tests__/styleTokens.spec.js`, replace this describe label:

```js
describe("editorial grayscale style tokens", () => {
```

with:

```js
describe("blue gray enterprise style tokens", () => {
```

Then replace the first test body with this test:

```js
test("defines the approved blue-gray enterprise tokens and keeps the neutral token API", () => {
  expectCustomProperty("--page-bg", "#f4f7fb");
  expectCustomProperty("--page-bg-deep", "#e8eef5");
  expectCustomProperty("--surface", "#ffffff");
  expectCustomProperty("--surface-strong", "#f8fbff");
  expectCustomProperty("--surface-soft", "#eef4fa");
  expectCustomProperty("--surface-muted", "#e2ebf3");
  expectCustomProperty("--surface-tinted", "#e9f2fb");
  expectCustomProperty("--sidebar-bg", "#eaf1f8");
  expectCustomProperty("--border-soft", "#c9d6e3");
  expectCustomProperty("--border-strong", "#aebfce");
  expectCustomProperty("--border-focus", "#3f6f9f");
  expectCustomProperty("--brand-primary", "#17324d");
  expectCustomProperty("--brand-primary-hover", "#22527a");
  expectCustomProperty("--brand-primary-soft", "#dceaf6");
  expectCustomProperty("--accent-info", "#2f7da8");
  expectCustomProperty("--text-main", "#102033");
  expectCustomProperty("--text-muted", "#516173");
  expectCustomProperty("--text-helper", "#748397");
  expectCustomProperty("--text-strong", "#0b1728");
  expectCustomProperty("--danger", "#a43f3f");
  expectCustomProperty("--focus-ring", "0 0 0 3px rgba\\(47, 125, 168, 0\\.18\\)");

  expect(stylesheet).not.toContain("linear-gradient(135deg, #76ad87, #558667)");
  expect(stylesheet).not.toMatch(/rgba\(110,\s*167,\s*129/i);
});
```

- [ ] **Step 2: Add explicit primary-state replacement coverage**

Still in `frontend/src/__tests__/styleTokens.spec.js`, add this test after the token test:

```js
test("uses blue-gray theme tokens for primary, focus, hover, pending, and loading states", () => {
  expectSelectorsShareRule([".primary-sidebar-button", ".primary-button"], [
    "border: 1px solid var(--brand-primary);",
    "background: var(--brand-primary);",
    "color: #ffffff;"
  ]);

  expectSelectorsShareRule([".primary-sidebar-button:hover", ".primary-button:hover"], [
    "background: var(--brand-primary-hover);",
    "border-color: var(--brand-primary-hover);"
  ]);

  expectSelectorsShareRule([".composer-card:focus-within", ".composer-card-editorial:focus-within"], [
    "border-color: var(--border-focus);",
    "box-shadow: var(--shadow-md), var(--focus-ring);"
  ]);

  expectSelectorInRule(".text-input:focus", [
    "border-color: var(--border-focus);",
    "box-shadow: var(--focus-ring);"
  ]);

  expectSelectorInRule(".message-card-user", [
    "background: var(--brand-primary);",
    "color: #ffffff;"
  ]);

  expectSelectorInRule(".message-card-assistant", [
    "border-left: 3px solid var(--brand-primary);"
  ]);

  expectSelectorInRule(".message-bubble-user", [
    "background: var(--brand-primary);",
    "color: #ffffff;"
  ]);

  expectSelectorInRule(".login-submit-button", [
    "border: 1px solid var(--brand-primary);",
    "background: linear-gradient(135deg, var(--brand-primary-hover), var(--brand-primary));"
  ]);

  expectSelectorInRule(".sidebar-module-question.is-pending", [
    "border-color: var(--border-focus);",
    "background: var(--brand-primary-soft);"
  ]);

  expectSelectorInRule(".thinking-dot", [
    "background: var(--accent-info);"
  ]);

  expectSelectorInRule(".skeleton-spinner-icon", [
    "border-top-color: var(--accent-info);"
  ]);
});
```

- [ ] **Step 3: Update the existing alias rule expectations**

In the existing `keeps editorial alias classes...` test, change the focus declarations from:

```js
expectSelectorsShareRule([".composer-card:focus-within", ".composer-card-editorial:focus-within"], [
  "border-color: #111111;",
  "box-shadow: var(--shadow-md), var(--focus-ring);"
]);
```

to:

```js
expectSelectorsShareRule([".composer-card:focus-within", ".composer-card-editorial:focus-within"], [
  "border-color: var(--border-focus);",
  "box-shadow: var(--shadow-md), var(--focus-ring);"
]);
```

Keep the existing responsive assertions in this test unchanged.

- [ ] **Step 4: Update Mermaid surface expectations**

In the Mermaid test, keep the comparison bar color assertions and replace the background assertion with:

```js
expectSelectorInRule(".mermaid-chart svg[aria-roledescription=\"xychart\"] .background", [
  "fill: var(--surface, #ffffff) !important;"
]);
expectSelectorInRule(".mermaid-block", [
  "border: 1px solid rgba(63, 111, 159, 0.18);",
  "background: var(--surface-tinted);"
]);
expectSelectorInRule(".mermaid-toolbar", [
  "border-bottom: 1px solid rgba(63, 111, 159, 0.16);",
  "background: rgba(248, 251, 255, 0.82);"
]);
```

- [ ] **Step 5: Run the focused style test and verify RED**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected result: FAIL. The failures should mention missing token values such as `--brand-primary`, old `#111111` focus declarations, or missing Mermaid blue-gray declarations. If the test fails from syntax errors, fix the test before moving on.

- [ ] **Step 6: Commit the failing visual contract**

Run:

```bash
git add -- frontend/src/__tests__/styleTokens.spec.js
git commit -m "test: define blue gray frontend theme contract"
```

---

## Task 2: Implement Root Theme Tokens And Primary States

**Files:**
- Modify: `frontend/src/style.css`
- Test: `frontend/src/__tests__/styleTokens.spec.js`

- [ ] **Step 1: Replace the root token block**

In `frontend/src/style.css`, replace the current `:root` token values with this block. Keep the existing font and rendering declarations.

```css
:root {
  color-scheme: light;
  font-family: "Avenir Next", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.5;
  font-weight: 400;
  color: #102033;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  --page-bg: #f4f7fb;
  --page-bg-deep: #e8eef5;
  --surface: #ffffff;
  --surface-strong: #f8fbff;
  --surface-soft: #eef4fa;
  --surface-muted: #e2ebf3;
  --surface-accent: #edf5fc;
  --surface-tinted: #e9f2fb;
  --sidebar-bg: #eaf1f8;
  --border-soft: #c9d6e3;
  --border-strong: #aebfce;
  --border-focus: #3f6f9f;
  --brand-primary: #17324d;
  --brand-primary-hover: #22527a;
  --brand-primary-soft: #dceaf6;
  --accent-info: #2f7da8;
  --text-main: #102033;
  --text-muted: #516173;
  --text-helper: #748397;
  --text-strong: #0b1728;
  --danger: #a43f3f;
  --shadow-lg: 0 18px 40px rgba(23, 50, 77, 0.11);
  --shadow-md: 0 10px 24px rgba(23, 50, 77, 0.08);
  --shadow-sm: 0 6px 16px rgba(23, 50, 77, 0.06);
  --focus-ring: 0 0 0 3px rgba(47, 125, 168, 0.18);
  --chat-column-width: 66rem;
}
```

- [ ] **Step 2: Retune page background overlays**

Update the `body::before` and `body::after` rules to use blue-gray colors:

```css
body::before {
  opacity: 0.5;
  background-image:
    linear-gradient(rgba(63, 111, 159, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(63, 111, 159, 0.045) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.2), transparent 72%);
}

body::after {
  background:
    radial-gradient(circle at top right, rgba(47, 125, 168, 0.08), transparent 28%),
    radial-gradient(circle at bottom left, rgba(23, 50, 77, 0.06), transparent 32%);
}
```

- [ ] **Step 3: Replace primary button colors**

Replace the existing `.primary-sidebar-button, .primary-button` rule with:

```css
.primary-sidebar-button,
.primary-button {
  border: 1px solid var(--brand-primary);
  background: var(--brand-primary);
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(23, 50, 77, 0.16);
}
```

Add this hover rule near the existing button hover rules:

```css
.primary-sidebar-button:hover,
.primary-button:hover {
  background: var(--brand-primary-hover);
  border-color: var(--brand-primary-hover);
}
```

- [ ] **Step 4: Replace focus and status accent colors**

Update the following rules:

```css
.status-pill::before {
  content: "";
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 999px;
  background: var(--accent-info);
}

.text-input:focus {
  border-color: var(--border-focus);
  box-shadow: var(--focus-ring);
  background: var(--surface);
}

.login-input-field:focus {
  border-color: var(--border-focus);
  box-shadow: var(--focus-ring);
  background: rgba(255, 255, 255, 0.92);
}
```

- [ ] **Step 5: Run the focused style test**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected result: still FAIL. Root token and primary button failures should be resolved. Remaining failures should point to chat bubbles, login submit, pending/sidebar states, loading, or Mermaid surfaces.

- [ ] **Step 6: Commit root tokens and primary states**

Run:

```bash
git add -- frontend/src/style.css
git commit -m "feat: add blue gray frontend theme tokens"
```

---

## Task 3: Refresh Login, Sidebar, And Topbar Surfaces

**Files:**
- Modify: `frontend/src/style.css`
- Test: `frontend/src/__tests__/styleTokens.spec.js`

- [ ] **Step 1: Retune login surfaces**

Update the login-related CSS rules that currently use black gradients or black accent bars:

```css
.login-cover-panel {
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.94), rgba(233, 242, 251, 0.94)),
    var(--surface);
}

.login-panel::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 6px;
  background: var(--brand-primary);
}

.login-note-label {
  display: inline-flex;
  padding: 0.34rem 0.68rem;
  border-radius: 999px;
  background: var(--brand-primary);
  color: #ffffff;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-submit-button {
  width: 100%;
  padding: 1rem;
  border-radius: 1rem;
  border: 1px solid var(--brand-primary);
  background: linear-gradient(135deg, var(--brand-primary-hover), var(--brand-primary));
  color: #ffffff;
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  box-shadow: 0 8px 22px rgba(23, 50, 77, 0.2);
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, opacity 180ms ease;
}

.login-submit-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 26px rgba(23, 50, 77, 0.28);
}
```

- [ ] **Step 2: Retune sidebar module surfaces and pending states**

Update the sidebar module rules:

```css
.sidebar-module-card {
  display: grid;
  gap: 0.65rem;
  padding: 0.8rem;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.78), rgba(255, 255, 255, 0.98)),
    var(--surface);
  transition:
    border-color 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease;
}

.sidebar-module-card:hover {
  border-color: var(--border-strong);
  box-shadow: 0 8px 18px rgba(23, 50, 77, 0.06);
}

.sidebar-module-question.is-pending {
  border-color: var(--border-focus);
  background: var(--brand-primary-soft);
  color: var(--text-strong);
}

.sidebar-question-spinner {
  width: 0.9rem;
  height: 0.9rem;
  flex: none;
  border: 2px solid rgba(47, 125, 168, 0.18);
  border-top-color: var(--accent-info);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
```

- [ ] **Step 3: Retune topbar hover and badge accents**

Update these rules:

```css
.topbar,
.topbar-editorial {
  position: sticky;
  top: 0;
  z-index: 5;
  padding: 0.75rem 1.4rem;
  gap: 1rem;
  background: rgba(248, 251, 255, 0.96);
  border-bottom: 1px solid var(--border-soft);
  box-shadow: none;
}

.topbar-icon-btn:hover {
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
}

.spring-ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.625rem;
  background: var(--brand-primary-soft);
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--brand-primary);
}

.spring-ai-badge-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: var(--accent-info);
  flex-shrink: 0;
}
```

- [ ] **Step 4: Run the focused style test**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected result: still FAIL only for chat/composer/Mermaid/loading declarations that are not yet updated.

- [ ] **Step 5: Commit surface refresh**

Run:

```bash
git add -- frontend/src/style.css
git commit -m "feat: refresh login and sidebar blue gray surfaces"
```

---

## Task 4: Refresh Chat, Composer, Markdown, And Mermaid States

**Files:**
- Modify: `frontend/src/style.css`
- Test: `frontend/src/__tests__/styleTokens.spec.js`

- [ ] **Step 1: Update chat avatars and message cards**

Replace user and assistant accent colors with tokens:

```css
.message-user .avatar,
.avatar-user {
  background: var(--brand-primary);
  color: #ffffff;
}

.message-card,
.message-card-assistant,
.message-card-user {
  padding: 1rem 1.1rem;
  background: var(--surface);
  color: var(--text-main);
}

.message-card-assistant,
.message-assistant .message-card {
  border-left: 3px solid var(--brand-primary);
}

.message-card-user,
.message-user .message-card {
  background: var(--brand-primary);
  color: #ffffff;
  border-color: transparent;
}

.message-steps li::marker {
  color: var(--accent-info);
  font-weight: 700;
}

.thinking-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: var(--accent-info);
  animation: thinking-dot-bounce 1s ease-in-out infinite;
}
```

- [ ] **Step 2: Update markdown links, code, and user bubbles**

Update these rules:

```css
.markdown-body a {
  color: var(--brand-primary-hover);
  text-decoration: underline;
  text-underline-offset: 0.14em;
}

.markdown-body a:hover {
  color: var(--brand-primary);
}

.markdown-body code {
  padding: 0.18rem 0.42rem;
  border-radius: 8px;
  font-family: "IBM Plex Mono", "Cascadia Code", "Consolas", monospace;
  background: rgba(47, 125, 168, 0.08);
}

.message-bubble-user {
  background: var(--brand-primary);
  color: #ffffff;
  border-radius: 0.75rem;
  border-top-right-radius: 0;
  padding: 0.85rem 1.25rem;
  box-shadow: 0 6px 16px rgba(23, 50, 77, 0.18);
  max-width: 85%;
  white-space: pre-wrap;
  font-size: 0.875rem;
  line-height: 1.6;
}
```

- [ ] **Step 3: Update composer focus, send, skeleton, and loading colors**

Update these rules:

```css
.composer-card:focus-within,
.composer-card-editorial:focus-within {
  border-color: var(--border-focus);
  background: var(--surface);
  box-shadow: var(--shadow-md), var(--focus-ring);
}

.send-button {
  border-color: var(--brand-primary);
  background: var(--brand-primary);
}

.send-button:hover {
  background: var(--brand-primary-hover);
  border-color: var(--brand-primary-hover);
}

.skeleton-spinner-icon {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid var(--border-soft);
  border-top-color: var(--accent-info);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
```

- [ ] **Step 4: Update Mermaid block and toolbar surfaces**

Update the Mermaid wrapper rules:

```css
.mermaid-block {
  margin: 14px 0;
  border: 1px solid rgba(63, 111, 159, 0.18);
  border-radius: 8px;
  background: var(--surface-tinted);
  overflow: hidden;
}

.mermaid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(63, 111, 159, 0.16);
  background: rgba(248, 251, 255, 0.82);
}

.analysis-empty-chart {
  display: grid;
  align-content: center;
  gap: 0.35rem;
  min-height: 104px;
  margin: 14px 0;
  padding: 0.95rem 1rem;
  border: 1px dashed rgba(63, 111, 159, 0.24);
  border-radius: 8px;
  background: var(--surface-tinted);
}

.mermaid-skeleton-plot {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  min-height: 170px;
  padding: 1rem 0.4rem 0.2rem;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(47, 125, 168, 0.06), rgba(47, 125, 168, 0));
}
```

- [ ] **Step 5: Run the focused style test and verify GREEN**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected result: PASS.

- [ ] **Step 6: Commit chat and chart refresh**

Run:

```bash
git add -- frontend/src/style.css
git commit -m "feat: refresh chat and chart blue gray states"
```

---

## Task 5: Verification And Rendered Visual Check

**Files:**
- Test only unless verification finds a defect.

- [ ] **Step 1: Run the focused frontend visual contract test**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected result: PASS.

- [ ] **Step 2: Run related frontend component tests**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js src/components/__tests__/WorkspaceChrome.spec.js src/components/__tests__/MessageShell.spec.js src/views/__tests__/LoginView.spec.js src/components/__tests__/AssistantMessage.spec.js
```

Expected result: PASS.

- [ ] **Step 3: Run the frontend build**

Run:

```bash
cd frontend
npm.cmd run build
```

Expected result: build exits successfully and Vite reports generated assets.

- [ ] **Step 4: Start the frontend dev server for rendered visual inspection**

Run:

```bash
cd frontend
npm.cmd run dev -- --host 127.0.0.1
```

Expected result: Vite prints a local URL, usually `http://127.0.0.1:5173/`. Keep the server running until the rendered checks are complete.

- [ ] **Step 5: Inspect the login screen**

Open the local Vite URL. Verify:

- Login card is centered and not clipped.
- Card top accent, input focus, submit button, and disabled button states are visibly blue-gray.
- Login error state remains red and readable.
- Language toggle remains visible.

- [ ] **Step 6: Inspect the authenticated workspace**

Authenticate with the existing local access key used for this project. Verify:

- Sidebar module hover and pending question states are visible.
- Topbar icon hover states are visible.
- User messages use deep blue bubbles and remain readable.
- Assistant messages have a blue left accent and readable markdown.
- Composer focus ring is visible.
- Disabled, loading, stop, error, and success states remain distinguishable.

- [ ] **Step 7: Inspect Mermaid rendering**

Submit or load a message with a Mermaid chart. Verify:

- Mermaid toolbar text and buttons are readable.
- Chart labels are readable against the light surface.
- Empty/fallback state is readable.
- Multi-color bars remain distinguishable.

- [ ] **Step 8: Stop the dev server**

Stop the Vite process with `Ctrl+C`.

- [ ] **Step 9: Commit any verification fixes**

If rendered inspection required fixes, run the focused tests again, then commit:

```bash
git add -- frontend/src/style.css frontend/src/__tests__/styleTokens.spec.js
git commit -m "fix: polish blue gray frontend visual states"
```

If no fixes were needed, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Blue-gray palette and token implementation are covered in Tasks 1 and 2.
- Login, sidebar, topbar, chat, composer, markdown, Mermaid, and loading states are covered in Tasks 3 and 4.
- Responsive alias preservation is covered by existing assertions retained in Task 1.
- Interaction state visibility and rendered Mermaid readability are covered in Task 5.
- API, layout, and workflow non-goals are respected because no component templates or backend files are planned.

Placeholder scan:

- No task contains placeholder markers or deferred implementation.
- Every code-changing step includes concrete snippets or exact selectors.

Type and selector consistency:

- New tokens referenced in later tasks are defined in Task 2.
- Test expectations in Task 1 match the token names and declarations introduced in Tasks 2 through 4.
