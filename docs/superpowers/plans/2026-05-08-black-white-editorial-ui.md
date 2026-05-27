# Black/White Editorial UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing green glassmorphism frontend into a white-dominant, black/gray editorial UI without changing the app's core behavior.

**Architecture:** Keep the current Vue view/component split, add a small set of semantic class hooks for the new editorial layout, and drive the visual shift through shared CSS tokens in `frontend/src/style.css`. Add lightweight Vitest component tests so the visual rewrite still has structural regression coverage.

**Tech Stack:** Vue 3, Vite, Vitest, Vue Test Utils, plain CSS

---

## File Structure

### Existing files to modify
- `frontend/package.json`
  - Add test scripts and frontend test devDependencies.
- `frontend/package-lock.json`
  - Lock the Vitest and Vue Test Utils dependency graph after install.
- `frontend/vite.config.js`
  - Add Vitest configuration alongside the existing Vite build config.
- `frontend/src/style.css`
  - Replace green/glass tokens and component styling with monochrome editorial rules.
- `frontend/src/views/LoginView.vue`
  - Add stable editorial layout hooks for the login cover and access panel.
- `frontend/src/components/layout/TopNav.vue`
  - Add stable editorial hooks for the thinner header layout.
- `frontend/src/components/layout/ExampleSidebar.vue`
  - Add stable editorial hooks for the sidebar content blocks.
- `frontend/src/components/chat/ChatInput.vue`
  - Add stable editorial hooks for the composer shell.
- `frontend/src/components/chat/AssistantMessage.vue`
  - Add stable role-specific monochrome classes for assistant messages.
- `frontend/src/components/chat/UserMessage.vue`
  - Add stable role-specific monochrome classes for user messages.

### New files to create
- `frontend/src/test/setup.js`
  - Shared Vitest setup for Vue component rendering in `jsdom`.
- `frontend/src/views/__tests__/LoginView.spec.js`
  - Covers the login page's editorial split layout and submit action visibility.
- `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
  - Covers the editorial class hooks on the top bar, sidebar, and composer.
- `frontend/src/components/__tests__/MessageShell.spec.js`
  - Covers monochrome role distinctions for user and assistant messages.
- `frontend/src/__tests__/styleTokens.spec.js`
  - Guards the new grayscale token set and blocks old green accent tokens from reappearing.

## Task 1: Add frontend test tooling and lock down the login layout hooks

**Files:**
- Create: `frontend/src/test/setup.js`
- Create: `frontend/src/views/__tests__/LoginView.spec.js`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.js`
- Modify: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: Add frontend test scripts and Vitest dependencies**

Update `frontend/package.json`:

```json
{
  "name": "agent-poc-frontend",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest --run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "highlight.js": "^11.11.1",
    "markdown-it": "^14.1.1",
    "vue": "^3.5.13"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.4",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^26.1.0",
    "vite": "^6.3.5",
    "vitest": "^3.2.4"
  }
}
```

- [ ] **Step 2: Add Vitest config and test setup**

Update `frontend/vite.config.js`:

```js
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js"
  },
  build: {
    outDir: "../backend/src/main/resources/static",
    emptyOutDir: true
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true
      }
    }
  }
});
```

Create `frontend/src/test/setup.js`:

```js
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

global.ResizeObserver = ResizeObserverMock;
```

- [ ] **Step 3: Install the new test dependencies**

Run:

```bash
cd frontend
npm.cmd install
```

Expected:
- `package-lock.json` is updated.
- The install completes without missing package errors.

- [ ] **Step 4: Write the failing login layout test**

Create `frontend/src/views/__tests__/LoginView.spec.js`:

