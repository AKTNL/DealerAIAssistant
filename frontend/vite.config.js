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
    emptyOutDir: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, "/");

          if (normalizedId.includes("/node_modules/vue/") || normalizedId.includes("/node_modules/@vue/")) {
            return "vendor-vue";
          }

          if (normalizedId.includes("/node_modules/mermaid/")) {
            return "vendor-mermaid";
          }

          if (normalizedId.includes("/node_modules/echarts/")) {
            return "vendor-echarts";
          }

          if (
            normalizedId.includes("/node_modules/markdown-it/") ||
            normalizedId.includes("/node_modules/highlight.js/")
          ) {
            return "vendor-markdown";
          }

          return undefined;
        }
      }
    }
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
