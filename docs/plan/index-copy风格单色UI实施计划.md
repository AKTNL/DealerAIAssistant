# Index Copy 风格单色 UI 实施计划

> **面向代理工作器：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 重做当前单色前端，使登录页和聊天工作区结构借鉴 `index copy.html` 的布局节奏，同时保留已批准的黑/白/灰视觉体系。

**架构：** 保留现有 Vue 视图/组件拆分，复用此分支中已有的测试/工具基础。用聚焦的单入口框架替换当前分割登录结构，向现有聊天框架添加一小组新工作区布局钩子，然后围绕这些结构重写 `frontend/src/style.css` 的样式，不重新引入彩色强调色或玻璃效果。

**技术栈：** Vue 3、Vite、Vitest、Vue Test Utils、纯 CSS

---

## 文件结构

### 需修改的现有文件
- `frontend/src/views/LoginView.vue`
  - 用居中单入口构图替换分割登录结构。
- `frontend/src/views/__tests__/LoginView.spec.js`
  - 将登录断言从分割面板钩子更新为单入口钩子，同时保留行为检查。
- `frontend/src/views/ChatView.vue`
  - 为更紧凑的产品风格框架添加新工作区包裹钩子。
- `frontend/src/components/layout/TopNav.vue`
  - 添加参考头部结构启发的产品头部包裹钩子。
- `frontend/src/components/layout/ExampleSidebar.vue`
  - 为支持轨道/问题列表结构添加更清晰的分组钩子。
- `frontend/src/components/chat/ChatInput.vue`
  - 添加停靠/工具栏钩子，使输入框可以样式化为底部控制栏。
- `frontend/src/components/__tests__/WorkspaceChrome.spec.js`
  - 更新结构断言以匹配新工作区框架钩子，同时保留键盘提交覆盖。
- `frontend/src/__tests__/styleTokens.spec.js`
  - 检查新登录/工作区不变量并保持单色 token 覆盖。
- `frontend/src/style.css`
  - 为单入口登录页和更紧凑的参考启发工作区框架重做布局和间距。
- `backend/src/main/resources/static/index.html`
  - 当前端最终构建产生新资产对时将变化。
- `backend/src/main/resources/static/assets/*`
  - 当前端最终构建产生新资产对时将变化。

### 预期保持不变的现有文件
- `frontend/src/components/chat/AssistantMessage.vue`
- `frontend/src/components/chat/UserMessage.vue`
- `frontend/src/components/__tests__/MessageShell.spec.js`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.js`
- `frontend/src/test/setup.js`

## 任务 1：将登录页从分割编辑舞台转换为聚焦单入口布局

**文件：**
- 修改：`frontend/src/views/__tests__/LoginView.spec.js`
- 修改：`frontend/src/views/LoginView.vue`

- [ ] **步骤 1：重写登录测试以预期新单入口钩子**

更新 `frontend/src/views/__tests__/LoginView.spec.js`，使结构断言针对新居中布局：

```js
test("渲染聚焦的单入口登录布局", () => {
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

- [ ] **步骤 2：运行登录测试并验证因预期原因失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

预期：
- `FAIL  src/views/__tests__/LoginView.spec.js`
- 失败显示 `.login-entry-shell`、`.login-entry-panel` 或 `.login-entry-stack` 缺失，因为视图仍使用分割布局。

- [ ] **步骤 3：用居中单入口框架替换分割登录模板**

更新 `frontend/src/views/LoginView.vue`：

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
                <img src="/logo.png" alt="品牌Logo" class="login-hero-logo-image" />
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

- [ ] **步骤 4：再次运行登录测试并验证通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/views/__tests__/LoginView.spec.js
```

预期：
- `PASS  src/views/__tests__/LoginView.spec.js`
- 加载、错误重放和提交守卫的现有行为测试保持绿色。

- [ ] **步骤 5：提交**

```bash
git add frontend/src/views/__tests__/LoginView.spec.js frontend/src/views/LoginView.vue
git commit -m "feat: refocus login into single entry shell"
```

## 任务 2：将聊天工作区框架重构为参考布局节奏

**文件：**
- 修改：`frontend/src/components/__tests__/WorkspaceChrome.spec.js`
- 修改：`frontend/src/views/ChatView.vue`
- 修改：`frontend/src/components/layout/TopNav.vue`
- 修改：`frontend/src/components/layout/ExampleSidebar.vue`
- 修改：`frontend/src/components/chat/ChatInput.vue`

- [ ] **步骤 1：扩展工作区测试以针对新框架钩子**

更新 `frontend/src/components/__tests__/WorkspaceChrome.spec.js`：

```js
import { mount } from "@vue/test-utils";
import ChatInput from "../chat/ChatInput.vue";
import ExampleSidebar from "../layout/ExampleSidebar.vue";
import TopNav from "../layout/TopNav.vue";

// 保留现有字典和键盘提交测试

test("渲染参考启发的工作区框架钩子", () => {
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
      activeSessionLabel: "当前会话",
      dictionary
    }
  });
  const chatInput = mount(ChatInput, {
    props: {
      dictionary,
      modelValue: "你好"
    }
  });

  expect(topNav.find(".topbar-product").exists()).toBe(true);
  expect(topNav.find(".topbar-tools").exists()).toBe(true);
  expect(sidebar.find(".sidebar-rail").exists()).toBe(true);
  expect(sidebar.find(".sidebar-group-list").exists()).toBe(true);
  expect(chatInput.find(".composer-dock").exists()).toBe(true);
});
```

- [ ] **步骤 2：运行工作区测试并验证失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js
```

