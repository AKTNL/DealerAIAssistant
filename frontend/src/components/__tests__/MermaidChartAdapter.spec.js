import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, test, vi } from "vitest";

const { disconnectMock, disposeMock, initMock, observeMock, resizeMock, setOptionMock, useMock } = vi.hoisted(() => ({
  disconnectMock: vi.fn(),
  disposeMock: vi.fn(),
  initMock: vi.fn(),
  observeMock: vi.fn(),
  resizeMock: vi.fn(),
  setOptionMock: vi.fn(),
  useMock: vi.fn()
}));

vi.mock("echarts/charts", () => ({
  BarChart: {},
  PieChart: {}
}));

vi.mock("echarts/components", () => ({
  GridComponent: {},
  LegendComponent: {},
  TitleComponent: {},
  TooltipComponent: {}
}));

vi.mock("echarts/core", () => ({
  init: initMock,
  use: useMock
}));

vi.mock("echarts/renderers", () => ({
  CanvasRenderer: {}
}));

import MermaidChartAdapter from "../chat/MermaidChartAdapter.vue";

const chartInstance = {
  dispose: disposeMock,
  resize: resizeMock,
  setOption: setOptionMock
};

function mountAdapter(model) {
  return mount(MermaidChartAdapter, {
    attachTo: document.body,
    props: {
      rawJson: typeof model === "string" ? model : JSON.stringify(model)
    }
  });
}

beforeEach(() => {
  disconnectMock.mockClear();
  disposeMock.mockClear();
  initMock.mockReset();
  observeMock.mockClear();
  resizeMock.mockClear();
  setOptionMock.mockClear();
  initMock.mockReturnValue(chartInstance);
  global.ResizeObserver = vi.fn(() => ({
    disconnect: disconnectMock,
    observe: observeMock
  }));
});

describe("MermaidChartAdapter", () => {
  test("renders bar chart-json with percentage color thresholds and average mark line", async () => {
    mountAdapter({
      type: "bar",
      title: "Achievement",
      metric: "Rate",
      metricType: "percentage",
      categories: ["A", "B", "C"],
      values: [85, 65, 45],
      averageLine: 70
    });

    await flushPromises();

    expect(initMock).toHaveBeenCalledTimes(1);
    expect(setOptionMock).toHaveBeenCalled();
    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.series[0].type).toBe("bar");
    expect(option.series[0].data.map((entry) => entry.itemStyle.color)).toEqual([
      "#2f7da8",
      "#6f879d",
      "#a43f3f"
    ]);
    expect(option.series[0].markLine.data[0].xAxis).toBe(70);
  });

  test("renders pie chart-json as a donut with label overlap protection", async () => {
    mountAdapter({
      type: "pie",
      title: "Lead Source",
      slices: [
        { name: "Web", value: 4 },
        { name: "Referral", value: 2 },
        { name: "Event", value: 1 }
      ]
    });

    await flushPromises();

    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.series[0].type).toBe("pie");
    expect(option.series[0].radius).toEqual(["45%", "70%"]);
    expect(option.series[0].avoidLabelOverlap).toBe(true);
    expect(option.series[0].data).toHaveLength(3);
  });

  test("renders empty state instead of initializing echarts for empty bar data", async () => {
    const wrapper = mountAdapter({
      type: "bar",
      title: "No Data",
      metric: "Rate",
      categories: [],
      values: [],
      emptyMessage: "No usable rows"
    });

    await flushPromises();

    expect(wrapper.find(".analysis-empty-chart").exists()).toBe(true);
    expect(wrapper.text()).toContain("No Data");
    expect(wrapper.text()).toContain("No usable rows");
    expect(initMock).not.toHaveBeenCalled();
  });

  test("falls back to a code block when chart-json parsing fails", async () => {
    const wrapper = mountAdapter("{bad json");

    await flushPromises();

    expect(wrapper.find("pre code").text()).toBe("{bad json");
    expect(initMock).not.toHaveBeenCalled();
  });

  test("cleans up resize observer and chart instance on unmount", async () => {
    const wrapper = mountAdapter({
      type: "bar",
      title: "Achievement",
      metric: "Rate",
      categories: ["A", "B"],
      values: [1, 2]
    });

    await flushPromises();
    wrapper.unmount();

    expect(observeMock).toHaveBeenCalled();
    expect(disconnectMock).toHaveBeenCalledTimes(1);
    expect(disposeMock).toHaveBeenCalledTimes(1);
  });
});
