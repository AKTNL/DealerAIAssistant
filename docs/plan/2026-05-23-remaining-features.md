# 待完成功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 F34（输入框自适应高度）、F16+F17（欢迎语与清空提示）、F23+F38（流式阶段状态统一），前端改动为主，后端零改动。

**Architecture:** F34 用 CSS `field-sizing: content` + JS `scrollHeight` 降级，`@supports` 隔离；F16+F17 用 `role: 'system'` 虚拟消息替代布尔标记；F23+F38 用 `streamPhase` 三态枚举（thinking/generating/idle）统一驱动 TopNav 状态文字和气泡内 loading。

**Tech Stack:** Vue 3 (Composition API), CSS `@supports`, Vitest

---

## Task 1: F34 — CSS 自适应高度

**Files:**
- Modify: `frontend/src/style.css`

### Step 1: 写测试

`frontend/src/__tests__/styleTokens.spec.js` 中新增：

```javascript
import { describe, expect, it } from "vitest";

// 在文件末尾追加
describe("composer-input auto-resize", () => {
  it("declares field-sizing: content inside @supports", () => {
    // Importing the CSS file is already handled by the test setup.
    // This test validates that the CSS has been loaded in the document.
    const styleSheets = Array.from(document.styleSheets);
    const hasFieldSizing = styleSheets.some((sheet) => {
      try {
        return Array.from(sheet.cssRules || []).some(
          (rule) =>
            rule instanceof CSSSupportsRule &&
            rule.cssRules &&
            Array.from(rule.cssRules).some(
              (inner) =>
                inner.selectorText === ".composer-input" &&
                inner.style.fieldSizing === "content"
            )
        );
      } catch {
        return false;
      }
    });

    expect(hasFieldSizing).toBe(true);
  });
});
```

### Step 2: 跑测试确认失败

```bash
cd frontend && npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected: FAIL — `.composer-input` 尚无 `field-sizing: content`

### Step 3: 加 CSS

在 `frontend/src/style.css` 的 `.composer-input` 规则块内追加：

```css
.composer-input {
  /* 现有属性不变 */
  max-height: 200px;
  overflow-y: auto;
}

@supports (field-sizing: content) {
  .composer-input {
    field-sizing: content;
    height: auto !important;
  }
}
```

### Step 4: 跑测试确认通过

```bash
cd frontend && npm.cmd run test -- src/__tests__/styleTokens.spec.js
```

Expected: PASS

### Step 5: 提交

```bash
cd /d "D:\高知特\int-group02-agent\int-group02-agent" && git add frontend/src/style.css frontend/src/__tests__/styleTokens.spec.js && git commit -m "feat: add CSS field-sizing auto-resize for composer input"
```

---

## Task 2: F34 — JS autoResize 函数

**Files:**
- Modify: `frontend/src/components/chat/ChatInput.vue` (script: add `autoResize`, call in `handleInput`)
- Modify: `frontend/src/components/__tests__/ChatInput.spec.js`

### Step 1: 写测试

在 `frontend/src/components/__tests__/ChatInput.spec.js` 中新增：

```javascript
test("auto-resizes textarea height on input", async () => {
  const wrapper = mountChatInput();

  const textarea = wrapper.find("textarea").element;

  // 模拟内容超出默认高度
  Object.defineProperty(textarea, "scrollHeight", {
    configurable: true,
    get() {
      return 80;
    }
  });

  await wrapper.find("textarea").setValue("Line 1\nLine 2\nLine 3");

  // JS 应设置 height 为 scrollHeight 像素值
  expect(textarea.style.height).toBe("80px");
});
```

### Step 2: 跑测试确认失败

```bash
cd frontend && npm.cmd run test -- src/components/__tests__/ChatInput.spec.js
```

Expected: FAIL — `handleInput` 未调用 `autoResize`，height 不为 `80px`

### Step 3: 实现 autoResize

修改 `frontend/src/components/chat/ChatInput.vue` 的 `<script setup>`：

```javascript
// handleInput 中加 autoResize 调用
function handleInput(event) {
  emit("update:modelValue", event.target.value);
  autoResize(event.target);
}

// 新增函数
function autoResize(el) {
  el.style.height = "auto";
  el.style.height = el.scrollHeight + "px";
}
```

### Step 4: 跑测试确认通过

```bash
cd frontend && npm.cmd run test -- src/components/__tests__/ChatInput.spec.js
```

Expected: PASS

### Step 5: 提交

```bash
cd /d "D:\高知特\int-group02-agent\int-group02-agent" && git add frontend/src/components/chat/ChatInput.vue frontend/src/components/__tests__/ChatInput.spec.js && git commit -m "feat: add JS scrollHeight auto-resize fallback for composer"
```

---

## Task 3: F16+F17 — SystemMessage 虚拟消息

**Files:**
- Create: `frontend/src/components/chat/SystemMessage.vue`
- Modify: `frontend/src/components/chat/ChatMessageList.vue`
- Modify: `frontend/src/composables/useChat.js`
- Modify: `frontend/src/components/__tests__/ChatMessageList.spec.js`
- Modify: `frontend/src/composables/__tests__/useChat.spec.js`

### Step 1: 创建 SystemMessage.vue

```vue
<script setup>
defineProps({
  type: { type: String, required: true },
  dictionary: { type: Object, required: true }
});
</script>