```js
import { mount } from "@vue/test-utils";
import LoginView from "../LoginView.vue";

const dictionary = {
  loginTitle: "Dealer Analysis Assistant",
  loginBody: "Use your access key to enter the workspace.",
  loginPlaceholder: "Enter access key",
  loginLoading: "Signing in...",
  loginButton: "Enter workspace",
  loginNoticeBody: "Authorized internal use only."
};

describe("LoginView", () => {
  it("renders the editorial split layout hooks", () => {
    const wrapper = mount(LoginView, {
      props: {
        accessKey: "",
        dictionary,
        locale: "zh",
        loginError: "",
        loginLoading: false
      },
      global: {
        stubs: {
          LanguageSwitcher: {
            template: "<button type=\"button\">EN</button>"
          }
        }
      }
    });

    expect(wrapper.find(".login-editorial-grid").exists()).toBe(true);
    expect(wrapper.find(".login-cover-panel").exists()).toBe(true);
    expect(wrapper.find(".login-access-panel").exists()).toBe(true);
    expect(wrapper.find(".login-access-panel .primary-button").exists()).toBe(true);
  });
});
```

- [ ] **Step 5: Run the login test and verify it fails before implementation**

Run:

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

Expected:
- `FAIL  src/views/__tests__/LoginView.spec.js`
- The assertion for `.login-editorial-grid`, `.login-cover-panel`, or `.login-access-panel` fails because those classes do not exist yet.

- [ ] **Step 6: Add the login editorial layout hooks**

Update `frontend/src/views/LoginView.vue` so the existing content is split into a cover panel and an access panel:

```vue
<template>
  <div class="login-shell">
    <header class="login-shell-header">
      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />
    </header>

    <section class="login-screen login-screen-editorial">
      <div class="login-editorial-grid">
        <div class="login-cover-panel">
          <div class="login-cover-copy">
            <div class="login-hero-logo-badge">
              <img src="/logo.png" alt="Brand logo" class="login-hero-logo-image" />
            </div>

            <div class="login-hero-text">
              <p class="eyebrow">{{ dictionary.loginTitle }}</p>
              <h2>{{ dictionary.loginTitle }}</h2>
              <p class="login-copy">{{ dictionary.loginBody }}</p>
            </div>
          </div>
        </div>

        <div class="login-access-panel">
          <form class="login-form" @submit.prevent="$emit('submit')">
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
    </section>
  </div>
</template>
```

- [ ] **Step 7: Run the login test again and verify it passes**

Run:

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

Expected:
- `PASS  src/views/__tests__/LoginView.spec.js`

- [ ] **Step 8: Commit**

Run:

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.js frontend/src/test/setup.js frontend/src/views/__tests__/LoginView.spec.js frontend/src/views/LoginView.vue
git commit -m "test: add frontend login layout coverage"
```

## Task 2: Add workspace component hooks for the editorial chrome

**Files:**
- Create: `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
- Create: `frontend/src/components/__tests__/MessageShell.spec.js`
- Modify: `frontend/src/components/layout/TopNav.vue`
- Modify: `frontend/src/components/layout/ExampleSidebar.vue`
- Modify: `frontend/src/components/chat/ChatInput.vue`
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
- Modify: `frontend/src/components/chat/UserMessage.vue`

- [ ] **Step 1: Write failing tests for the workspace chrome and role shells**

Create `frontend/src/components/__tests__/WorkspaceChrome.spec.js`:

```js
import { mount } from "@vue/test-utils";
import ChatInput from "../chat/ChatInput.vue";
import ExampleSidebar from "../layout/ExampleSidebar.vue";
import TopNav from "../layout/TopNav.vue";

const dictionary = {
  appName: "Dealer Analysis Assistant",
  appTagline: "Editorial workspace",
  clearChat: "Clear",
  logoutButton: "Sign out",
  openMenu: "Menu",
  closeMenu: "Close",
  newChat: "New chat",
  workspaceTitle: "Analysis Workspace",
  sidebarSection: "Prompts",
  historySection: "History",
  historyHint: "Current conversation",
  footerNote: "Internal use",
  inputPlaceholder: "Ask anything",
  sending: "Sending",
  sendButton: "Send",
  prompts: ["Analyze dealer pipeline"]
};

describe("workspace chrome", () => {
  it("renders editorial topbar and sidebar hooks", () => {
    const topbar = mount(TopNav, {
      props: {
        authVerified: true,
        dictionary,
        isSending: false,
        locale: "zh",
        statusMessage: "Ready"
      },
      global: {
        stubs: {
          LanguageSwitcher: {
            template: "<button type=\"button\">EN</button>"
          }
        }
      }
    });

    const sidebar = mount(ExampleSidebar, {
      props: {
        activeSessionLabel: "Current session",
        dictionary,
        isSending: false,
        showMobileSidebar: false
      }
    });

    const composer = mount(ChatInput, {
      props: {
        dictionary,
        isSending: false,
        modelValue: "Hello",
        requestError: "",
        toastMessage: ""
      }
    });

    expect(topbar.find(".topbar-editorial").exists()).toBe(true);
    expect(sidebar.find(".sidebar-editorial").exists()).toBe(true);
    expect(sidebar.find(".sidebar-action-block").exists()).toBe(true);
    expect(composer.find(".composer-card-editorial").exists()).toBe(true);
  });
});
```

