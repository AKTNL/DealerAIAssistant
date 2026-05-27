# Thinking Timeline — 思考过程可观测性优化设计

**日期:** 2026-05-25
**状态:** 设计中 → 待实现

---

## 背景

当前系统已有基础的 thinking 展示能力：后端通过 SSE 在回复前发送 `<think>...</think>` 块，前端解析并支持展开/收起。但 thinking 内容是预生成的模板文本（如 "1. 识别当前分析主题 2. 确认分析范围..."），而非真实的运行时数据。

用户需要两个层面的可观测性：
1. 后端规则引擎的真实工具调用链和执行细节（完整可审计级别）
2. 外部模型的真实推理过程（Prompt 引导 + 模型原生 thinking 增强）

---

## 设计目标

- 后端工具调用链展示到"完整可审计级别"：具体参数、数据源（Excel 文件名/Sheet）、匹配行数、过滤条件、计算公式与中间结果
- 模型推理通过 Prompt 改造让所有模型都能在 `<think>` 输出推理，原生 thinking 能力作为增强
- 前端用一个统一的时间线（Timeline）面板按顺序串联后端步骤和模型推理
- 不同类型步骤差异化渲染：`model_thought` 默认折叠，`insight` 高亮，`data_load/filter/calculation/tool_call` 默认折叠（可展开审计 meta）

---

## SSE 协议扩展

### 新增 `step` 事件

在现有 `message` / `progress` / `error` / `done` 基础上，新增 `event: step`。

### 数据结构

```jsonc
// 成功步骤
{
  "trace_id": "run_9a8b7c6d",
  "seq": 1,
  "type": "data_load",
  "ts": 1716650000000,
  "status": "success",
  "label": "加载目标数据",
  "detail": "从 Target 表加载全部目标数据 — 共 248 条记录",
  "meta": {
    "source_type": "excel",
    "file_name": "performance_2026.xlsx",
    "sheet": "Target",
    "recordCount": 248
  }
}

// 异常步骤
{
  "trace_id": "run_9a8b7c6d",
  "seq": 3,
  "type": "calculation",
  "ts": 1716650000200,
  "status": "failed",
  "label": "计算达成率",
  "detail": "计算 D002 达成率失败：分母（目标值）不能为零",
  "meta": {
    "formula": "wonCount / targetValue",
    "dealer": "D002",
    "inputs": {"wonCount": 12, "targetValue": 0},
    "error_message": "Division by zero: targetValue is 0"
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `trace_id` | string | 是 | 全局运行标识，区分并发请求 |
| `seq` | int | 是 | 步骤序号，递增 |
| `type` | string | 是 | 步骤类型，见枚举 |
| `ts` | long | 是 | Unix 毫秒时间戳 |
| `status` | string | 是 | `success` / `failed` / `skipped` |
| `label` | string | 是 | 简短步骤标题 |
| `detail` | string | 是 | 步骤详情，支持 Markdown |
| `meta` | object | 否 | 结构化审计数据（参数、公式、行列、错误上下文等） |

### StepType 枚举

| type | 用途 | 触发时机 |
|------|------|---------|
| `data_load` | 从数据源加载原始数据 | Repository 查询后 |
| `filter` | 按条件过滤数据 | 流过滤操作后 |
| `calculation` | 执行计算/公式 | 每次关键计算后 |
| `tool_call` | 通用工具调用 | 任何工具函数调用 |
| `model_thought` | 模型推理过程 | 模型开始输出推理/思维链时创建条目，流式持续追加 detail |
| `insight` | 后端分析引擎确定结论 | 生成结论/建议时 |

---

## 后端实现

### 新增文件

**`StepEvent.java`** — step 事件记录

```java
public record StepEvent(
    String traceId,
    int seq,
    StepType type,
    long ts,
    String status,
    String label,
    String detail,
    Map<String, Object> meta
) {}
```

**`StepType.java`** — 步骤类型枚举

```java
public enum StepType {
    data_load,
    filter,
    calculation,
    tool_call,
    model_thought,
    insight
}
```

### 改造 `RuleBasedAnalyticsService.plan()`

**当前签名：**
```java
public AnalyticsPlan plan(String message, String language)
```

**改造后签名：**
```java
public AnalyticsPlan plan(String message, String language, String traceId, Consumer<StepEvent> onStep)
```

内部各分析方法（`analyzeTargetAchievement` 等）在每完成一步计算后，通过 `onStep` 回调发送 `StepEvent`。现有的 `CalcStep` 和 `traceSteps` 机制保留，`onStep` 回调在构建 `CalcStep` 的同时触发。

#### 线程安全约束

`BufferedWriter` 不是线程安全的，所有 `writeEvent()` 调用必须串行化。约束如下：

- `RuleBasedAnalyticsService` 内部不使用并行流，所有 `onStep.accept()` 调用在同一个调用线程上顺序执行
- 如果在后续迭代中引入异步数据加载，`onStep` 回调的调用方需要通过 `synchronized` 块或 `ReentrantLock` 保护 `writeEvent` 的调用
- `ChatService` 在包装 `onStep` 回调时内部加锁，确保即使未来 `RuleBasedAnalyticsService` 内部并行化，SSE 写入也是安全的：

```java
final Object writeLock = new Object();
Consumer<StepEvent> onStep = step -> {
    synchronized (writeLock) {
        writeStepEvent(writer, step);
    }
};
```

### 改造 `ChatService.streamChat()`

1. 在方法入口生成 `traceId`（UUID 前 8 位短码）
2. 调用 `analyticsService.plan()` 时传入 `onStep` 回调，回调中（加锁后）将 `StepEvent` 序列化为 JSON 通过 `writeEvent(writer, "step", json)` 发送
3. 对于 `progress` 事件：保留现有逻辑，前端用其创建占位 step
4. **模型推理统一抹平层**：不同模型输出推理的方式不同（DeepSeek-R1 等通过 `reasoning_content` 字段，其他模型通过 `content` 中的 `<think>` 标签）。后端在消费模型流输出时统一抹平——无论上游格式如何，都将推理内容包裹为 `<think>...</think>` 文本块，通过 `message` 事件发送。前端只需要解析统一的 `<think>` 标签格式

```java
// ChatService 中模型推理抹平逻辑
private void writeModelChunk(BufferedWriter writer, ChatResponse chunk) {
    String reasoning = extractReasoningContent(chunk);  // API 原生 reasoning_content
    String content = extractContent(chunk);              // 普通 content

    if (hasText(reasoning)) {
        // 统一包裹为 <think> 标签发给前端
        writeChunkedEvent(writer, "message", "<think>" + reasoning + "</think>");
    }
    if (hasText(content)) {
        writeChunkedEvent(writer, "message", content);
    }
}
```

### Prompt 改造

**`system-prompt-zh.txt` / `system-prompt-en.txt`** 中：
- 删除 "不要暴露隐藏推理过程、内部工具名、接口名或实现细节"
- 末尾追加 `<thinking_protocol>` 规则：

```
<thinking_protocol>
在生成最终回复前，你必须先在 <think>...</think> 标签内交代你的分析思路：

