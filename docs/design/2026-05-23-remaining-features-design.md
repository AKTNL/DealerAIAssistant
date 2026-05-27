# 待完成功能设计

> 来源：`docs/待完成.md`（2026-05-23）
> 范围：F34 / F16+F17 / F23+F38

---

## 一、F34 — 输入框自适应高度

### 方案

双层策略：CSS `field-sizing: content` 为主，JS `scrollHeight` 降级，纯 CSS `@supports` 隔离，JS 无分支。

### CSS（style.css）

```css
.composer-input {
  max-height: 200px;
  overflow-y: auto;
}

@supports (field-sizing: content) {
  .composer-input {
    field-sizing: content;
    height: auto !important; /* 覆盖 JS 赋值，CSS 全权托管 */
  }
}
```

### JS（ChatInput.vue handleInput）

```javascript
function handleInput(event) {
  emit("update:modelValue", event.target.value);
  autoResize(event.target);
}

function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = el.scrollHeight + 'px';
}
```

- 支持 `field-sizing` 的浏览器：`height: auto !important` 覆盖 JS 赋值，CSS 托管
- 不支持的浏览器：JS `scrollHeight` 驱动，`max-height: 200px` + `overflow-y: auto` 兜底

### 不变

- `rows="1"` 保留，作为无 JS/CSS 时的 fallback
- `maxlength="500"` 不变
- `char-counter` 位置不变

---

## 二、F16 + F17 — 欢迎语 & 清空提示

### 思路

用虚拟消息替代布尔标记。`messages` 数组的第一条始终是 system 消息（`welcome` 或 `system-clear`），`ChatMessageList` 按 `role` 分支渲染。

### useChat.js 改动

```javascript
// 初始化：首条消息为 welcome
const messages = ref([createSystemMessage('welcome')]);

// 清空后：重建 messages，首条为 system-clear
function resetChatSession({ keepSessionId = false } = {}) {
  currentAbortController.value?.abort();
  flushAssistantRender();
  currentAbortController.value = null;
  isSending.value = false;
  hasUnreadContent.value = false;
  isPinnedToBottom.value = true;
  requestError.value = "";
  // 清空后插入 system-clear 虚拟消息
  messages.value = [createSystemMessage('system-clear')];

  if (!keepSessionId) {
    sessionId.value = createSessionId();
  }

  syncStatus();
}

function createSystemMessage(type) {
  return {
    id: `${type}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    role: 'system',
    type, // 'welcome' | 'system-clear'
    timestamp: Date.now()
  };
}
```

### ChatMessageList.vue 改动

`v-for` 中新增 `role === 'system'` 分支：

```vue
<SystemMessage
  v-if="message.role === 'system'"
  :type="message.type"
  :dictionary="dictionary"
/>
```

### SystemMessage.vue（新建）

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

### 删除

`ChatMessageList.vue` 中 `v-if="!hasMessages"` 的 empty-state 分支——welcome 消息始终在 `messages[0]`，无需额外判断。

### 安全确认

- **后端不接触**：`submitPrompt` 只发送 `{ sessionId, message }` 给后端，不发送 `messages` 数组。`role: 'system'` 消息永远停留在前端 UI 层
- **sessionId 刷新**：`resetChatSession` 已执行 `sessionId.value = createSessionId()`

### F42 自然解决

`SystemMessage.vue` 接收 `dictionary` prop，语言切换时响应式更新，无需额外逻辑。

---

## 三、F23 + F38 — 流式阶段状态统一

### 枚举定义（useChat.js）

```javascript
const STREAM_PHASE = { IDLE: 'idle', THINKING: 'thinking', GENERATING: 'generating' };
const streamPhase = ref(STREAM_PHASE.IDLE);
```

### 状态变迁（submitPrompt 内 onEvent 回调）

```
请求发出  → THINKING
检测到 </think> 标签闭合 → GENERATING
done / error / AbortError → IDLE
```

```javascript
// 请求发出
streamPhase.value = STREAM_PHASE.THINKING;

