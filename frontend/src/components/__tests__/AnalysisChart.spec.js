import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, test, vi } from "vitest";

const { disposeMock, initMock, resizeMock, setOptionMock, useMock } = vi.hoisted(() => ({
  disposeMock: vi.fn(),
  initMock: vi.fn(),
  resizeMock: vi.fn(),
  setOptionMock: vi.fn(),
  useMock: vi.fn()
}));

vi.mock("echarts/charts", () => ({
  BarChart: {},
  HeatmapChart: {},
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
  init: initMock,
  use: useMock
}));

vi.mock("echarts/renderers", () => ({
  CanvasRenderer: {}
}));

import AnalysisChart from "../chat/AnalysisChart.vue";

const chartInstance = {
  dispose: disposeMock,
  resize: resizeMock,
  setOption: setOptionMock
};

function barModel() {
  return {
    id: "chart-1",
    maxLabel: "Hangzhou A",
    metric: "Achievement Rate",
    minLabel: "Shanghai B",
    rows: [
      { display: "130.0%", label: "Hangzhou A", value: 130 },
      { display: "76.2%", label: "Shanghai B", value: 76.2 },
      { display: "98.4%", label: "Chengdu C", value: 98.4 }
    ],
    title: "Achievement Rate Comparison",
    type: "bar"
  };
}

beforeEach(() => {
  disposeMock.mockClear();
  initMock.mockReset();
  resizeMock.mockClear();
  setOptionMock.mockClear();
  initMock.mockReturnValue(chartInstance);
});

describe("AnalysisChart", () => {
  test("initializes echarts and marks highest and lowest bars", async () => {
    mount(AnalysisChart, {
      attachTo: document.body,
      props: { model: barModel() }
    });

    await flushPromises();

    expect(initMock).toHaveBeenCalledTimes(1);
    expect(setOptionMock).toHaveBeenCalled();
    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.series[0].data[0].itemStyle.color).toBe("#2f7da8");
    expect(option.series[0].data[1].itemStyle.color).toBe("#a43f3f");
  });

  test("updates options when sort and filter controls change", async () => {
    const wrapper = mount(AnalysisChart, {
      attachTo: document.body,
      props: { model: barModel() }
    });

    await flushPromises();
    await wrapper.find('[data-analysis-chart-control="sort"]').setValue("desc");
    await wrapper.find('[data-analysis-chart-control="filter"]').setValue("Chengdu");

    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.yAxis.data).toEqual(["Chengdu C"]);
  });

  test("disposes chart on unmount", async () => {
    const wrapper = mount(AnalysisChart, {
      attachTo: document.body,
      props: { model: barModel() }
    });

    await flushPromises();
    wrapper.unmount();

    expect(disposeMock).toHaveBeenCalledTimes(1);
  });
});