<template>
  <div v-if="type === 'welcome'" class="empty-state">
    <h3>{{ dictionary.welcomeTitle }}</h3>
    <p>{{ dictionary.welcomeBody }}</p>
  </div>
  <div v-else-if="type === 'system-clear'" class="empty-state">
    <h3>{{ dictionary.clearSuccess }}</h3>
    <p>{{ dictionary.welcomeBody }}</p>
  </div>
</template>
```

### Step 2: 更新 ChatMessageList.vue

修改 `<script setup>` — 新增 SystemMessage import：

```javascript
import SystemMessage from "./SystemMessage.vue";
```

修改 `<template>` — 删除空的 `<div v-if="!hasMessages" class="empty-state">` 分支，`v-for` 中新增 system 分支。`hasMessages` prop 定义保留（ChatView 仍在传），仅在模板中不再消费。

```vue
<template>
  <div>
    <template v-for="message in messages" :key="message.id">
      <SystemMessage
        v-if="message.role === 'system'"
        :type="message.type"
        :dictionary="dictionary"
      />

      <UserMessage
        v-else-if="message.role === 'user'"
        :dictionary="dictionary"
        :message="message"
      />

      <AssistantMessage
        v-else
        :dictionary="dictionary"
        :locale="locale"
        :message="message"
        @submit-follow-up="$emit('submit-follow-up', $event)"
        @toggle-thinking="$emit('toggle-thinking', $event)"
      />
    </template>
  </div>