Create `frontend/src/components/__tests__/MessageShell.spec.js`:

```js
import { mount } from "@vue/test-utils";
import AssistantMessage from "../chat/AssistantMessage.vue";
import UserMessage from "../chat/UserMessage.vue";

const dictionary = {
  assistantLabel: "Assistant",
  userLabel: "You",
  showThinking: "Show thinking",
  hideThinking: "Hide thinking"
};

describe("message shell", () => {
  it("keeps assistant and user cards visually distinct through classes", () => {
    const assistant = mount(AssistantMessage, {
      props: {
        dictionary,
        message: {
          id: "a1",
          html: "<p>Answer</p>",
          followUps: [],
          status: "Ready"
        }
      },
      global: {
        stubs: {
          FollowUpButtons: {
            template: "<div />"
          }
        }
      }
    });

    const user = mount(UserMessage, {
      props: {
        dictionary,
        message: {
          id: "u1",
          html: "<p>Question</p>"
        }
      }
    });

    expect(assistant.find(".avatar-assistant").exists()).toBe(true);
    expect(assistant.find(".message-card-assistant").exists()).toBe(true);
    expect(user.find(".avatar-user").exists()).toBe(true);
    expect(user.find(".message-card-user").exists()).toBe(true);
  });
});
```

- [ ] **Step 2: Run the workspace tests and verify they fail**

Run:

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js src/components/__tests__/MessageShell.spec.js
```

Expected:
- `FAIL  src/components/__tests__/WorkspaceChrome.spec.js`
- `FAIL  src/components/__tests__/MessageShell.spec.js`
- The missing class assertions fail because the editorial hooks are not in the templates yet.

- [ ] **Step 3: Add the top bar, sidebar, and composer hooks**

Update `frontend/src/components/layout/TopNav.vue`:

```vue
<template>
  <header class="topbar topbar-editorial">
    <div class="topbar-left">
      <button class="ghost-button mobile-only" type="button" @click="$emit('open-sidebar')">
        {{ dictionary.openMenu }}
      </button>

      <div class="topbar-brand">
        <div class="topbar-logo-badge">
          <img src="/logo.png" :alt="dictionary.appName" class="topbar-logo-image" />
        </div>

        <div class="topbar-title-group topbar-brand-copy">
          <p class="eyebrow">{{ dictionary.appTagline }}</p>
          <h2>{{ dictionary.appName }}</h2>
        </div>
      </div>
    </div>

    <div class="topbar-actions">
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
  <aside :class="['sidebar', 'sidebar-editorial', { 'sidebar-open': showMobileSidebar }]">
    <div class="sidebar-top">
      <div class="sidebar-intro">
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

    <section class="sidebar-section">
      <div class="section-head">
        <span>{{ dictionary.historySection }}</span>
      </div>

      <div class="session-card">
        <strong>{{ activeSessionLabel }}</strong>
        <p>{{ dictionary.historyHint }}</p>
      </div>
    </section>

    <div class="sidebar-footer">
      <p>{{ dictionary.footerNote }}</p>
    </div>
  </aside>
</template>
```

Update `frontend/src/components/chat/ChatInput.vue`:

```vue
<template>
  <div class="composer-shell">
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

- [ ] **Step 4: Add role-specific avatar and message card hooks**