1. 解读用户问题的核心意图
2. 列出你需要确认的关键数据维度（时间、门店、指标等）
3. 参考事实中提供了哪些数据？它们的口径和范围是什么？
4. 你打算如何组织回答结构？每个章节的要点是什么？
5. 有没有数据缺口或需要注意的假设？

<think> 内容应该是一段通顺的、可用 Markdown 的自然语言推理，不要写成 JSON 或代码。
<think> 标签属于内部推理，不要在 <think> 和最终回复之间重复相同内容。
最终回复紧跟 </think> 之后，按规定的 6 段结构输出。
</thinking_protocol>
```

---

## 前端实现

### 数据模型

消息对象新增/替换字段：

```javascript
// 替换旧的 thinking / thinkingHtml / thinkingExpanded
steps: [
  {
    traceId: "run_9a8b7c6d",
    seq: 1,
    type: "data_load",
    status: "success",  // "loading" | "success" | "failed" | "skipped"
    label: "加载目标数据",
    detail: "从 Target 表加载全部目标数据 — 共 248 条记录",
    meta: { source_type: "excel", sheet: "Target", recordCount: 248 },
    ts: 1716650000000
  }
]
```

### `useChat.js` 改造

1. **新增 `step` 事件处理**：SSE 收到 `event: step` → 解析 JSON，push 到当前 assistant 消息的 `steps` 数组
2. **Progress 占位替换**（改进算法）：
   - 收到 `progress` 事件时，push 一个 `status: "loading"` 的占位 step，可选携带 `stage` 字段（如 `"data_load"`）标识它将来的匹配类型
   - 当正式的 `step` 事件到达时，按以下优先级匹配：
     1. 如果 step 的 `type` 匹配某个 loading 占位的 `stage`，替换该占位
     2. 否则，**清除数组中所有 `status === "loading"` 的占位**，然后 push 正式 step
   - 这样可以防止多个连续 progress 导致的幽灵 loading（旧占位永远转圈）
3. **`<think>` 标签解析改为写入 steps**：不再写 `message.thinking`，而是在 `steps` 中创建 `type: "model_thought"` 的条目并流式追加 `detail`
   - **性能优化**：流式追加 `detail` 时直接修改字符串（浅层属性），避免触发深度响应式遍历；生成期间渲染纯文本，`done` 后再跑一次 Markdown 渲染
4. **清理旧字段**：删除 `thinking`、`thinkingHtml`、`thinkingExpanded` 相关逻辑
5. **Done 处理**：所有 loading 步骤归位（`status` 从 `"loading"` 改为 `"success"`），`model_thought` 折叠

### `AssistantMessage.vue` 改造

替换 `thinking-panel` 为 `timeline-panel`：

```
timeline-panel
├── timeline-step (data_load)    — 默认折叠，点击展开 meta 审计数据
├── timeline-step (filter)       — 默认折叠
├── timeline-step (calculation)  — 默认折叠
├── timeline-step (tool_call)    — 默认折叠
├── timeline-step (model_thought)— 生成中默认展开(打字机效果)，done 后自动折叠
└── timeline-step (insight)      — 默认展开高亮
```

各 step 类型差异化渲染：

- **`model_thought`**：`<details :open="message.streaming">` — 生成时展开，完成后折叠
- **`insight`**：`<blockquote class="timeline-step-insight">` — 高亮
- **其余类型**：`<details>` 嵌套 meta JSON — 点击展开审计数据

### `style.css` 改造

- 新增 `.timeline-panel`、`.timeline-step`、`.timeline-step-icon`、`.timeline-step-insight` 等样式
- 删除不再使用的 `.thinking-panel`、`.thinking-toggle`、`.thinking-dots` 等样式

### 全流程时序

```
用户: "北京 6 月目标达成情况怎么样？"