</template>
```

删除 `hasMessages` prop 定义（不再需要）。

### Step 3: 更新 useChat.js

「初始化」部分：`messages` 初始值改为含 welcome 虚拟消息：

```javascript
// 第 24 行，将
const messages = ref([]);
// 改为
const messages = ref([createSystemMessage("welcome")]);
```

「清空」部分：`resetChatSession` 中 `messages.value = []` 改为 `messages.value = [createSystemMessage("system-clear")]`（第 251 行）。

新增 `createSystemMessage` 函数，放在 `createUserMessage` 之前：

```javascript
function createSystemMessage(type) {
  return {
    id: `${type}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    role: "system",
    type,
    timestamp: Date.now()
  };
}
```

### Step 4: 更新 ChatMessageList 测试

在 `ChatMessageList.spec.js` 中：

```javascript
// 新增 SystemMessageStub
const SystemMessageStub = {
  props: ["dictionary", "type"],
  template: `
    <article data-test="system-message" :data-type="type">
      <h3 v-if="type === 'welcome'">{{ dictionary.welcomeTitle }}</h3>
      <h3 v-else-if="type === 'system-clear'">{{ dictionary.clearSuccess }}</h3>
      <p>{{ dictionary.welcomeBody }}</p>
    </article>
  `
};

// mountList 的 global.stubs 中新增
SystemMessage: SystemMessageStub,

// dictionary 中新增
welcomeTitle: "Welcome",
welcomeBody: "Start with a prompt.",
clearSuccess: "Session cleared.",
```

新增测试：

```javascript
it("renders the welcome system message initially (replaces old empty-state)", () => {
  const wrapper = mountList({
    hasMessages: false,
    messages: [{ id: "welcome-1", role: "system", type: "welcome" }]
  });

  const systemEl = wrapper.find('[data-test="system-message"]');
  expect(systemEl.exists()).toBe(true);
  expect(systemEl.attributes("data-type")).toBe("welcome");
  expect(systemEl.text()).toContain("Welcome");
  expect(systemEl.text()).toContain("Start with a prompt.");
});

it("renders system-clear message after session clear", () => {
  const wrapper = mountList({
    hasMessages: false,
    messages: [{ id: "system-clear-1", role: "system", type: "system-clear" }]
  });

  const systemEl = wrapper.find('[data-test="system-message"]');
  expect(systemEl.exists()).toBe(true);
  expect(systemEl.attributes("data-type")).toBe("system-clear");
  expect(systemEl.text()).toContain("Session cleared.");
});
```

原来的 `renders the empty state when there are no messages` 测试改为验证 welcome system message：

```javascript
it("renders welcome system message when messages only contains welcome", () => {
  const wrapper = mountList({
    hasMessages: false,
    messages: [{ id: "welcome-1", role: "system", type: "welcome" }]
  });

  expect(wrapper.text()).toContain("Welcome");
  expect(wrapper.text()).toContain("Start with a prompt.");
});
```

### Step 5: 更新 useChat 测试

在 `useChat.spec.js` 中新增：

```javascript
it("initializes messages with a welcome system message", () => {
  const wrapper = mountChatHarness(ref(null));

  expect(wrapper.vm.messages).toHaveLength(1);
  expect(wrapper.vm.messages[0].role).toBe("system");
  expect(wrapper.vm.messages[0].type).toBe("welcome");
});

it("replaces messages with system-clear after clearing the session", async () => {
  clearSessionMock.mockResolvedValue(undefined);

  streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
    onEvent({ event: "message", data: "Hello" });
    onEvent({ event: "done", data: "[DONE]" });
  });
  const wrapper = mountChatHarness(ref(null));

  await wrapper.vm.submitPrompt("Hello");
  expect(wrapper.vm.messages[0].role).toBe("system");
  expect(wrapper.vm.messages).toHaveLength(3); // welcome + user + assistant

  await wrapper.vm.handleClearSession();

  expect(wrapper.vm.messages).toHaveLength(1);
  expect(wrapper.vm.messages[0].role).toBe("system");
  expect(wrapper.vm.messages[0].type).toBe("system-clear");
});
```

### Step 6: 跑测试确认通过

```bash
cd frontend && npm.cmd run test -- src/composables/__tests__/useChat.spec.js src/components/__tests__/ChatMessageList.spec.js
```

Expected: PASS

### Step 7: 提交

```bash
cd /d "D:\高知特\int-group02-agent\int-group02-agent" && git add frontend/src/components/chat/SystemMessage.vue frontend/src/components/chat/ChatMessageList.vue frontend/src/composables/useChat.js frontend/src/components/__tests__/ChatMessageList.spec.js frontend/src/composables/__tests__/useChat.spec.js && git commit -m "feat: replace empty-state boolean with welcome/system-clear virtual messages"
```

---

## Task 4: F23+F38 — StreamPhase 三态枚举

**Files:**
- Modify: `frontend/src/composables/useChat.js`
- Modify: `frontend/src/i18n/messages.js`
- Modify: `frontend/src/components/layout/TopNav.vue`
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
- Modify: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/composables/__tests__/useChat.spec.js`

### Step 1: 更新 i18n messages.js

在 `zh` 对象中新增（`statusThinking` 已有值改为"思考中…"不调整，保持"助手正在整理回复"作 idle 兜底）：

```javascript
// zh 新增
streamPhaseThinking: "正在思考…",
streamPhaseGenerating: "正在生成…",
bubbleThinking: "思考中…",

// en 新增
streamPhaseThinking: "Thinking…",
streamPhaseGenerating: "Generating…",
bubbleThinking: "Thinking…",
```

### Step 2: 更新 useChat.js — 添加 streamPhase

文件顶部新增枚举常量（在 `SCROLL_LOCK_THRESHOLD` 之后）：

```javascript
const STREAM_PHASE = { IDLE: "idle", THINKING: "thinking", GENERATING: "generating" };
```

在 `useChat` 函数内 state 声明区新增（在 `messages` 之后）：

```javascript
const streamPhase = ref(STREAM_PHASE.IDLE);
```

`submitPrompt` 函数内，在 `isSending.value = true;` 之后（第 330 行附近）新增：

```javascript
streamPhase.value = STREAM_PHASE.THINKING;
```

在 `onEvent` 回调的 `event === "message"` 分支（第 353 行附近），`if` 内开头新增：

```javascript
// 检测思考→生成阶段切迁
if (streamPhase.value === STREAM_PHASE.THINKING) {
  if (eventText.includes("</think>")) {
    streamPhase.value = STREAM_PHASE.GENERATING;
  } else if (!eventText.includes("<think>") && eventText.trim().length > 0) {
    streamPhase.value = STREAM_PHASE.GENERATING;
  }
}
```

在 `done` 分支（第 376 行附近）新增：

```javascript
streamPhase.value = STREAM_PHASE.IDLE;
```

在 `error` 分支（第 367 行附近）新增：

```javascript
streamPhase.value = STREAM_PHASE.IDLE;
```

在 `catch` 块（第 385 行附近）开头新增：

```javascript
streamPhase.value = STREAM_PHASE.IDLE;
```

在 `finally` 块（第 399 行附近）开头新增：

```javascript
streamPhase.value = STREAM_PHASE.IDLE;
```

return 对象中新增：

```javascript
streamPhase,
```

### Step 3: 更新 TopNav.vue

新增 prop：

```javascript
streamPhase: {
  type: String,
  default: "idle"
}
```

状态文字改为三态：

```vue
<span v-if="statusMessage || streamPhase !== 'idle'" class="status-pill">
  {{ streamPhase === 'thinking' ? dictionary.streamPhaseThinking
    : streamPhase === 'generating' ? dictionary.streamPhaseGenerating
    : statusMessage }}
</span>
```

### Step 4: 更新 AssistantMessage.vue

新增 prop：

```javascript
streamPhase: { type: String, default: "idle" },
```

在 `<div v-if="message.streaming && !message.html" class="thinking-dots">` 块（第 546 行附近）之前加入 thinking-indicator：

```vue
<div
  v-if="message.streaming && streamPhase === 'thinking' && !message.html"
  class="thinking-indicator"
>
  <span class="thinking-dot"></span>
  {{ dictionary.bubbleThinking }}
</div>
```

### Step 5: 更新 ChatView.vue

在 `useChat` 解构中新增 `streamPhase`：

```javascript
const {
  // ... 现有解构 ...
  streamPhase
} = useChat({ ... });
```

TopNav 标签新增 `streamPhase` prop：

```vue
<TopNav
  :stream-phase="streamPhase"
  ...其余不变...
/>
```

`ChatMessageList` 标签新增 `streamPhase` prop：

```vue
<ChatMessageList
  :stream-phase="streamPhase"
  ...其余不变...
/>
```

`ChatMessageList.vue` 新增 prop 并透传给 `AssistantMessage`：

```vue
<!-- ChatMessageList.vue script -->
streamPhase: { type: String, default: "idle" },

<!-- ChatMessageList.vue template — AssistantMessage 行 -->
<AssistantMessage
  :stream-phase="streamPhase"
  ...其余不变...
/>
```

### Step 6: 更新 useChat 测试

新增测试：

```javascript
it("switches streamPhase from thinking to generating when </think> is detected", async () => {
  streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
    onEvent({ event: "message", data: "<think>analyzing</think>" });
  });
  const wrapper = mountChatHarness(ref(null));

  expect(wrapper.vm.streamPhase).toBe("idle");
  await wrapper.vm.submitPrompt("Hello");

  expect(wrapper.vm.streamPhase).toBe("generating");
});

