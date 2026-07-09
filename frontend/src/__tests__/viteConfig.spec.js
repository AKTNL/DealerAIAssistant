// @vitest-environment node

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import config from "../../vite.config.js";

describe("vite manual chunks", () => {
  const manualChunks = config.build?.rollupOptions?.output?.manualChunks;

  it("preserves backend-owned static files during frontend builds", () => {
    expect(config.build?.outDir).toBe("../backend/src/main/resources/static");
    expect(config.build?.emptyOutDir).toBe(false);

    const packageJson = JSON.parse(readFileSync(resolve(process.cwd(), "package.json"), "utf8"));
    expect(packageJson.scripts?.prebuild).toBe("node scripts/clean-build-assets.js");
  });

  it("uses function-form manual chunks", () => {
    expect(manualChunks).toEqual(expect.any(Function));
  });

  it.each([
    ["/project/node_modules/vue/dist/vue.runtime.esm-bundler.js", "vendor-vue"],
    ["/project/node_modules/@vue/runtime-core/dist/runtime-core.esm-bundler.js", "vendor-vue"],
    ["/project/node_modules/echarts/charts.js", "vendor-echarts"],
    ["/project/node_modules/echarts/components.js", "vendor-echarts"],
    ["/project/node_modules/echarts/core.js", "vendor-echarts"],
    ["/project/node_modules/echarts/renderers.js", "vendor-echarts"],
    ["/project/node_modules/mermaid/dist/mermaid.esm.mjs", "vendor-mermaid"],
    ["/project/node_modules/markdown-it/index.mjs", "vendor-markdown"],
    ["/project/node_modules/highlight.js/es/core.js", "vendor-markdown"],
    ["C:\\project\\node_modules\\vue\\dist\\vue.runtime.esm-bundler.js", "vendor-vue"],
    ["C:\\project\\node_modules\\echarts\\charts.js", "vendor-echarts"]
  ])("maps %s to %s", (id, chunkName) => {
    expect(manualChunks(id)).toBe(chunkName);
  });

  it.each([
    "/project/src/App.vue",
    "/project/src/components/ChatPanel.vue",
    "/project/node_modules/lodash-es/lodash.js"
  ])("leaves %s unchunked", (id) => {
    expect(manualChunks(id)).toBeUndefined();
  });
});