Update `frontend/src/components/chat/AssistantMessage.vue`:

```vue
<template>
  <article class="message-row message-assistant">
    <div class="avatar avatar-assistant">AI</div>

    <div class="message-card message-card-assistant">
      <div class="message-meta">
        <span>{{ dictionary.assistantLabel }}</span>
        <span v-if="message.status">{{ message.status }}</span>
      </div>

      <ol v-if="message.steps?.length" class="message-steps">
        <li v-for="step in message.steps" :key="step">{{ step }}</li>
      </ol>

      <button
        v-if="message.thinking"
        class="thinking-toggle"
        type="button"
        @click="$emit('toggle-thinking', message)"
      >
        {{ message.thinkingExpanded ? dictionary.hideThinking : dictionary.showThinking }}
      </button>

      <div
        v-if="message.thinking && message.thinkingExpanded"
        class="thinking-panel markdown-body"
        v-html="message.thinkingHtml"
      ></div>

      <div v-if="message.html" class="markdown-body" v-html="message.html"></div>
      <span v-if="message.streaming" class="message-cursor" aria-hidden="true"></span>

      <FollowUpButtons
        :dictionary="dictionary"
        :follow-ups="message.followUps"
        @select="$emit('submit-follow-up', $event)"
      />
    </div>
  </article>
</template>
```

Update `frontend/src/components/chat/UserMessage.vue`:

```vue
<template>
  <article class="message-row message-user">
    <div class="avatar avatar-user">YOU</div>

    <div class="message-card message-card-user">
      <div class="message-meta">
        <span>{{ dictionary.userLabel }}</span>
      </div>

      <div v-if="message.html" class="markdown-body" v-html="message.html"></div>
    </div>
  </article>
</template>
```

- [ ] **Step 5: Run the workspace tests again and verify they pass**

Run:

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js src/components/__tests__/MessageShell.spec.js
```

Expected:
- `PASS  src/components/__tests__/WorkspaceChrome.spec.js`
- `PASS  src/components/__tests__/MessageShell.spec.js`

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/src/components/__tests__/WorkspaceChrome.spec.js frontend/src/components/__tests__/MessageShell.spec.js frontend/src/components/layout/TopNav.vue frontend/src/components/layout/ExampleSidebar.vue frontend/src/components/chat/ChatInput.vue frontend/src/components/chat/AssistantMessage.vue frontend/src/components/chat/UserMessage.vue
git commit -m "feat: add editorial workspace structure hooks"
```

## Task 3: Replace the green visual system with grayscale editorial styling

**Files:**
- Create: `frontend/src/__tests__/styleTokens.spec.js`
- Modify: `frontend/src/style.css`

- [ ] **Step 1: Write a failing style token regression test**

Create `frontend/src/__tests__/styleTokens.spec.js`:

```js
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("style tokens", () => {
  it("uses grayscale editorial tokens and removes the old green accents", () => {
    const css = readFileSync(resolve(process.cwd(), "src/style.css"), "utf8");

    expect(css).toContain("--page-bg: #f8f8f6;");
    expect(css).toContain("--surface: #ffffff;");
    expect(css).toContain("--text-main: #111111;");
    expect(css).toContain("--text-muted: #5f5f5f;");
    expect(css).toContain("--border-soft: #d9d9d4;");
    expect(css).toContain(".message-card-user");
    expect(css).toContain(".message-card-assistant");
    expect(css).not.toMatch(/#6ea781|#4b755d|#76ad87|#558667|#eff7f1|#e7f2ea/);
  });
});
```

- [ ] **Step 2: Run the style token test and verify it fails**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected:
- `FAIL  src/__tests__/styleTokens.spec.js`
- At least one expected grayscale token is missing, and/or the regex still finds the old green values.

- [ ] **Step 3: Rewrite the global tokens and component styling in `style.css`**

Update the top token block in `frontend/src/style.css`:

