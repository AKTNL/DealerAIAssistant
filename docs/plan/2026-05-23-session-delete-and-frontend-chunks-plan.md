# Session Delete And Frontend Chunks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tighten chat session deletion ownership checks and split large frontend vendor dependencies with function-form Vite manual chunks.

**Architecture:** The backend change is limited to `ChatController.clearSession()`, switching the delete path from claim-or-verify to strict ownership verification. The frontend change is limited to Vite build configuration plus a small config test that verifies ECharts subpath imports are routed to the same chunk.

**Tech Stack:** Spring Boot, JUnit 5, Mockito, MockMvc, Vue 3, Vite 6, Rollup manual chunks, Vitest.

---

## File Structure

- Modify `backend/src/test/java/com/brand/agentpoc/controller/ChatControllerTest.java`: encode the desired delete ownership behavior before implementation.
- Modify `backend/src/main/java/com/brand/agentpoc/controller/ChatController.java`: use `SessionOwnershipService.owns()` on the delete path.
- Create `frontend/src/__tests__/viteConfig.spec.js`: verify function-form `manualChunks(id)` maps dependency IDs to intended vendor chunks.
- Modify `frontend/vite.config.js`: add `build.rollupOptions.output.manualChunks` as a function using normalized module IDs.

---

### Task 1: Backend Delete Ownership Tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/controller/ChatControllerTest.java`

- [ ] **Step 1: Replace the successful delete test with strict ownership semantics**

Replace `clearsSessionWhenTokenCanClaimOrVerifySession()` with:

```java
@Test
void clearsSessionWhenTokenOwnsSession() throws Exception {
    when(sessionOwnershipService.owns("session-1", "token-subject")).thenReturn(true);

    mockMvc.perform(delete("/api/chat/session-1")
                    .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

    verify(sessionOwnershipService).owns("session-1", "token-subject");
    verify(sessionOwnershipService, never()).claimOrVerify("session-1", "token-subject");
    verify(sessionMemoryService).clearSession("session-1");
    verify(sessionOwnershipService).release("session-1", "token-subject");
}
```

- [ ] **Step 2: Replace the rejected delete test with strict ownership semantics**

Replace `rejectsClearSessionWhenTokenDoesNotOwnSession()` with:

```java
@Test
void rejectsClearSessionWhenTokenDoesNotOwnSession() throws Exception {
    when(sessionOwnershipService.owns("session-1", "other-token")).thenReturn(false);

    mockMvc.perform(delete("/api/chat/session-1")
                    .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "other-token"))
            .andExpect(status().isForbidden());

    verify(sessionOwnershipService).owns("session-1", "other-token");
    verify(sessionOwnershipService, never()).claimOrVerify("session-1", "other-token");
    verify(sessionMemoryService, never()).clearSession("session-1");
    verify(sessionOwnershipService, never()).release("session-1", "other-token");
}
```

- [ ] **Step 3: Add an explicit unregistered session rejection test**

Add this test below the rejected delete test:

```java
@Test
void rejectsClearSessionWhenSessionWasNeverRegistered() throws Exception {
    when(sessionOwnershipService.owns("missing-session", "token-subject")).thenReturn(false);

    mockMvc.perform(delete("/api/chat/missing-session")
                    .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject"))
            .andExpect(status().isForbidden());

    verify(sessionOwnershipService).owns("missing-session", "token-subject");
    verify(sessionOwnershipService, never()).claimOrVerify("missing-session", "token-subject");
    verify(sessionMemoryService, never()).clearSession("missing-session");
    verify(sessionOwnershipService, never()).release("missing-session", "token-subject");
}
```

- [ ] **Step 4: Run the focused backend test and confirm it fails**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ChatControllerTest" test
```

Expected before implementation: FAIL. The successful delete test should receive `403` because `ChatController.clearSession()` still calls `claimOrVerify()`, whose unstubbed mock result is `false`.

---

### Task 2: Backend Delete Ownership Implementation

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/controller/ChatController.java`
- Test: `backend/src/test/java/com/brand/agentpoc/controller/ChatControllerTest.java`

- [ ] **Step 1: Change the delete path to strict ownership verification**

In `ChatController.clearSession()`, replace:

