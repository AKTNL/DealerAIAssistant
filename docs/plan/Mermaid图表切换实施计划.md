# Mermaid 图表切换实施计划

> **面向代理工作器：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 为助手消息中的 Mermaid 图表提供块级 `图表 / 代码` 切换、失败降级和状态恢复，同时保持 Markdown 层纯结构输出。

**架构：** `frontend/src/utils/markdown.js` 只负责输出稳定的 Mermaid 容器、源码面板和基于源码哈希的块 ID；`frontend/src/components/chat/AssistantMessage.vue` 负责注入切换控件、维护块级视图状态、驱动 Mermaid 渲染与失败重试；`frontend/src/style.css` 和 `frontend/src/i18n/messages.js` 提供局部样式与文案。

**技术栈：** Vue 3、Markdown-It、Mermaid 11、Vitest、jsdom

---

## 文件结构

- 修改：`frontend/src/utils/markdown.js`
  - 将 Mermaid 围栏渲染为 `.mermaid-block` 容器。
  - 生成稳定的 `data-mermaid-block-id`。
- 修改：`frontend/src/utils/__tests__/markdown.spec.js`
  - 覆盖 Mermaid HTML 结构和稳定块 ID。
- 修改：`frontend/src/components/chat/AssistantMessage.vue`
  - 注入切换控件、维护本地状态映射、渲染 Mermaid、处理失败重试。
- 新增：`frontend/src/components/__tests__/AssistantMessage.spec.js`
  - 覆盖默认视图、切换、状态恢复、失败降级与重试、ARIA。
- 修改：`frontend/src/i18n/messages.js`
  - 添加 Mermaid 切换与失败提示文案。
- 修改：`frontend/src/style.css`
  - 添加 Mermaid 容器、工具栏、代码面板、错误态样式。

## 任务 1：先写失败的 Mermaid 结构测试

**文件：**
- 修改：`frontend/src/utils/__tests__/markdown.spec.js`
- 测试：`frontend/src/utils/__tests__/markdown.spec.js`

- [ ] **步骤 1：添加 Mermaid 容器结构测试**

```javascript
it("renders mermaid fences as a structured block with source and chart panels", () => {
  const html = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");

  expect(html).toContain('class="mermaid-block"');
  expect(html).toContain('class="mermaid-chart"');
  expect(html).toContain('class="mermaid-source"');
  expect(html).toContain('data-mermaid-block-id="');
});
```

- [ ] **步骤 2：添加稳定块 ID 测试**

```javascript
it("uses a stable mermaid block id for the same source", () => {
  const first = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");
  const second = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");
  const firstId = first.match(/data-mermaid-block-id="([^"]+)"/)?.[1];
  const secondId = second.match(/data-mermaid-block-id="([^"]+)"/)?.[1];

  expect(firstId).toBeTruthy();
  expect(secondId).toBe(firstId);
});
```

- [ ] **步骤 3：运行 Markdown 测试并确认红灯**

运行：

```bash
npm.cmd run test -- src/utils/__tests__/markdown.spec.js
```

预期：FAIL，因为当前 Mermaid 围栏仍只输出单个 `pre.mermaid-chart`。

## 任务 2：先写失败的组件行为测试

**文件：**
- 新增：`frontend/src/components/__tests__/AssistantMessage.spec.js`
- 测试：`frontend/src/components/__tests__/AssistantMessage.spec.js`

- [ ] **步骤 1：添加默认视图和切换测试**

```javascript
test("renders mermaid toggle controls and switches to code view", async () => {
  const wrapper = mountAssistantWithMermaid();
  await flushPromises();

  const block = wrapper.find(".mermaid-block");
  const [chartButton, codeButton] = wrapper.findAll(".mermaid-toggle-button");

  expect(block.attributes("data-view")).toBe("chart");
  expect(chartButton.attributes("aria-pressed")).toBe("true");

  await codeButton.trigger("click");

  expect(block.attributes("data-view")).toBe("code");
  expect(codeButton.attributes("aria-pressed")).toBe("true");
});
```

- [ ] **步骤 2：添加块 ID 绑定的状态恢复测试**