```css
:root {
  color-scheme: light;
  font-family: "Avenir Next", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.5;
  font-weight: 400;
  color: #111111;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  --page-bg: #f8f8f6;
  --surface: #ffffff;
  --surface-muted: #f1f1ee;
  --sidebar-bg: #f3f3f0;
  --border-soft: #d9d9d4;
  --border-strong: #c8c8c2;
  --text-main: #111111;
  --text-muted: #5f5f5f;
  --text-strong: #111111;
  --text-helper: #8a8a8a;
  --shadow-lg: 0 12px 40px rgba(17, 17, 17, 0.06);
  --shadow-md: 0 8px 24px rgba(17, 17, 17, 0.05);
  --shadow-sm: 0 4px 12px rgba(17, 17, 17, 0.04);
  --chat-column-width: 66rem;
}
```

Replace the shell styling with restrained editorial surfaces:

```css
body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
  color: var(--text-main);
  background: var(--page-bg);
}

body::before,
body::after {
  content: none;
}

.app-shell {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 312px minmax(0, 1fr);
  background: var(--surface);
}

.sidebar,
.sidebar-editorial {
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border-soft);
}

.topbar,
.topbar-editorial {
  padding: 0.9rem 1.25rem;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid var(--border-soft);
  box-shadow: none;
}

.primary-sidebar-button,
.primary-button {
  border: 1px solid #111111;
  background: #111111;
  color: #ffffff;
  box-shadow: none;
}

.ghost-button,
.prompt-card,
.follow-up-button,
.session-card,
.sidebar-section,
.workspace-badge,
.status-pill {
  border: 1px solid var(--border-soft);
  background: #ffffff;
  color: var(--text-main);
  box-shadow: none;
}
```

Add the new login, message, and composer treatments:

```css
.login-screen-editorial {
  padding: 2rem 1.5rem 3rem;
}

.login-editorial-grid {
  width: min(100%, 1080px);
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 420px);
  gap: 2rem;
  align-items: stretch;
}

.login-cover-panel,
.login-access-panel,
.chat-copy,
.empty-state,
.message-card,
.composer-card-editorial,
.sidebar-section {
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #ffffff;
  box-shadow: none;
}

.message-card-assistant {
  background: #ffffff;
}

.message-card-user {
  background: #f3f3f0;
  border-color: var(--border-strong);
}

.avatar-assistant,
.avatar-user {
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: #ffffff;
  color: #111111;
  box-shadow: none;
}

.composer-card-editorial:focus-within,
.text-input:focus {
  border-color: #111111;
  box-shadow: 0 0 0 3px rgba(17, 17, 17, 0.08);
}
```

- [ ] **Step 4: Run the style test and verify it passes**

Run:

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected:
- `PASS  src/__tests__/styleTokens.spec.js`

- [ ] **Step 5: Run the full frontend test suite**

Run:

```bash
cd frontend
npm.cmd run test
```

Expected:
- All three spec files pass.
- No Vue runtime warnings appear in the test output.

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/src/__tests__/styleTokens.spec.js frontend/src/style.css
git commit -m "feat: apply black and white editorial styling"
```

## Task 4: Verify the built UI and sync the backend static bundle

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
- Vite writes the updated frontend bundle into `backend/src/main/resources/static`.
- No unresolved asset or CSS parse errors appear.

- [ ] **Step 2: Perform a manual QA pass against the approved spec**

Run:

```bash
cd frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

Expected:
- The development server starts without CSS or Vue compile errors.
- The UI is available at `http://127.0.0.1:5173`.

Check these states in the running UI:

```text
1. Login page reads like a cover page with a separate access panel.
2. The top bar is thinner and flatter than the current green version.
3. The sidebar uses gray structure instead of gradients or glass.
4. User and assistant messages are distinguishable without any green accent.
5. The send button is the highest-contrast action on the page.
6. Mobile layout still works below 1040px and 720px breakpoints.
```

- [ ] **Step 3: Run one final regression pass**

Run:

```bash
cd frontend
npm.cmd run test
npm.cmd run build
```

Expected:
- Tests still pass after any QA tweaks.
- The final build still succeeds.

- [ ] **Step 4: Commit**

Run:

```bash
git add frontend backend/src/main/resources/static
git commit -m "build: ship editorial monochrome frontend"
```
