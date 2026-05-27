# 黑白编辑风格UI实施计划

> **面向agentic工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 将现有的绿色玻璃拟态（glassmorphism）前端转换为以白色为主、黑/灰色调为主的编辑风格UI，同时不改变应用的核心行为。

**架构：** 保留当前的Vue视图/组件拆分结构，为新编辑布局添加一小组语义化类钩子，并通过 `frontend/src/style.css` 中的共享CSS令牌驱动视觉变化。添加轻量级的Vitest组件测试，使视觉重写仍具备结构性回归覆盖。

**技术栈：** Vue 3、Vite、Vitest、Vue Test Utils、纯CSS

---

## 文件结构

### 需要修改的现有文件
- `frontend/package.json`
  - 添加测试脚本和前端测试devDependencies。
- `frontend/package-lock.json`
  - 安装后锁定Vitest和Vue Test Utils依赖图。
- `frontend/vite.config.js`
  - 在现有Vite构建配置旁添加Vitest配置。
- `frontend/src/style.css`
  - 将绿色/玻璃拟态令牌和组件样式替换为单色编辑风格规则。
- `frontend/src/views/LoginView.vue`
  - 为登录封面和访问面板添加稳定的编辑布局钩子。
- `frontend/src/components/layout/TopNav.vue`
  - 为更薄的头部布局添加稳定的编辑风格钩子。
- `frontend/src/components/layout/ExampleSidebar.vue`
  - 为侧边栏内容块添加稳定的编辑风格钩子。
- `frontend/src/components/chat/ChatInput.vue`
  - 为输入框外壳添加稳定的编辑风格钩子。
- `frontend/src/components/chat/AssistantMessage.vue`
  - 为助手消息添加稳定的角色特定单色类。
- `frontend/src/components/chat/UserMessage.vue`
  - 为用户消息添加稳定的角色特定单色类。

### 需要创建的新文件
- `frontend/src/test/setup.js`
  - 用于在 `jsdom` 中渲染Vue组件的共享Vitest设置。
- `frontend/src/views/__tests__/LoginView.spec.js`
  - 覆盖登录页的编辑风格分栏布局和提交操作可见性。
- `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
  - 覆盖顶部栏、侧边栏和输入框的编辑风格类钩子。
- `frontend/src/components/__tests__/MessageShell.spec.js`
  - 覆盖用户和助手消息的单色角色区分。
- `frontend/src/__tests__/styleTokens.spec.js`
  - 守卫新的灰度令牌集，阻止旧的绿色强调色令牌重新出现。

## 任务1：添加前端测试工具并锁定登录布局钩子

**文件：**
- 创建：`frontend/src/test/setup.js`
- 创建：`frontend/src/views/__tests__/LoginView.spec.js`
- 修改：`frontend/package.json`
- 修改：`frontend/package-lock.json`
- 修改：`frontend/vite.config.js`
- 修改：`frontend/src/views/LoginView.vue`

- [ ] **步骤1：添加前端测试脚本和Vitest依赖**

更新 `frontend/package.json`：

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

- [ ] **步骤2：添加Vitest配置和测试设置**

更新 `frontend/vite.config.js`：

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

创建 `frontend/src/test/setup.js`：

```js
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

global.ResizeObserver = ResizeObserverMock;
```

- [ ] **步骤3：安装新的测试依赖**

运行：

```bash
cd frontend
npm.cmd install
```

预期结果：
- `package-lock.json` 已更新。
- 安装完成且无缺少包的错误。

- [ ] **步骤4：编写会失败的登录布局测试**

创建 `frontend/src/views/__tests__/LoginView.spec.js`：

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

- [ ] **步骤5：运行登录测试并验证在实现前它会失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

预期结果：
- `FAIL  src/views/__tests__/LoginView.spec.js`
- 对 `.login-editorial-grid`、`.login-cover-panel` 或 `.login-access-panel` 的断言失败，因为这些类尚不存在。

- [ ] **步骤6：添加登录编辑布局钩子**

更新 `frontend/src/views/LoginView.vue`，使现有内容拆分为封面面板和访问面板：

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

- [ ] **步骤7：再次运行登录测试并验证它通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

预期结果：
- `PASS  src/views/__tests__/LoginView.spec.js`

- [ ] **步骤8：提交**

运行：

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.js frontend/src/test/setup.js frontend/src/views/__tests__/LoginView.spec.js frontend/src/views/LoginView.vue
git commit -m "test: add frontend login layout coverage"
```

## 任务2：为编辑风格chrome添加工作区组件钩子

**文件：**
- 创建：`frontend/src/components/__tests__/WorkspaceChrome.spec.js`
- 创建：`frontend/src/components/__tests__/MessageShell.spec.js`
- 修改：`frontend/src/components/layout/TopNav.vue`
- 修改：`frontend/src/components/layout/ExampleSidebar.vue`
- 修改：`frontend/src/components/chat/ChatInput.vue`
- 修改：`frontend/src/components/chat/AssistantMessage.vue`
- 修改：`frontend/src/components/chat/UserMessage.vue`