it("switches streamPhase to generating on first body chunk when model skips think", async () => {
  streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
    onEvent({ event: "message", data: "Direct answer text" });
  });
  const wrapper = mountChatHarness(ref(null));

  await wrapper.vm.submitPrompt("Hello");

  expect(wrapper.vm.streamPhase).toBe("generating");
});

it("resets streamPhase to idle on done", async () => {
  streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
    onEvent({ event: "message", data: "text" });
    onEvent({ event: "done", data: "[DONE]" });
  });
  const wrapper = mountChatHarness(ref(null));

  await wrapper.vm.submitPrompt("Hello");

  expect(wrapper.vm.streamPhase).toBe("idle");
});

it("resets streamPhase to idle on error", async () => {
  streamChatMock.mockRejectedValueOnce(new Error("Network error"));
  const wrapper = mountChatHarness(ref(null));

  await wrapper.vm.submitPrompt("Hello");

  expect(wrapper.vm.streamPhase).toBe("idle");
});

it("resets streamPhase to idle on abort", async () => {
  const deferred = createDeferred();
  streamChatMock.mockImplementationOnce(async ({ signal }) => {
    signal.addEventListener("abort", () => {
      deferred.reject(Object.assign(new DOMException("Aborted", "AbortError"), { name: "AbortError" }));
    });
    await deferred.promise;
  });
  const wrapper = mountChatHarness(ref(null));

  const submitPromise = wrapper.vm.submitPrompt("Hello");
  await nextTick();

  expect(wrapper.vm.streamPhase).toBe("thinking");

  wrapper.vm.stopGenerating();
  await submitPromise;

  expect(wrapper.vm.streamPhase).toBe("idle");
});
```

### Step 7: 跑测试确认通过

```bash
cd frontend && npm.cmd run test -- src/composables/__tests__/useChat.spec.js
```

Expected: PASS（新增 5 个 + 已有全部通过）

### Step 8: 跑全量测试确认无回归

```bash
cd frontend && npm.cmd run test
```

Expected: ALL PASS

### Step 9: 提交

```bash
cd /d "D:\高知特\int-group02-agent\int-group02-agent" && git add frontend/src/composables/useChat.js frontend/src/i18n/messages.js frontend/src/components/layout/TopNav.vue frontend/src/components/chat/AssistantMessage.vue frontend/src/components/chat/ChatMessageList.vue frontend/src/views/ChatView.vue frontend/src/composables/__tests__/useChat.spec.js && git commit -m "feat: add streamPhase enum for unified thinking/generating status"
```
