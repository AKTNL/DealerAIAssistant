import { flushPromises, mount } from "@vue/test-utils";
import { beforeAll, beforeEach, describe, expect, test, vi } from "vitest";

const { mermaidInitializeMock, mermaidRenderMock } = vi.hoisted(() => ({
  mermaidInitializeMock: vi.fn(),
  mermaidRenderMock: vi.fn()
}));

const { echartsInitMock, echartsUseMock } = vi.hoisted(() => ({
  echartsInitMock: vi.fn(() => ({
    dispose: vi.fn(),
    resize: vi.fn(),
    setOption: vi.fn()
  })),
  echartsUseMock: vi.fn()
}));

vi.mock("mermaid", () => ({
  default: {
    initialize: (...args) => mermaidInitializeMock(...args),
    render: (...args) => mermaidRenderMock(...args)
  }
}));

vi.mock("echarts/charts", () => ({
  BarChart: {},
  HeatmapChart: {},
  PieChart: {},
  RadarChart: {}
}));

vi.mock("echarts/components", () => ({
  GridComponent: {},
  LegendComponent: {},
  TitleComponent: {},
  TooltipComponent: {},
  VisualMapComponent: {}
}));

vi.mock("echarts/core", () => ({
  init: echartsInitMock,
  use: echartsUseMock
}));

vi.mock("echarts/renderers", () => ({
  CanvasRenderer: {}
}));

import { renderMarkdownLite } from "../../utils/markdown";

let AssistantMessage;

const dictionary = {
  assistantLabel: "AI 助手",
  hideThinking: "收起思考过程",
  mermaidRenderError: "图表渲染失败，请检查语法或切换到代码查看源码",
  mermaidReload: "重新加载",
  mermaidViewChart: "图表",
  mermaidViewCode: "代码",
  showThinking: "展开思考过程"
};

Object.assign(dictionary, {
  chartEmptyTitle: "暂无可视化信号",
  chartEmptyBody: "当前数据不足以支持可靠的排名图表，因此已隐藏可视化。",
  chartEmptyNoDataBody: "当前范围没有匹配记录。",
  chartEmptyDenominatorZeroBody: "该指标分母为 0，暂不展示图表。",
  chartEmptyAllZeroBody: "当前数据的关键指标均为 0，暂不展示图表。",
  chartEmptyInsufficientSampleBody: "样本量不足以支持可靠排名，暂不展示图表。"
});

function buildMermaidHtml(prefix = "") {
  return renderMarkdownLite(`${prefix}\n\`\`\`mermaid\ngraph TD\nA-->B\n\`\`\``.trim());
}

function buildDuplicateMermaidHtml() {
  return renderMarkdownLite(`\`\`\`mermaid
graph TD
A-->B
\`\`\`

\`\`\`mermaid
graph TD
A-->B
\`\`\``);
}

function buildMessage(html, overrides = {}) {
  return {
    followUps: [],
    html,
    rendered: true,
    streaming: false,
    ...overrides
  };
}

function mountAssistant(message = buildMessage(buildMermaidHtml())) {
  return mount(AssistantMessage, {
    attachTo: document.body,
    props: {
      dictionary,
      locale: "zh",
      message
    },
    global: {
      stubs: {
        FollowUpButtons: {
          template: "<div />"
        }
      }
    }
  });
}

async function flushMermaid() {
  for (let index = 0; index < 4; index += 1) {
    await flushPromises();
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  }
}

async function waitForElement(wrapper, selector) {
  for (let index = 0; index < 10; index += 1) {
    await flushPromises();
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });

    const element = wrapper.element.querySelector(selector);
    if (element) {
      return element;
    }
  }

  return null;
}

beforeEach(() => {
  echartsInitMock.mockClear();
  echartsUseMock.mockClear();
  mermaidRenderMock.mockReset();
  mermaidRenderMock.mockResolvedValue({ svg: "<svg><text>ok</text></svg>" });
  global.ResizeObserver = vi.fn(() => ({
    disconnect: vi.fn(),
    observe: vi.fn()
  }));
});