- [ ] **步骤1：为工作区chrome和角色外壳编写会失败的测试**

创建 `frontend/src/components/__tests__/WorkspaceChrome.spec.js`：

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

创建 `frontend/src/components/__tests__/MessageShell.spec.js`：

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

- [ ] **步骤2：运行工作区测试并验证它们失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js src/components/__tests__/MessageShell.spec.js
```

预期结果：
- `FAIL  src/components/__tests__/WorkspaceChrome.spec.js`
- `FAIL  src/components/__tests__/MessageShell.spec.js`
- 缺失的类断言失败，因为编辑风格钩子尚未在模板中。

- [ ] **步骤3：添加顶部栏、侧边栏和输入框钩子**

更新 `frontend/src/components/layout/TopNav.vue`：

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

更新 `frontend/src/components/layout/ExampleSidebar.vue`：

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

更新 `frontend/src/components/chat/ChatInput.vue`：

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

- [ ] **步骤4：添加角色特定的头像和消息卡片钩子**

更新 `frontend/src/components/chat/AssistantMessage.vue`：

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

更新 `frontend/src/components/chat/UserMessage.vue`：

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

- [ ] **步骤5：再次运行工作区测试并验证它们通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js src/components/__tests__/MessageShell.spec.js
```

预期结果：
- `PASS  src/components/__tests__/WorkspaceChrome.spec.js`
- `PASS  src/components/__tests__/MessageShell.spec.js`

- [ ] **步骤6：提交**

运行：

```bash
git add frontend/src/components/__tests__/WorkspaceChrome.spec.js frontend/src/components/__tests__/MessageShell.spec.js frontend/src/components/layout/TopNav.vue frontend/src/components/layout/ExampleSidebar.vue frontend/src/components/chat/ChatInput.vue frontend/src/components/chat/AssistantMessage.vue frontend/src/components/chat/UserMessage.vue
git commit -m "feat: add editorial workspace structure hooks"
```

## 任务3：将绿色视觉系统替换为灰度编辑风格

**文件：**
- 创建：`frontend/src/__tests__/styleTokens.spec.js`
- 修改：`frontend/src/style.css`

- [ ] **步骤1：编写会失败的样式令牌回归测试**

创建 `frontend/src/__tests__/styleTokens.spec.js`：

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

- [ ] **步骤2：运行样式令牌测试并验证它失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

预期结果：
- `FAIL  src/__tests__/styleTokens.spec.js`
- 至少缺少一个预期的灰度令牌，并且/或者正则表达式仍然匹配到旧的绿色值。

- [ ] **步骤3：在 `style.css` 中重写全局令牌和组件样式**

更新 `frontend/src/style.css` 中的顶部令牌块：

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

将外壳样式替换为克制的编辑风格表面样式：

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

添加新的登录、消息和输入框样式处理：

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

- [ ] **步骤4：运行样式测试并验证它通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

预期结果：
- `PASS  src/__tests__/styleTokens.spec.js`

- [ ] **步骤5：运行完整的前端测试套件**

运行：

```bash
cd frontend
npm.cmd run test
```

预期结果：
- 所有三个spec文件都通过。
- 测试输出中没有Vue运行时警告。

- [ ] **步骤6：提交**

运行：

```bash
git add frontend/src/__tests__/styleTokens.spec.js frontend/src/style.css
git commit -m "feat: apply black and white editorial styling"
```

## 任务4：验证构建的UI并同步后端静态包

**文件：**
- 修改：`backend/src/main/resources/static/index.html`
- 修改：`backend/src/main/resources/static/assets/*`

- [ ] **步骤1：构建前端包**

运行：

```bash
cd frontend
npm.cmd run build
```

预期结果：
- Vite将更新后的前端包写入 `backend/src/main/resources/static`。
- 没有未解决的资源或CSS解析错误。

- [ ] **步骤2：对照批准的规格执行手动QA检查**

运行：

```bash
cd frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

预期结果：
- 开发服务器启动时没有CSS或Vue编译错误。
- UI可在 `http://127.0.0.1:5173` 访问。

检查运行中UI的以下状态：

```text
1. 登录页面看起来像封面页，带有独立的访问面板。
2. 顶部栏比当前绿色版本更薄、更平整。
3. 侧边栏使用灰色结构，而非渐变或玻璃效果。
4. 用户和助手消息可区分，且没有任何绿色强调色。
5. 发送按钮是页面上对比度最高的操作元素。
6. 移动端布局在1040px和720px断点以下仍可正常工作。
```

- [ ] **步骤3：执行最后一次回归检查**

运行：

```bash
cd frontend
npm.cmd run test
npm.cmd run build
```

预期结果：
- 在任何QA调整后测试仍然通过。
- 最终构建仍然成功。

- [ ] **步骤4：提交**

运行：

```bash
git add frontend backend/src/main/resources/static
git commit -m "build: ship editorial monochrome frontend"
```