SSE 流:
event: progress  →  占位 step: "正在识别分析主题" (loading)
event: step      →  覆盖占位: "识别分析主题 → 目标达成" (success)
event: progress  →  占位 step: "正在加载目标数据" (loading)
event: step      →  "加载 Target 表 → 248 条记录" (success)
event: step      →  "过滤: city=北京, month=2024-06 → 匹配 24 条" (success)
event: step      →  "计算: D001 达成率 = 45/50 = 90.0%" (success)
event: step      →  "计算: D005 达成率 = 34/50 = 68.0%" (success)
event: step      →  insight: "D005 为红色预警门店，目标缺口 16 台" (success, 高亮)
event: progress  →  占位 step: "正在调用模型生成报告" (loading)
event: step      →  model_thought: "从数据来看，D005 的达成率仅 68%..." (loading → 流式打字)
event: step      →  model_thought status → success
event: message   →  "## 核心结论\n- D005 达成率最低，仅 68%..."
...
event: done      →  所有 loading 归位，model_thought 折叠
```

---

## 改动文件清单

### 后端

| 文件 | 改动 |
|------|------|
| `backend/src/main/java/.../service/StepEvent.java` | **新增** — step 事件 record |
| `backend/src/main/java/.../service/StepType.java` | **新增** — 步骤类型枚举 |
| `backend/src/main/java/.../service/ChatService.java` | 修改 `streamChat()`，生成 traceId，传入 onStep 回调，新增 `writeStepEvent()` |
| `backend/src/main/java/.../service/RuleBasedAnalyticsService.java` | 修改 `plan()` 签名加 `Consumer<StepEvent>`，内部各分析方法按步骤回调 |
| `backend/src/main/java/.../ai/PromptFactory.java` | 修改 `buildSystemPrompt()` 追加 `<thinking_protocol>` 规则 |
| `backend/src/main/resources/prompts/system-prompt-zh.txt` | 删除 "不要暴露隐藏推理过程"，追加 `<thinking_protocol>` |
| `backend/src/main/resources/prompts/system-prompt-en.txt` | 同上 |

### 前端

| 文件 | 改动 |
|------|------|
| `frontend/src/composables/useChat.js` | 新增 step 事件处理、progress 占位、model_thought 写入 steps、清理旧 thinking 字段 |
| `frontend/src/components/chat/AssistantMessage.vue` | 替换 thinking-panel → timeline-panel，按 step type 差异化渲染 |
| `frontend/src/style.css` | 新增 timeline 样式，删除废弃 thinking 样式 |

### 测试

| 文件 | 改动 |
|------|------|
| `frontend/src/composables/__tests__/useChat.spec.js` | 新增 step 事件处理测试、progress 占位替换测试 |
| `frontend/src/components/__tests__/AssistantMessage.spec.js` | 更新为 timeline 渲染测试 |
| `backend/src/test/.../RuleBasedAnalyticsServiceTest.java` | 更新 `plan()` 调用，验证 step 回调 |
| `backend/src/test/.../ModelConfigServiceTest.java` | 如有涉及，同步更新 |

---

## 风险与边界

- **并发请求**：通过 `trace_id` 区分，前端根据当前 assistant 消息的 `traceId` 过滤 step 事件；用户连续发送两条消息时，第一条的 step 不会污染第二条
- **线程安全**：`BufferedWriter` 非线程安全，`onStep` 回调在 `ChatService` 层通过 `synchronized` 锁保护，确保 SSE 写入串行化
- **模型推理格式差异**：后端做统一抹平——`reasoning_content` 字段和 `<think>` 标签内容均包裹为 `<think>...</think>` 文本块发给前端，前端只需处理一种格式
- **Progress 幽灵 loading**：通过 "清除所有 loading 占位" 策略防止连续 progress 导致的永久转圈
- **流式渲染性能**：`model_thought` 流式追加时只修改浅层属性，生成期间渲染纯文本避免频繁 Markdown 解析
- **向后兼容**：`step` 是新增事件类型，不加不会破坏现有流程；旧客户端收到 `step` 事件会静默忽略
- **Prompt 兼容性**：`<thinking_protocol>` 规则对不支持原生 thinking 的模型同样有效（`<think>` 标签是纯文本标记，不依赖模型能力）
- **模型忽略 prompt 指令**：如果模型不在 `<think>` 里输出推理（直接输出回复），前端需要容错——没有 model_thought step 就直接显示消息内容