beforeAll(async () => {
  ({ default: AssistantMessage } = await import("../chat/AssistantMessage.vue"));
});

describe("AssistantMessage mermaid blocks", () => {
  test("initializes mermaid with the blue-gray chart theme", () => {
    mountAssistant();

    expect(mermaidInitializeMock).toHaveBeenCalledWith({
      startOnLoad: false,
      theme: "base",
      themeVariables: {
        primaryColor: "#e9f2fb",
        primaryTextColor: "#102033",
        primaryBorderColor: "#c9d6e3",
        lineColor: "#aebfce",
        secondaryColor: "#ffffff",
        tertiaryColor: "#a43f3f",
        fontSize: "13px"
      }
    });
  });

  test("renders mermaid toggle controls and switches to code view", async () => {
    const wrapper = mountAssistant();
    await flushPromises();

    const block = wrapper.find(".mermaid-block");
    const buttons = wrapper.findAll(".mermaid-toggle-button");

    expect(block.exists()).toBe(true);
    expect(buttons).toHaveLength(2);
    expect(block.attributes("data-view")).toBe("chart");
    expect(buttons[0].attributes("aria-pressed")).toBe("true");
    expect(buttons[1].attributes("aria-pressed")).toBe("false");

    await buttons[1].trigger("click");

    expect(block.attributes("data-view")).toBe("code");
    expect(buttons[0].attributes("aria-pressed")).toBe("false");
    expect(buttons[1].attributes("aria-pressed")).toBe("true");
  });

  test("restores the selected mermaid view after the message html rerenders", async () => {
    const wrapper = mountAssistant();
    await flushPromises();

    await wrapper.findAll(".mermaid-toggle-button")[1].trigger("click");
    await wrapper.setProps({
      message: buildMessage(buildMermaidHtml("Lead"))
    });
    await flushPromises();

    expect(wrapper.find(".mermaid-block").attributes("data-view")).toBe("code");
  });

  test("shows a skeleton while a mermaid chart is still rendering", async () => {
    let resolveRender;
    mermaidRenderMock.mockImplementationOnce(() => new Promise((resolve) => {
      resolveRender = resolve;
    }));

    const wrapper = mountAssistant();
    await flushPromises();

    expect(wrapper.find(".mermaid-skeleton").exists()).toBe(true);

    resolveRender({ svg: "<svg><text>ok</text></svg>" });
    await flushMermaid();
  });

  test("keeps a failed mermaid chart in chart view with source view available", async () => {
    mermaidRenderMock.mockRejectedValueOnce(new Error("bad syntax"));

    const wrapper = mountAssistant();
    await flushMermaid();

    const block = wrapper.find(".mermaid-block");
    const buttons = wrapper.findAll(".mermaid-toggle-button");

    expect(mermaidRenderMock).toHaveBeenCalledTimes(1);
    expect(block.attributes("data-view")).toBe("chart");
    expect(wrapper.text()).toContain(dictionary.mermaidRenderError);
    expect(buttons[0].attributes("aria-pressed")).toBe("true");
    expect(buttons[1].attributes("aria-pressed")).toBe("false");

    await buttons[1].trigger("click");

    expect(block.attributes("data-view")).toBe("code");
    expect(wrapper.find(".mermaid-source").text()).toContain("graph TD");
  });

  test("retries a failed mermaid chart when streaming finishes and keeps chart view", async () => {
    mermaidRenderMock
      .mockRejectedValueOnce(new Error("incomplete source"))
      .mockResolvedValueOnce({ svg: "<svg><text>ok</text></svg>" });

    const html = buildMermaidHtml();
    const wrapper = mountAssistant(buildMessage(html, { rendered: false, streaming: true }));
    await flushMermaid();

    expect(wrapper.find(".mermaid-block").attributes("data-view")).toBe("chart");
    expect(wrapper.text()).toContain(dictionary.mermaidRenderError);

    await wrapper.setProps({
      message: buildMessage(html, { rendered: true, streaming: false })
    });
    await flushMermaid();

    const block = wrapper.find(".mermaid-block");
    expect(block.attributes("data-view")).toBe("chart");
    expect(block.find(".mermaid-chart svg").exists()).toBe(true);
  });

  test("keeps duplicate mermaid chart view state independent", async () => {
    const wrapper = mountAssistant(buildMessage(buildDuplicateMermaidHtml()));
    await flushPromises();

    const blocks = wrapper.findAll(".mermaid-block");
    expect(blocks).toHaveLength(2);
    expect(blocks[0].attributes("data-mermaid-block-id")).not.toBe(blocks[1].attributes("data-mermaid-block-id"));

    await blocks[0].findAll(".mermaid-toggle-button")[1].trigger("click");
    await wrapper.setProps({
      message: buildMessage(buildDuplicateMermaidHtml())
    });
    await flushPromises();

    const rerenderedBlocks = wrapper.findAll(".mermaid-block");
    expect(rerenderedBlocks[0].attributes("data-view")).toBe("code");
    expect(rerenderedBlocks[1].attributes("data-view")).toBe("chart");
  });

  test("keeps matching mermaid block view state isolated across assistant message instances", async () => {
    const firstWrapper = mountAssistant(buildMessage(buildMermaidHtml()));
    const secondWrapper = mountAssistant(buildMessage(buildMermaidHtml()));
    await flushPromises();

    await firstWrapper.findAll(".mermaid-toggle-button")[1].trigger("click");

    expect(firstWrapper.find(".mermaid-block").attributes("data-view")).toBe("code");
    expect(secondWrapper.find(".mermaid-block").attributes("data-view")).toBe("chart");
  });

  test("does not retry mermaid rendering after unmount", async () => {
    vi.useFakeTimers();
    mermaidRenderMock.mockRejectedValueOnce(new Error("incomplete source"));

    const wrapper = mountAssistant(buildMessage(buildMermaidHtml(), { rendered: false, streaming: true }));
    await flushPromises();

    expect(mermaidRenderMock).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    vi.advanceTimersByTime(350);
    await flushPromises();
    vi.useRealTimers();

    expect(mermaidRenderMock).toHaveBeenCalledTimes(1);
  });

  test("fills missing chart empty copy from dictionary", async () => {
    const html = '<div class="analysis-empty-chart" role="status" data-chart-empty="true" data-chart-empty-reason="ALL_ZERO_SIGNAL"></div>';
    const wrapper = mountAssistant(buildMessage(html));

    await flushPromises();

    expect(wrapper.find(".analysis-empty-chart-title").text()).toBe(dictionary.chartEmptyTitle);
    expect(wrapper.find(".analysis-empty-chart-body").text()).toBe(dictionary.chartEmptyAllZeroBody);
  });
});