onEvent: ({ event, data }) => {
  const eventText = normalizeEventText(data);

  if (event === "progress") {
    // ... 现有逻辑不变 ...
    return;
  }

  if (event === "message") {
    // 检测思考→生成阶段切迁
    if (streamPhase.value === STREAM_PHASE.THINKING) {
      // 情况 A：think 标签正常闭合
      if (eventText.includes('</think>')) {
        streamPhase.value = STREAM_PHASE.GENERATING;
      }
      // 情况 B：模型直出正文，无思考块
      else if (!eventText.includes('<think>') && eventText.trim().length > 0) {
        streamPhase.value = STREAM_PHASE.GENERATING;
      }
    }

    // ... 现有 think / content 处理逻辑不变 ...
    return;
  }

  if (event === "done" || eventText === "[DONE]") {
    streamPhase.value = STREAM_PHASE.IDLE;
    // ... 现有逻辑不变 ...
  }

  if (event === "error") {
    streamPhase.value = STREAM_PHASE.IDLE;
    // ... 现有逻辑不变 ...
  }
}

// catch
catch (error) {
  streamPhase.value = STREAM_PHASE.IDLE;
  // ... 现有逻辑不变 ...
}

// finally
finally {
  streamPhase.value = STREAM_PHASE.IDLE;
  // ... 现有逻辑不变 ...
}
```

### 消费方 1：F38 顶部导航状态文字

TopNav 接收 `streamPhase` prop：

| streamPhase | 中文 | English |
|---|---|---|
| `thinking` | 正在思考… | Thinking… |
| `generating` | 正在生成… | Generating… |
| `idle` | 系统已连接，等待新问题 | System connected and ready |

### 消费方 2：F23 气泡内"思考中"指示器

`AssistantMessage.vue` 接收 `streamPhase` prop：

```vue
<!-- 思考阶段：显示 loading -->
<div v-if="message.streaming && streamPhase === 'thinking'" class="thinking-indicator">
  <span class="thinking-dot"></span>
  {{ dictionary.bubbleThinking }}
</div>
```

不依赖 `message.thinking` 是否为空——只要整体处于思考阶段，气泡内持续展示 loading，`streamPhase` 变为 `generating` 时切换为正文打字。

### i18n 新增 key

```javascript
// zh
streamPhaseThinking: "正在思考…",
streamPhaseGenerating: "正在生成…",
bubbleThinking: "思考中…",

// en
streamPhaseThinking: "Thinking…",
streamPhaseGenerating: "Generating…",
bubbleThinking: "Thinking…",
```

已有 `statusThinking`（"助手正在整理回复"）和 `statusReady`（"系统已连接…"）保持不变，作为 idle 状态和旧 `syncStatus()` 的兜底文案。新增 key 仅供 `streamPhase` 驱动。`syncStatus()` 中 `isSending` 分支不改动——当 `streamPhase` 非 IDLE 时 TopNav 优先读 stream 文案，`statusMessage` 不被消费。

### useChat 暴露

```javascript
return {
  // ... 现有 ...
  streamPhase
};
```

---

## 四、影响范围汇总

| 文件 | 改动 |
|------|------|
| `frontend/src/components/chat/ChatInput.vue` | `handleInput` 中加 `autoResize()`；新增 `autoResize` 函数 |
| `frontend/src/style.css` | `.composer-input` 加 `max-height`/`overflow-y`；新增 `@supports` 块 |
| `frontend/src/composables/useChat.js` | 初始化 messages 含 welcome；`resetChatSession` 插入 system-clear；新增 `createSystemMessage`/`streamPhase`；onEvent 中检测 `</think>` |
| `frontend/src/components/chat/SystemMessage.vue` | **新建** |
| `frontend/src/components/chat/ChatMessageList.vue` | `v-for` 加 `role === 'system'` 分支；删除旧的 empty-state |
| `frontend/src/components/chat/AssistantMessage.vue` | 新增 `streamPhase` prop + thinking-indicator DOM |
| `frontend/src/components/layout/TopNav.vue` | 新增 `streamPhase` prop，状态文字三态切换 |
| `frontend/src/views/ChatView.vue` | 透传 `streamPhase` 给 ChatInput / ChatMessageList / TopNav |
| `frontend/src/i18n/messages.js` | 新增 `statusGenerating`/`bubbleThinking`；修改 `statusThinking` 值 |

### 不变

- 后端零改动
- `useSseParser.js` 零改动
- `normalizeAssistantPayload` 零改动
- SSE 协议不新增字段

---

## 五、风险

- `</think>` 检测依赖后端模型确实输出该闭合标签。若模型不输出 `<think>`（例如直接出正文），由情况 B 的 `!eventText.includes('<think>') && eventText.trim().length > 0` 兜底，第一个有效正文 chunk 到达时立即切为 GENERATING
- `field-sizing: content` 在 Firefox 中不支持，自动走 JS 降级路径
- 若用户快速连续清空会话，`createSystemMessage` 生成的 id 可能碰撞（同毫秒），建议加随机后缀