```java
if (!sessionOwnershipService.claimOrVerify(sessionId, tokenSubject)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

with:

```java
if (!sessionOwnershipService.owns(sessionId, tokenSubject)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

Leave the chat and stream paths unchanged; they should continue using `claimOrVerify()`.

- [ ] **Step 2: Run the focused backend test and confirm it passes**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ChatControllerTest" test
```

Expected after implementation: PASS for all `ChatControllerTest` tests.

- [ ] **Step 3: Commit the backend implementation**

Run:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/controller/ChatController.java backend/src/test/java/com/brand/agentpoc/controller/ChatControllerTest.java
git commit -m "fix: require chat session ownership before delete"
```

Expected: commit includes both the red tests from Task 1 and the implementation that makes them green.

---

### Task 3: Frontend Manual Chunk Tests

**Files:**
- Create: `frontend/src/__tests__/viteConfig.spec.js`
- Modify: `frontend/vite.config.js`

- [ ] **Step 1: Write a failing Vitest spec for chunk routing**

Create `frontend/src/__tests__/viteConfig.spec.js` with:

```javascript
import { describe, expect, test } from "vitest";
import config from "../../vite.config.js";

function manualChunkFor(id) {
  const manualChunks = config.build?.rollupOptions?.output?.manualChunks;
  expect(typeof manualChunks).toBe("function");
  return manualChunks(id);
}

describe("vite manual chunks", () => {
  test("groups Vue runtime packages into the Vue vendor chunk", () => {
    expect(manualChunkFor("D:/repo/frontend/node_modules/vue/dist/vue.runtime.esm-bundler.js"))
      .toBe("vendor-vue");
    expect(manualChunkFor("D:/repo/frontend/node_modules/@vue/runtime-core/dist/runtime-core.esm-bundler.js"))
      .toBe("vendor-vue");
  });

  test("groups ECharts subpath imports into one vendor chunk", () => {
    expect(manualChunkFor("D:/repo/frontend/node_modules/echarts/charts.js")).toBe("vendor-echarts");
    expect(manualChunkFor("D:/repo/frontend/node_modules/echarts/components.js")).toBe("vendor-echarts");
    expect(manualChunkFor("D:/repo/frontend/node_modules/echarts/core.js")).toBe("vendor-echarts");
    expect(manualChunkFor("D:/repo/frontend/node_modules/echarts/renderers.js")).toBe("vendor-echarts");
  });

  test("groups Mermaid and markdown rendering libraries into dedicated chunks", () => {
    expect(manualChunkFor("D:/repo/frontend/node_modules/mermaid/dist/mermaid.esm.mjs"))
      .toBe("vendor-mermaid");
    expect(manualChunkFor("D:/repo/frontend/node_modules/markdown-it/index.mjs"))
      .toBe("vendor-markdown");
    expect(manualChunkFor("D:/repo/frontend/node_modules/highlight.js/lib/core.js"))
      .toBe("vendor-markdown");
  });

  test("leaves application modules to Rollup default chunking", () => {
    expect(manualChunkFor("D:/repo/frontend/src/components/chat/AssistantMessage.vue")).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run the focused frontend spec and confirm it fails**

Run:

```powershell
cd frontend
npm.cmd run test -- src/__tests__/viteConfig.spec.js
```

Expected before implementation: FAIL because `config.build.rollupOptions.output.manualChunks` is not defined.

---

### Task 4: Frontend Manual Chunk Implementation

**Files:**
- Modify: `frontend/vite.config.js`
- Test: `frontend/src/__tests__/viteConfig.spec.js`

- [ ] **Step 1: Add a function-form `manualChunks(id)` helper**

Update `frontend/vite.config.js` to:

```javascript
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function manualChunks(id) {
  const normalizedId = id.replace(/\\/g, "/");

  if (!normalizedId.includes("/node_modules/")) {
    return undefined;
  }

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

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js"
  },
  build: {
    outDir: "../backend/src/main/resources/static",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks
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
```

- [ ] **Step 2: Run the focused frontend spec and confirm it passes**

Run:

```powershell
cd frontend
npm.cmd run test -- src/__tests__/viteConfig.spec.js
```

Expected after implementation: PASS for all tests in `viteConfig.spec.js`.

- [ ] **Step 3: Run the frontend build and inspect chunk output**

Run:

```powershell
cd frontend
npm.cmd run build
```

Expected: build succeeds. Output should include separate files whose names start with `vendor-vue`, `vendor-mermaid`, `vendor-echarts`, and `vendor-markdown`.

- [ ] **Step 4: Commit the frontend implementation**

Run:

```powershell
git add -- frontend/vite.config.js frontend/src/__tests__/viteConfig.spec.js
git commit -m "build: split large frontend vendor chunks"
```

---

### Task 5: Final Regression

**Files:**
- Verify all files touched in prior tasks.

- [ ] **Step 1: Run the backend focused test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ChatControllerTest" test
```

Expected: PASS.

- [ ] **Step 2: Run the backend full test suite**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" test
```

Expected: PASS.

- [ ] **Step 3: Run the frontend full test suite**

Run:

```powershell
cd frontend
npm.cmd run test
```

Expected: PASS.

- [ ] **Step 4: Run the frontend production build**

Run:

```powershell
cd frontend
npm.cmd run build
```

Expected: PASS. The output should contain the dedicated vendor chunks configured in `manualChunks(id)`.

- [ ] **Step 5: Inspect git diff**

Run:

```powershell
git status --short
git diff -- backend/src/main/java/com/brand/agentpoc/controller/ChatController.java backend/src/test/java/com/brand/agentpoc/controller/ChatControllerTest.java frontend/vite.config.js frontend/src/__tests__/viteConfig.spec.js
```

Expected: only the intended implementation files should remain changed if commits were skipped; if commits were made task-by-task, these implementation files should be clean.