预期：
- `FAIL  src/components/__tests__/WorkspaceChrome.spec.js`
- 缺失 `.topbar-product`、`.sidebar-rail`、`.sidebar-group-list` 或 `.composer-dock`。

- [ ] **步骤 3：添加产品头部和支持轨道钩子**

更新 `frontend/src/components/layout/TopNav.vue`：

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

更新 `frontend/src/components/layout/ExampleSidebar.vue`：

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

- [ ] **步骤 4：添加输入框停靠钩子和工作区包裹器**

更新 `frontend/src/components/chat/ChatInput.vue`：

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

更新 `frontend/src/views/ChatView.vue`：

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

- [ ] **步骤 5：再次运行工作区测试并验证通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/components/__tests__/WorkspaceChrome.spec.js
```

预期：
- `PASS  src/components/__tests__/WorkspaceChrome.spec.js`
- 现有键盘提交断言保持绿色。

- [ ] **步骤 6：提交**

```bash
git add frontend/src/components/__tests__/WorkspaceChrome.spec.js frontend/src/views/ChatView.vue frontend/src/components/layout/TopNav.vue frontend/src/components/layout/ExampleSidebar.vue frontend/src/components/chat/ChatInput.vue
git commit -m "feat: align workspace shell to index copy structure"
```

## 任务 3：围绕参考启发结构重写登录和工作区框架样式

**文件：**
- 修改：`frontend/src/__tests__/styleTokens.spec.js`
- 修改：`frontend/src/style.css`

- [ ] **步骤 1：更新样式回归测试以预期新登录/工作区不变量**

扩展 `frontend/src/__tests__/styleTokens.spec.js`：

```js
test("支持聚焦的登录入口和参考启发的工作区框架", () => {
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

- [ ] **步骤 2：运行样式测试并验证失败**

运行：

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

预期：
- `FAIL  src/__tests__/styleTokens.spec.js`
- 缺失 `.login-entry-shell`、`.login-entry-panel` 或 `.composer-dock` 规则覆盖。

- [ ] **步骤 3：在 `style.css` 中重写登录和工作区布局规则**

更新登录框架，脱离分割网格：

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

更新工作区框架以匹配参考节奏：

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

保持响应式规则对齐：

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

- [ ] **步骤 4：运行样式和视图测试并验证通过**

运行：

```bash
cd frontend
npm.cmd run test -- src/__tests__/styleTokens.spec.js src/views/__tests__/LoginView.spec.js src/components/__tests__/WorkspaceChrome.spec.js
```

预期：
- 所有三个文件通过。
- 登录测试现在验证单入口结构。

- [ ] **步骤 5：运行完整前端测试套件**

运行：

```bash
cd frontend
npm.cmd run test
```

预期：
- 所有四个前端 spec 文件通过。
- 输出中无 Vue 运行时警告。

- [ ] **步骤 6：提交**

```bash
git add frontend/src/__tests__/styleTokens.spec.js frontend/src/style.css
git commit -m "feat: restyle shell around index copy structure"
```

## 任务 4：重新构建并验证后端服务的静态框架

**文件：**
- 修改：`backend/src/main/resources/static/index.html`
- 修改：`backend/src/main/resources/static/assets/*`

- [ ] **步骤 1：构建前端包**

运行：

```bash
cd frontend
npm.cmd run build
```

预期：
- Vite 将新的 `index.html` 加上新的哈希 CSS/JS 对写入 `../backend/src/main/resources/static`。
- 无 CSS 解析错误或未解析资产错误。

- [ ] **步骤 2：对最终源码状态再次运行前端测试套件**

运行：

```bash
cd frontend
npm.cmd run test
```

预期：
- 在任何最终调整后所有前端测试仍然通过。

- [ ] **步骤 3：进行运行时健全性检查**

运行：

```bash
cd frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

预期：
- 开发服务器启动无编译错误。
- 框架在 `http://127.0.0.1:5173` 加载。

检查以下状态：

```text
1. 登录页是单聚焦入口区域，非双面板分割。
2. 头部纤细且像产品。
3. 侧边栏读起来像分组支持轨道。
4. 聊天正文感觉更紧凑，不那么像浮动卡片。
5. 输入框读起来像底部控制栏。
6. 无蓝/青强调色或玻璃态效果残留。
```

- [ ] **步骤 4：将后端静态资产目录同步到当前构建**

运行：

```bash
Get-Content backend/src/main/resources/static/index.html
Get-ChildItem backend/src/main/resources/static/assets
```

预期：
- `index.html` 引用当前哈希的 CSS 和 JS 文件。
- 在最终交付前移除不再引用的任何过期哈希文件。

- [ ] **步骤 5：提交**

```bash
git add frontend backend/src/main/resources/static
git commit -m "build: ship index-copy-inspired monochrome frontend"
```
