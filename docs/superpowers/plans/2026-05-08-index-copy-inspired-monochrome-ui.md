# Index Copy Inspired Monochrome UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the current monochrome frontend so its login page and chat workspace structure borrow the layout rhythm of `index copy.html` while keeping the approved black/white/gray visual system.

**Architecture:** Keep the existing Vue view/component split and reuse the test/tooling foundation already added in this branch. Replace the current split login structure with a focused single-entry shell, add a small set of new workspace layout hooks to the existing chat shell, then restyle `frontend/src/style.css` around those structures without reintroducing color accents or glass effects.

**Tech Stack:** Vue 3, Vite, Vitest, Vue Test Utils, plain CSS

---

## File Structure

### Existing files to modify
- `frontend/src/views/LoginView.vue`
  - Replace the split login structure with a centered single-entry composition.
- `frontend/src/views/__tests__/LoginView.spec.js`
  - Update login assertions from split-panel hooks to single-entry hooks while preserving behavior checks.
- `frontend/src/views/ChatView.vue`
  - Add new workspace wrapper hooks for the denser product-like shell.
- `frontend/src/components/layout/TopNav.vue`
  - Add product-header wrapper hooks inspired by the reference header structure.
- `frontend/src/components/layout/ExampleSidebar.vue`
  - Add clearer grouping hooks for the support rail / question list structure.
- `frontend/src/components/chat/ChatInput.vue`
  - Add a dock/toolbar hook so the composer can be styled like a bottom control bar.