describe("AssistantMessage timeline panel", () => {
  test("renders retained progress steps in a localized processing progress section", async () => {
    const steps = [
      { seq: 1, type: "progress", status: "success", label: "正在识别分析主题", ts: 1000 },
      { seq: 2, type: "progress", status: "success", label: "正在调用外部模型生成经营分析报告", ts: 1001 },
      { seq: 3, type: "progress", status: "success", label: "模型生成完成，正在校验数据一致性", ts: 1002 }
    ];

    const wrapper = mountAssistant(buildMessage("<p>response</p>", {
      id: "assistant-progress",
      steps,
      streaming: false
    }));

    expect(wrapper.find(".processing-progress").exists()).toBe(false);
    await wrapper.find(".timeline-toggle").trigger("click");

    const progress = wrapper.find(".processing-progress");
    expect(progress.exists()).toBe(true);
    expect(progress.text()).toContain("分析进度");
    expect(progress.findAll(".processing-progress-item")).toHaveLength(3);
    expect(progress.text()).toContain("正在识别分析主题");
    expect(progress.text()).toContain("正在调用外部模型生成经营分析报告");
    expect(progress.text()).toContain("模型生成完成，正在校验数据一致性");
  });

  test("renders English processing progress title", async () => {
    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        message: buildMessage("<p>response</p>", {
          id: "assistant-progress-en",
          steps: [
            { seq: 1, type: "progress", status: "success", label: "Calling the external model", ts: 1000 }
          ],
          streaming: false
        })
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    expect(wrapper.find(".processing-progress").exists()).toBe(false);
    await wrapper.find(".timeline-toggle").trigger("click");

    expect(wrapper.find(".processing-progress").text()).toContain("Processing progress");
  });

  test("auto-collapses thought and progress when streaming finishes and reopens with the thinking toggle", async () => {
    const steps = [
      { seq: 1, type: "model_thought", status: "loading", label: "Reasoning", detail: "Checking inputs", ts: 1000 },
      { seq: 2, type: "progress", status: "loading", label: "Calling model", ts: 1001 }
    ];

    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        message: buildMessage("<p>Final answer</p>", {
          id: "assistant-auto-collapse",
          steps,
          streaming: true,
          rendered: false
        }),
        streamPhase: "thinking"
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    expect(wrapper.find(".timeline-panel").exists()).toBe(true);
    expect(wrapper.find(".processing-progress").exists()).toBe(true);

    await wrapper.setProps({
      message: buildMessage("<p>Final answer</p>", {
        id: "assistant-auto-collapse",
        steps: steps.map(step => ({ ...step, status: "success" })),
        streaming: false,
        rendered: true,
        followUps: ["What should we inspect next?"]
      }),
      streamPhase: "idle"
    });

    const toggle = wrapper.find(".timeline-toggle");
    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.find(".timeline-panel").exists()).toBe(false);
    expect(wrapper.find(".processing-progress").exists()).toBe(false);
    expect(wrapper.find(".analysis-response-body").text()).toContain("Final answer");

    await toggle.trigger("click");

    expect(wrapper.find(".timeline-panel").exists()).toBe(true);
    expect(wrapper.find(".processing-progress").exists()).toBe(true);
    expect(wrapper.find(".timeline-panel").text()).toContain("Checking inputs");
  });

  test("keeps progress-only completed messages collapsed until the thinking toggle is opened", async () => {
    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        message: buildMessage("<p>Final answer</p>", {
          id: "assistant-progress-only-collapse",
          steps: [
            { seq: 1, type: "progress", status: "success", label: "Calling model", ts: 1000 }
          ],
          streaming: false,
          rendered: true
        }),
        streamPhase: "idle"
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    const toggle = wrapper.find(".timeline-toggle");
    expect(toggle.exists()).toBe(true);
    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.find(".processing-progress").exists()).toBe(false);

    await toggle.trigger("click");

    expect(wrapper.find(".processing-progress").exists()).toBe(true);
    expect(wrapper.find(".processing-progress").text()).toContain("Calling model");
  });

  test("collapses evidence summary and processing progress with the thinking toggle", async () => {
    const steps = [
      {
        seq: 1,
        type: "data_load",
        status: "success",
        label: "Load target data",
        ts: "2026-05-26T10:00:00Z",
        meta: {
          file_name: "performance_2026.xlsx",
          recordCount: 248,
          source_type: "excel"
        }
      },
      { seq: 2, type: "progress", status: "success", label: "姝ｅ湪璋冪敤澶栭儴妯″瀷鐢熸垚缁忚惀鍒嗘瀽鎶ュ憡", ts: 1001 }
    ];

    const wrapper = mountAssistant(buildMessage("<p>response</p>", {
      id: "assistant-process-collapse",
      steps,
      streaming: true
    }));

    expect(wrapper.find(".thinking-summary").exists()).toBe(true);
    expect(wrapper.find(".processing-progress").exists()).toBe(true);

    await wrapper.find(".timeline-toggle").trigger("click");

    expect(wrapper.find(".thinking-summary").exists()).toBe(false);
    expect(wrapper.find(".processing-progress").exists()).toBe(false);

    await wrapper.find(".timeline-toggle").trigger("click");

    expect(wrapper.find(".thinking-summary").exists()).toBe(true);
    expect(wrapper.find(".processing-progress").exists()).toBe(true);
  });

  test("renders structured timeline steps when no model thought is present", () => {
    const steps = [
      { seq: 1, type: "data_load", status: "success", label: "Load sales data", ts: "2026-05-26T10:00:00Z", detail: "Loaded 1,234 rows" },
      { seq: 2, type: "filter", status: "success", label: "Filter by region", ts: "2026-05-26T10:00:01Z" },
      { seq: 3, type: "calculation", status: "loading", label: "Computing aggregates", ts: "2026-05-26T10:00:02Z" },
      { seq: 4, type: "tool_call", status: "skipped", label: "Fetch external data", ts: "2026-05-26T10:00:03Z" }
    ];

    const wrapper = mountAssistant(buildMessage("<p>response</p>", {
      id: "assistant-tl",
      steps,
      streaming: true
    }));

    const steps_el = wrapper.findAll(".timeline-step");
    expect(steps_el).toHaveLength(4);

    expect(steps_el[0].classes()).toContain("timeline-step--success");
    expect(steps_el[0].classes()).toContain("timeline-step--data_load");
    expect(steps_el[0].find(".timeline-step-check").exists()).toBe(true);
    expect(steps_el[0].find(".timeline-step-label").text()).toBe("Load sales data");
    expect(steps_el[0].find(".timeline-step-detail-text").text()).toBe("Loaded 1,234 rows");

    expect(steps_el[2].classes()).toContain("timeline-step--loading");
    expect(steps_el[2].find(".timeline-step-spinner").exists()).toBe(true);
    expect(steps_el[2].find(".badge-loading").exists()).toBe(true);

    expect(steps_el[3].classes()).toContain("timeline-step--skipped");
    expect(steps_el[3].find(".timeline-step-dash").exists()).toBe(true);
  });

  test("shows only model thought in the expanded timeline when evidence steps are already summarized", () => {
    const steps = [
      { seq: 1, type: "data_load", status: "success", label: "Load sales data", ts: "2026-05-26T10:00:00Z", detail: "Loaded 1,234 rows" },
      { seq: 2, type: "filter", status: "success", label: "Filter by region", ts: "2026-05-26T10:00:01Z" },
      { seq: 3, type: "calculation", status: "success", label: "Computing aggregates", ts: "2026-05-26T10:00:02Z" },
      { seq: 4, type: "insight", status: "success", label: "Conclusion", ts: "2026-05-26T10:00:03Z", detail: "The evidence summary already shows this." },
      { seq: 5, type: "model_thought", status: "success", label: "Reasoning", ts: "2026-05-26T10:00:04Z", detail: "The model considered multiple factors." }
    ];

    const wrapper = mountAssistant(buildMessage("<p>response</p>", {
      id: "assistant-tl-thought-only",
      steps,
      streaming: true
    }));

    const steps_el = wrapper.findAll(".timeline-step");
    expect(steps_el).toHaveLength(1);
    expect(steps_el[0].classes()).toContain("timeline-step--model_thought");
    expect(wrapper.find(".timeline-panel").text()).not.toContain("Load sales data");
    expect(wrapper.find(".timeline-panel").text()).not.toContain("The evidence summary already shows this.");

    expect(steps_el[0].find(".timeline-step-detail").exists()).toBe(true);
    expect(steps_el[0].find(".timeline-step-detail .markdown-body").text()).toBe("The model considered multiple factors.");
  });

  test("renders a compact evidence summary from step metadata", async () => {
    const steps = [
      {
        seq: 1,
        type: "data_load",
        status: "success",
        label: "Load target data",
        ts: "2026-05-26T10:00:00Z",
        meta: {
          file_name: "performance_2026.xlsx",
          sheet: "Target",
          recordCount: 248,
          source_type: "excel"
        }
      },
      {
        seq: 2,
        type: "filter",
        status: "success",
        label: "Filter by scope",
        ts: "2026-05-26T10:00:01Z",
        meta: {
          city: "Beijing",
          month: "2026-05",
          inputCount: 248,
          outputCount: 24
        }
      },
      {
        seq: 3,
        type: "calculation",
        status: "success",
        label: "Aggregate targets",
        ts: "2026-05-26T10:00:02Z",
        meta: {
          formula: "achievementRate = wonCount / targetValue",
          totalWonCount: 13,
          totalTargetValue: 20,
          sampleRows: [{ dealer: "D001" }, { dealer: "D005" }]
        }
      },
      {
        seq: 4,
        type: "insight",
        status: "success",
        label: "Analysis insight",
        detail: "D005 has the lowest achievement rate at 68.0%.",
        ts: "2026-05-26T10:00:03Z"
      }
    ];

    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        message: buildMessage("<p>reply</p>", {
          id: "assistant-evidence",
          steps,
          streaming: false
        }),
        streamPhase: "idle"
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    expect(wrapper.find(".thinking-summary").exists()).toBe(false);

    await wrapper.find(".timeline-toggle").trigger("click");

    const summary = wrapper.find(".thinking-summary");
    expect(summary.exists()).toBe(true);
    expect(summary.text()).toContain("Evidence trail");
    expect(summary.text()).toContain("Data source");
    expect(summary.text()).toContain("248 records");
    expect(summary.text()).toContain("performance_2026.xlsx");
    expect(summary.text()).toContain("Scope");
    expect(summary.text()).toContain("Beijing · 2026-05");
    expect(summary.text()).toContain("248 -> 24 matched");
    expect(summary.text()).toContain("Calculation");
    expect(summary.text()).toContain("achievementRate = wonCount / targetValue");
    expect(summary.text()).toContain("D005 has the lowest achievement rate at 68.0%.");
  });

  test("renders failed step badge with zh locale", () => {
    const steps = [
      { seq: 1, type: "data_load", status: "failed", label: "Load data", ts: "2026-05-26T10:00:00Z" }
    ];

    const wrapper = mountAssistant(buildMessage("<p>x</p>", {
      id: "assistant-fail",
      steps,
      streaming: true
    }));

    const step = wrapper.find(".timeline-step");
    expect(step.find(".timeline-step-cross").exists()).toBe(true);
    expect(wrapper.find(".badge-error").text()).toBe("失败");
  });

  test("renders model_thought details open during streaming", () => {
    const steps = [
      { seq: 1, type: "model_thought", status: "loading", label: "Thinking", ts: "2026-05-26T10:00:00Z", detail: "Analyzing..." }
    ];

    const wrapper = mountAssistant(buildMessage("<p>reply</p>", {
      id: "assistant-mt",
      steps,
      streaming: true
    }));

    const detail = wrapper.find(".timeline-step-detail");
    expect(detail.attributes("open")).toBeDefined();
    expect(detail.find("summary").exists()).toBe(true);
    expect(detail.find(".markdown-body").text()).toBe("Analyzing...");
  });

  test("renders insight step with blockquote styling", () => {
    const steps = [
      { seq: 1, type: "insight", status: "success", label: "Key Finding", ts: "2026-05-26T10:00:00Z", detail: "Revenue increased 15%" }
    ];

    const wrapper = mountAssistant(buildMessage("<p>x</p>", {
      id: "assistant-insight",
      steps,
      streaming: true
    }));

    const quote = wrapper.find(".timeline-step-insight");
    expect(quote.exists()).toBe(true);
    expect(quote.text()).toBe("Revenue increased 15%");
  });

  test("renders collapsible audit meta details", () => {
    const steps = [
      {
        seq: 1, type: "data_load", status: "success", label: "Load data",
        ts: "2026-05-26T10:00:00Z",
        meta: { rows: 1234, source: "postgres", query: "SELECT * FROM sales" }
      }
    ];

    const wrapper = mountAssistant(buildMessage("<p>x</p>", {
      id: "assistant-meta",
      steps,
      streaming: true
    }));

    const meta = wrapper.find(".timeline-step-meta");
    expect(meta.exists()).toBe(true);
    expect(meta.find("summary").text()).toBe("查看审计数据");
  });

  test("does not render audit details when meta is empty", () => {
    const steps = [
      { seq: 1, type: "data_load", status: "success", label: "Load data", ts: "2026-05-26T10:00:00Z", meta: {} }
    ];

    const wrapper = mountAssistant(buildMessage("<p>x</p>", {
      id: "assistant-empty-meta",
      steps,
      streaming: true
    }));

    expect(wrapper.find(".timeline-step-meta").exists()).toBe(false);
  });

  test("shows thinking indicator when streaming and thinking phase but no model_thought step active", () => {
    const steps = [
      { seq: 1, type: "data_load", status: "loading", label: "Loading...", ts: "2026-05-26T10:00:00Z" }
    ];

    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "zh",
        message: buildMessage("", {
          id: "assistant-indicator",
          steps,
          streaming: true
        }),
        streamPhase: "thinking"
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    expect(wrapper.find(".thinking-indicator").exists()).toBe(true);
  });

  test("hides thinking indicator when model_thought step is loading", () => {
    const steps = [
      { seq: 1, type: "model_thought", status: "loading", label: "Thinking", ts: "2026-05-26T10:00:00Z", detail: "Analyzing..." }
    ];

    const wrapper = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "zh",
        message: buildMessage("", {
          id: "assistant-no-indicator",
          steps,
          streaming: true
        }),
        streamPhase: "thinking"
      },
      global: {
        stubs: {
          FollowUpButtons: { template: "<div />" }
        }
      }
    });

    expect(wrapper.find(".thinking-indicator").exists()).toBe(false);
  });
});
describe("AssistantMessage analysis enhancements", () => {
  test("renders metric cards from current reply values and not fixed examples", async () => {
    const html = renderMarkdownLite(`## Conclusion

- Average achievement 98.4%, lowest dealer Shanghai B 76.2%, highest dealer Hangzhou A 130.0%, gap 53.8%`);
    const wrapper = mountAssistant(buildMessage(html));

    await flushPromises();

    expect(wrapper.findAll(".analysis-metric-card")).toHaveLength(4);
    expect(wrapper.text()).toContain("98.4%");
    expect(wrapper.text()).toContain("53.8%");
    expect(wrapper.text()).not.toContain("112.6%");
  });

  test("renders analysis chart panels for supported completed tables", async () => {
    const html = renderMarkdownLite(`<table>
      <thead><tr><th>Dealer</th><th>Achievement Rate</th></tr></thead>
      <tbody>
        <tr><td>Hangzhou A</td><td>130%</td></tr>
        <tr><td>Shanghai B</td><td>76%</td></tr>
      </tbody>
    </table>`);
    const wrapper = mountAssistant(buildMessage(html));

    await flushPromises();

    expect(wrapper.findComponent({ name: "AnalysisChart" }).exists()).toBe(true);
    expect(wrapper.find(".analysis-chart-panel").exists()).toBe(true);
  });

  test("skips analysis charts while message is streaming", async () => {
    const html = renderMarkdownLite(`<table>
      <thead><tr><th>Dealer</th><th>Achievement Rate</th></tr></thead>
      <tbody>
        <tr><td>Hangzhou A</td><td>130%</td></tr>
        <tr><td>Shanghai B</td><td>76%</td></tr>
      </tbody>
    </table>`);
    const wrapper = mountAssistant(buildMessage(html, { rendered: false, streaming: true }));

    await flushPromises();

    expect(wrapper.findComponent({ name: "AnalysisChart" }).exists()).toBe(false);
    expect(wrapper.find(".analysis-chart-panel").exists()).toBe(false);
  });

  test("renders chart-json fences through the lazy ECharts adapter", async () => {
    const html = renderMarkdownLite(`\`\`\`chart-json
{"type":"bar","title":"Achievement","metric":"Rate","categories":["A","B"],"values":[80,60]}
\`\`\``);
    const wrapper = mountAssistant(buildMessage(html));

    const renderedChart = await waitForElement(wrapper, ".chart-json-panel");

    expect(wrapper.find(".chart-json-block").exists()).toBe(true);
    const placeholder = wrapper.element.querySelector(".chart-json-block");
    expect(renderedChart).toBeTruthy();
    expect(echartsInitMock).toHaveBeenCalled();
    expect(placeholder.compareDocumentPosition(renderedChart) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(mermaidRenderMock).not.toHaveBeenCalled();
  });
});