```javascript
test("restores the selected mermaid view after the message html rerenders", async () => {
  const wrapper = mountAssistantWithMermaid();
  await flushPromises();

  await wrapper.findAll(".mermaid-toggle-button")[1].trigger("click");
  await wrapper.setProps({
    message: buildMessage(`<p>lead</p>${renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```")}`)
  });
  await flushPromises();

  expect(wrapper.find(".mermaid-block").attributes("data-view")).toBe("code");
});
```

- [ ] **步骤 3：添加失败降级和流结束重试测试**

```javascript
test("shows a localized fallback and retries when the stream finishes", async () => {
  mermaidRenderMock
    .mockRejectedValueOnce(new Error("bad syntax"))
    .mockResolvedValueOnce({ svg: "<svg><text>ok</text></svg>" });

  const wrapper = mountAssistantWithMermaid({ rendered: false });
  await flushPromises();

  expect(wrapper.text()).toContain("图表渲染失败");

  await wrapper.setProps({
    message: buildMessage(renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```"), { rendered: true })
  });
  await flushPromises();

  expect(wrapper.find(".mermaid-chart svg").exists()).toBe(true);
});
```

- [ ] **步骤 4：运行组件测试并确认红灯**

运行：

```bash
npm.cmd run test -- src/components/__tests__/AssistantMessage.spec.js
```

预期：FAIL，因为当前 `AssistantMessage.vue` 尚未注入切换控件，也没有块级状态恢复或重试逻辑。

## 任务 3：实现 Mermaid 结构、交互和局部样式

**文件：**
- 修改：`frontend/src/utils/markdown.js`
- 修改：`frontend/src/components/chat/AssistantMessage.vue`
- 修改：`frontend/src/i18n/messages.js`
- 修改：`frontend/src/style.css`
- 测试：`frontend/src/utils/__tests__/markdown.spec.js`
- 测试：`frontend/src/components/__tests__/AssistantMessage.spec.js`

- [ ] **步骤 1：在 Markdown 工具中输出结构化 Mermaid 容器**

```javascript
if (langName === "mermaid") {
  const source = normalizeMermaidSource(token.content);
  const blockId = createMermaidBlockId(source);
  const escapedSource = escapeHtml(source);

  return `
    <div class="mermaid-block" data-mermaid-block-id="${blockId}" data-view="chart">
      <pre class="mermaid-chart" data-mermaid-role="chart">${escapedSource}</pre>
      <pre class="mermaid-source" data-mermaid-role="source"><code class="language-mermaid">${escapedSource}</code></pre>
    </div>
  `;
}
```

- [ ] **步骤 2：在助手消息组件中实现注入、切换和渲染**

```javascript
const mermaidViewState = new Map();
const retryTimers = new Map();

function setMermaidView(blockId, view) {
  mermaidViewState.set(blockId, view);
}

function applyMermaidBlockState(block) {
  const blockId = block.dataset.mermaidBlockId;
  const view = mermaidViewState.get(blockId) ?? "chart";
  block.dataset.view = view;
  syncToggleButtons(block, view);
}

async function renderMermaidBlock(block) {
  const sourcePanel = block.querySelector(".mermaid-source");
  const chartPanel = block.querySelector(".mermaid-chart");
  const source = sourcePanel?.textContent ?? "";

  try {
    const { svg } = await mermaid.render(`mermaid-${block.dataset.mermaidBlockId}`, source);
    chartPanel.innerHTML = svg;
    chartPanel.classList.add("mermaid-rendered");
    block.dataset.renderState = "ready";
  } catch (error) {
    block.dataset.renderState = "retry-pending";
    chartPanel.innerHTML = `<div class="mermaid-chart-error">${props.dictionary.mermaidRenderError}</div>`;
    scheduleMermaidRetry(block.dataset.mermaidBlockId);
  }
}
```

- [ ] **步骤 3：补充 Mermaid 本地化文案与样式**

```javascript
zh: {
  mermaidViewChart: "图表",
  mermaidViewCode: "代码",
  mermaidRenderError: "图表渲染失败，请检查语法或切换到代码查看源码"
},
en: {
  mermaidViewChart: "Chart",
  mermaidViewCode: "Code",
  mermaidRenderError: "Unable to render this chart. Check the syntax or switch to code view."
}
```

```css
.mermaid-block {
  margin: 12px 0;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: var(--surface-soft);
}

.mermaid-toolbar-button[aria-pressed="true"] {
  background: var(--surface);
  color: var(--text-main);
  border-color: var(--border-strong);
}
```

- [ ] **步骤 4：运行针对性前端测试并确认转绿**

运行：

```bash
npm.cmd run test -- src/utils/__tests__/markdown.spec.js src/components/__tests__/AssistantMessage.spec.js src/components/__tests__/MessageShell.spec.js
```

预期：PASS，包括新的 Mermaid 结构与组件行为测试。

## 任务 4：做最终回归验证

**文件：**
- 测试：`frontend/src/utils/__tests__/markdown.spec.js`
- 测试：`frontend/src/components/__tests__/AssistantMessage.spec.js`
- 测试：`frontend/src/components/__tests__/MessageShell.spec.js`
- 测试：`frontend/src/components/__tests__/WorkspaceChrome.spec.js`

- [ ] **步骤 1：运行触及组件的完整前端回归集**

运行：

```bash
npm.cmd run test -- src/utils/__tests__/markdown.spec.js src/components/__tests__/AssistantMessage.spec.js src/components/__tests__/MessageShell.spec.js src/components/__tests__/WorkspaceChrome.spec.js
```

预期：PASS，确认 Mermaid 局部改动未破坏消息壳层与工作区钩子。

- [ ] **步骤 2：记录仍需手动验证的项**

```text
1. 流式输出中切到代码视图后，后续增量到达时是否保持当前视图
2. Mermaid 语法错误时是否只影响当前块
3. 键盘操作和 aria-pressed 是否符合预期
4. 代码面板是否可直接选择复制
```