- `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
  - Update structural assertions to match the new workspace shell hooks while keeping keyboard-submit coverage.
- `frontend/src/__tests__/styleTokens.spec.js`
  - Check the new login/workspace invariants and keep monochrome token coverage.
- `frontend/src/style.css`
  - Rework layout and spacing for the single-entry login page and the denser reference-inspired workspace shell.
- `backend/src/main/resources/static/index.html`
  - Will change when the final frontend build emits a new asset pair.
- `backend/src/main/resources/static/assets/*`
  - Will change when the final frontend build emits a new asset pair.

### Existing files expected to remain unchanged
- `frontend/src/components/chat/AssistantMessage.vue`
- `frontend/src/components/chat/UserMessage.vue`
- `frontend/src/components/__tests__/MessageShell.spec.js`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.js`
- `frontend/src/test/setup.js`

## Task 1: Convert the login page from split editorial stage to focused single-entry layout

**Files:**
- Modify: `frontend/src/views/__tests__/LoginView.spec.js`
- Modify: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: Rewrite the login test to expect the new single-entry hooks**

Update `frontend/src/views/__tests__/LoginView.spec.js` so the structure assertion targets the new centered layout:

```js
test("renders the focused single-entry login layout", () => {
  const wrapper = mountLoginView();

  expect(wrapper.find(".login-entry-shell").exists()).toBe(true);
  expect(wrapper.find(".login-entry-panel").exists()).toBe(true);
  expect(wrapper.find(".login-entry-stack").exists()).toBe(true);
  expect(wrapper.find(".login-entry-actions .primary-button").exists()).toBe(true);
  expect(wrapper.find(".login-cover-panel").exists()).toBe(false);
  expect(wrapper.find(".login-access-panel").exists()).toBe(false);
  expect(wrapper.text()).toContain(dictionary.loginTitle);
  expect(wrapper.text()).toContain(dictionary.loginBody);
  expect(wrapper.text()).toContain(dictionary.loginNoticeBody);
});
```

- [ ] **Step 2: Run the login test and verify it fails for the expected reason**

Run:

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

Expected:
- `FAIL  src/views/__tests__/LoginView.spec.js`
- Failure shows `.login-entry-shell`, `.login-entry-panel`, or `.login-entry-stack` is missing because the view still uses the split layout.

- [ ] **Step 3: Replace the split login template with a centered single-entry shell**

Update `frontend/src/views/LoginView.vue`:

```vue
<template>
  <div class="login-shell">
    <header class="login-shell-header">
      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />
    </header>

    <section class="login-screen">
      <div class="login-entry-shell">
        <div class="login-entry-panel">
          <div class="login-entry-stack">
            <div class="login-entry-header">
              <div class="login-hero-logo-badge">
                <img src="/logo.png" alt="Brand logo" class="login-hero-logo-image" />
              </div>

              <div class="login-hero-text">
                <h2>{{ dictionary.loginTitle }}</h2>
                <p class="login-copy">{{ dictionary.loginBody }}</p>
              </div>
            </div>

            <form class="login-form login-entry-actions" @submit.prevent="handleSubmit">
              <input
                ref="accessKeyInput"
                :class="[
                  'text-input',
                  { 'text-input-error': loginError, 'text-input-shake': isInputShaking }
                ]"
                :placeholder="dictionary.loginPlaceholder"
                :value="accessKey"
                type="password"
                autocomplete="off"
                :aria-invalid="loginError ? 'true' : 'false'"
                @animationend="handleShakeEnd"
                @input="$emit('update:access-key', $event.target.value)"
              />

              <button class="primary-button" type="submit" :disabled="loginLoading">
                {{ loginLoading ? dictionary.loginLoading : dictionary.loginButton }}
              </button>
            </form>

            <p v-if="loginError" class="error-text">{{ loginError }}</p>
          </div>
        </div>

        <div class="login-screen-footer">
          <p>{{ dictionary.loginNoticeBody }}</p>
        </div>
      </div>
    </section>
  </div>
</template>
```

- [ ] **Step 4: Run the login test again and verify it passes**

Run:

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

Expected:
- `PASS  src/views/__tests__/LoginView.spec.js`
- Existing behavior tests for loading, error replay, and submit guard remain green.

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/src/views/__tests__/LoginView.spec.js frontend/src/views/LoginView.vue
git commit -m "feat: refocus login into single entry shell"
```

## Task 2: Reshape the chat workspace shell toward the reference layout rhythm

**Files:**
- Modify: `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
- Modify: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/components/layout/TopNav.vue`
- Modify: `frontend/src/components/layout/ExampleSidebar.vue`
- Modify: `frontend/src/components/chat/ChatInput.vue`

- [ ] **Step 1: Extend the workspace test to target the new shell hooks**

Update `frontend/src/components/__tests__/WorkspaceChrome.spec.js`:

```js
import { mount } from "@vue/test-utils";
import ChatInput from "../chat/ChatInput.vue";
import ExampleSidebar from "../layout/ExampleSidebar.vue";
import TopNav from "../layout/TopNav.vue";

// keep the existing dictionary and keyboard-submit test

test("renders the reference-inspired workspace shell hooks", () => {
  const topNav = mount(TopNav, {
    props: {
      dictionary,
      locale: "en"
    },
    global: {
      stubs: {
        LanguageSwitcher: {
          template: "<button type='button'>lang</button>"
        }
      }
    }
  });
  const sidebar = mount(ExampleSidebar, {
    props: {
      activeSessionLabel: "Current session",
      dictionary
    }
  });
  const chatInput = mount(ChatInput, {
    props: {
      dictionary,
      modelValue: "Hello"
    }
  });

  expect(topNav.find(".topbar-product").exists()).toBe(true);
  expect(topNav.find(".topbar-tools").exists()).toBe(true);
  expect(sidebar.find(".sidebar-rail").exists()).toBe(true);
  expect(sidebar.find(".sidebar-group-list").exists()).toBe(true);
  expect(chatInput.find(".composer-dock").exists()).toBe(true);
});
```

- [ ] **Step 2: Run the workspace test and verify it fails**

Run:

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js
```

Expected:
- `FAIL  src/components/__tests__/WorkspaceChrome.spec.js`
- Missing `.topbar-product`, `.sidebar-rail`, `.sidebar-group-list`, or `.composer-dock`.

- [ ] **Step 3: Add the product-header and support-rail hooks**

Update `frontend/src/components/layout/TopNav.vue`:

```vue
<template>
  <header class="topbar topbar-editorial topbar-product">
    <div class="topbar-left topbar-identity">
      <button class="ghost-button mobile-only" type="button" @click="$emit('open-sidebar')">
        {{ dictionary.openMenu }}
      </button>

      <div class="topbar-brand">
        <div class="topbar-logo-badge">
          <img src="/logo.png" :alt="dictionary.appName" class="topbar-logo-image" />
        </div>

        <div class="topbar-title-group">
          <p class="eyebrow">{{ dictionary.appTagline }}</p>
          <h2>{{ dictionary.appName }}</h2>
        </div>
      </div>
    </div>

    <div class="topbar-actions topbar-tools">
      <span v-if="statusMessage" class="status-pill">{{ statusMessage }}</span>
      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />
      <button
        v-if="authVerified"
        class="ghost-button"
        type="button"
        :disabled="isSending"
        @click="$emit('clear-session')"
      >
        {{ dictionary.clearChat }}
      </button>
      <button
        v-if="authVerified"
        class="ghost-button"
        type="button"
        @click="$emit('sign-out')"
      >
        {{ dictionary.logoutButton }}
      </button>
    </div>
  </header>
</template>
```

Update `frontend/src/components/layout/ExampleSidebar.vue`:

```vue
<template>
  <aside :class="['sidebar', 'sidebar-editorial', 'sidebar-rail', { 'sidebar-open': showMobileSidebar }]">
    <div class="sidebar-top">
      <div>
        <p class="eyebrow">{{ dictionary.appName }}</p>
        <h1 class="sidebar-title">{{ dictionary.workspaceTitle }}</h1>
      </div>

      <button class="ghost-button mobile-only" type="button" @click="$emit('close')">
        {{ dictionary.closeMenu }}
      </button>
    </div>

    <div class="sidebar-action-block">
      <button class="primary-sidebar-button" type="button" @click="$emit('new-chat')">
        {{ dictionary.newChat }}
      </button>
    </div>

    <div class="sidebar-group-list">
      <section v-if="dictionary.prompts?.length" class="sidebar-section">
        <div class="section-head">
          <span>{{ dictionary.sidebarSection }}</span>
        </div>

        <button
          v-for="prompt in dictionary.prompts"
          :key="prompt"
          class="prompt-card"
          type="button"
          :disabled="isSending"
          @click="$emit('fill-prompt', prompt)"
        >
          {{ prompt }}
        </button>
      </section>

      <section class="sidebar-section sidebar-current-session">
        <div class="section-head">
          <span>{{ dictionary.historySection }}</span>
        </div>

        <div class="session-card">
          <strong>{{ activeSessionLabel }}</strong>
          <p>{{ dictionary.historyHint }}</p>
        </div>
      </section>
    </div>

    <div class="sidebar-footer">
      <p>{{ dictionary.footerNote }}</p>
    </div>
  </aside>
</template>
```

- [ ] **Step 4: Add the composer dock hook and workspace wrapper**

Update `frontend/src/components/chat/ChatInput.vue`:

```vue
<template>
  <div class="composer-shell composer-dock">
    <p v-if="requestError" class="error-text">{{ requestError }}</p>
    <p v-if="toastMessage" class="toast-text">{{ toastMessage }}</p>

    <div class="composer-card composer-card-editorial">
      <textarea
        ref="composer"
        class="composer-input"
        :disabled="isSending"
        :placeholder="dictionary.inputPlaceholder"
        :value="modelValue"
        rows="1"
        @input="handleInput"
        @keydown="handleKeydown"
      ></textarea>

      <button
        class="primary-button send-button"
        type="button"
        :disabled="isSending || !modelValue.trim()"
        @click="$emit('submit')"
      >
        {{ isSending ? dictionary.sending : dictionary.sendButton }}
      </button>
    </div>
  </div>
</template>
```

Update `frontend/src/views/ChatView.vue`:

```vue
<template>
  <div class="app-shell workspace-shell">
    <ExampleSidebar
      :active-session-label="activeSessionLabel"
      :dictionary="dictionary"
      :is-sending="isSending"
      :show-mobile-sidebar="showMobileSidebar"
      @close="closeMobileSidebar"
      @fill-prompt="handleFillPrompt"
      @new-chat="startNewChat"
    />

    <div v-if="showMobileSidebar" class="sidebar-backdrop" @click="closeMobileSidebar"></div>

    <main class="main-panel workspace-stage">
      <TopNav
        :auth-verified="authVerified"
        :dictionary="dictionary"
        :is-sending="isSending"
        :locale="locale"
        :status-message="statusMessage"
        @clear-session="handleClearSession"
        @open-sidebar="openMobileSidebar"
        @sign-out="$emit('sign-out')"
        @toggle-locale="$emit('toggle-locale')"
      />

      <section class="chat-screen">
        <div class="chat-copy workspace-intro">
          <div class="chat-copy-top">
            <p class="eyebrow">{{ dictionary.workspaceTitle }}</p>
            <span class="workspace-badge">{{ activeSessionLabel }}</span>
          </div>
          <h2>{{ dictionary.workspaceSubtitle }}</h2>
        </div>

        <div ref="scrollContainer" class="chat-scroll">
          <ChatMessageList
            :dictionary="dictionary"
            :has-messages="hasMessages"
            :messages="messages"
            @submit-follow-up="submitPrompt"
            @toggle-thinking="toggleThinking"
          />
        </div>

        <ChatInput
          ref="chatInputRef"
          v-model="promptInput"
          :dictionary="dictionary"
          :is-sending="isSending"
          :request-error="requestError"
          :toast-message="toastMessage"
          @submit="submitPrompt"
        />
      </section>
    </main>
  </div>
</template>
```

- [ ] **Step 5: Run the workspace test again and verify it passes**

Run:

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js
```

Expected:
- `PASS  src/components/__tests__/WorkspaceChrome.spec.js`
- Existing keyboard-submit assertions remain green.

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/src/components/__tests__/WorkspaceChrome.spec.js frontend/src/views/ChatView.vue frontend/src/components/layout/TopNav.vue frontend/src/components/layout/ExampleSidebar.vue frontend/src/components/chat/ChatInput.vue
git commit -m "feat: align workspace shell to index copy structure"
```

## Task 3: Restyle the login and workspace shell around the reference-inspired structure

**Files:**
- Modify: `frontend/src/__tests__/styleTokens.spec.js`
- Modify: `frontend/src/style.css`

- [ ] **Step 1: Update the style regression test to expect the new login/workspace invariants**

Extend `frontend/src/__tests__/styleTokens.spec.js`:

```js
test("supports the focused login entry and reference-inspired workspace shell", () => {
  expectSelectorsShareRule([".topbar", ".topbar-editorial", ".topbar-product"], [
    "padding: 0.75rem 1.4rem;",
    "border-bottom: 1px solid var(--border-soft);"
  ]);
  expectSelectorInRule(".login-entry-shell", [
    "width: min(100%, 480px);"
  ]);
  expectSelectorInRule(".login-entry-panel", [
    "background: var(--surface);",
    "border: 1px solid var(--border-soft);"
  ]);
  expectSelectorInRule(".sidebar-rail", [
    "background: var(--sidebar-bg);"
  ]);
  expectSelectorInRule(".composer-dock", [
    "max-width: var(--chat-column-width);"
  ]);
  expect(stylesheet).not.toContain(".login-editorial-grid");
  expect(stylesheet).not.toMatch(/gradient-to-r|brand-blue|glass/i);
});
```

- [ ] **Step 2: Run the style test and verify it fails**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected:
- `FAIL  src/__tests__/styleTokens.spec.js`
- Missing `.login-entry-shell`, `.login-entry-panel`, or `.composer-dock` rule coverage.

- [ ] **Step 3: Rewrite the login and workspace layout rules in `style.css`**

Update the login shell away from the split grid:

```css
.login-screen {
  display: grid;
  place-items: center;
  padding: 2rem 1rem 3rem;
}

.login-entry-shell {
  width: min(100%, 480px);
  display: grid;
  gap: 1rem;
}

.login-entry-panel {
  padding: 2.5rem 2rem;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.login-entry-stack {
  display: grid;
  gap: 1.5rem;
}

.login-entry-header {
  display: grid;
  justify-items: center;
  gap: 1rem;
  text-align: center;
}

.login-entry-actions {
  width: 100%;
  gap: 0.85rem;
}
```

Update the workspace shell toward the reference rhythm:

```css
.workspace-shell {
  background: var(--page-bg);
}

.workspace-stage {
  background: var(--page-bg);
}

.topbar,
.topbar-editorial,
.topbar-product {
  padding: 0.75rem 1.4rem;
  background: rgba(248, 248, 246, 0.96);
  border-bottom: 1px solid var(--border-soft);
}

.topbar-tools {
  gap: 0.65rem;
}

.sidebar-rail {
  gap: 0.85rem;
}

.sidebar-group-list {
  display: grid;
  gap: 0.85rem;
}

.workspace-intro {
  padding: 1rem 1.2rem;
  background: var(--surface);
  border: 1px solid var(--border-soft);
}

.chat-scroll {
  padding-right: 0.25rem;
}

.composer-dock {
  max-width: var(--chat-column-width);
  width: 100%;
  margin: 0 auto;
}
```

Keep the responsive rules aligned:

```css
@media (max-width: 1040px) {
  .login-entry-shell {
    width: min(100%, 100%);
  }
}

@media (max-width: 720px) {
  .login-entry-panel {
    padding: 1.5rem;
  }

  .topbar,
  .topbar-editorial,
  .topbar-product {
    padding: 0.9rem;
  }
}
```

- [ ] **Step 4: Run the style and view tests and verify they pass**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js src/views/__tests__/LoginView.spec.js src/components/__tests__/WorkspaceChrome.spec.js
```

Expected:
- All three files pass.
- The login test now validates the single-entry structure.

- [ ] **Step 5: Run the full frontend test suite**

Run:

```bash
cd frontend
npm.cmd run test
```

Expected:
- All four frontend spec files pass.
- No Vue runtime warnings appear in the output.

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/src/__tests__/styleTokens.spec.js frontend/src/style.css
git commit -m "feat: restyle shell around index copy structure"
```

## Task 4: Rebuild and verify the backend-served static shell

**Files:**
- Modify: `backend/src/main/resources/static/index.html`
- Modify: `backend/src/main/resources/static/assets/*`

- [ ] **Step 1: Build the frontend bundle**

Run:

```bash
cd frontend
npm.cmd run build
```

Expected:
- Vite writes a new `index.html` plus a new hashed CSS/JS pair into `../backend/src/main/resources/static`.
- No CSS parse errors or unresolved asset errors appear.

- [ ] **Step 2: Run the frontend test suite one more time on the final source state**

Run:

```bash
cd frontend
npm.cmd run test
```

Expected:
- All frontend tests still pass after any final tweaks.

- [ ] **Step 3: Do a runtime sanity pass**

Run:

```bash
cd frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

Expected:
- Dev server starts without compile errors.
- The shell loads at `http://127.0.0.1:5173`.

Check these states:

```text
1. Login page is a single focused entry area, not a two-panel split.
2. Header is thin and product-like.
3. Sidebar reads like a grouped support rail.
4. Chat body feels denser and less like floating cards.
5. Composer reads like a bottom control bar.
6. No blue/cyan accents or glassmorphism remain.
```

- [ ] **Step 4: Sync the backend static asset directory to the current build**

Run:

```bash
Get-Content backend/src/main/resources/static/index.html
Get-ChildItem backend/src/main/resources/static/assets
```

Expected:
- `index.html` references the current hashed CSS and JS files.
- Any stale hashed files that are no longer referenced are removed before final handoff.

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend backend/src/main/resources/static
git commit -m "build: ship index-copy-inspired monochrome frontend"
```
