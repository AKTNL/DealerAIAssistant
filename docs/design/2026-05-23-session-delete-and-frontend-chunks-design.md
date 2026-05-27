# 会话删除权限与前端分包优化设计

## 背景

`docs/待优化.md` 中剩余两项需要处理：

- 阻断项：`DELETE /api/chat/{sessionId}` 使用 `claimOrVerify()`，会在删除路径上认领未登记会话，权限边界过宽。
- 非阻断项：前端构建成功，但 Vite 报告部分 chunk 超过 500 kB。

本设计同时处理这两项，但保持实现范围克制：修正删除接口权限语义，并通过构建配置拆分大型前端依赖。

## 目标

- 删除会话时只接受已存在的会话归属关系。
- 未登记或不归属当前 token 的 session 不能被清理。
- 前端构建不再把 Mermaid、ECharts、Markdown 渲染等大型依赖压入同一个主入口 chunk。
- 保持现有聊天、流式输出、Mermaid 渲染和分析图表的运行时行为不变。

## 非目标

- 不改变 `POST /api/chat` 和 `POST /api/chat/stream` 的认领语义；这两个入口仍可用 `claimOrVerify()` 建立或验证归属。
- 不新增持久化会话归属存储。
- 不重构 `AssistantMessage.vue` 的 Mermaid 渲染流程。
- 不把 `AnalysisChart.vue` 或 Mermaid 渲染改成异步组件。
- 不通过单纯调高 `chunkSizeWarningLimit` 隐藏告警。

## 后端设计

### 接口语义

`ChatController.clearSession()` 改为：

1. 从 `HttpServletRequest` 读取 token subject。
2. 使用 `sessionOwnershipService.owns(sessionId, tokenSubject)` 校验已存在归属关系。
3. 校验失败返回 `403 Forbidden`。
4. 校验成功后调用 `sessionMemoryService.clearSession(sessionId)`。
5. 清理成功后调用 `sessionOwnershipService.release(sessionId, tokenSubject)`。
6. 返回 `SimpleSuccessResponse(true)`。

选择 `403` 作为未登记 session 的响应，是为了保持当前“不允许清理”路径的状态码一致，避免前端或测试同时适配 `403/404` 两种语义。

### 服务边界

`SessionOwnershipService.claimOrVerify()` 保持不变，继续服务于会话创建和消息发送路径。

`SessionOwnershipService.owns()` 已存在，删除接口直接复用它，不新增新的 service 方法。

## 后端测试

更新 `ChatControllerTest`：

- 将成功清理测试改名为“token 拥有 session 时清理会话”。
- 成功路径 mock `sessionOwnershipService.owns("session-1", "token-subject")` 返回 `true`。
- 断言成功路径调用 `clearSession()` 和 `release()`。
- 拒绝路径 mock `owns()` 返回 `false`。
- 断言拒绝路径返回 `403`，不调用 `clearSession()`，不调用 `release()`。
- 增加断言或覆盖现有测试，确保删除路径不再调用 `claimOrVerify()`。

## 前端分包设计

### 构建配置

在 `frontend/vite.config.js` 的 `build.rollupOptions.output.manualChunks` 中按大型依赖拆包，并使用函数形式而不是对象形式。

本项目当前是 ECharts 按需引入：

- `echarts/charts`
- `echarts/components`
- `echarts/core`
- `echarts/renderers`

因此分包规则需要通过 `id.includes("echarts")` 覆盖 `echarts/*` 子路径，确保 ECharts 相关模块进入同一个 chunk。Mermaid、Markdown 和 Highlight.js 也采用同一类路径匹配，避免对象形式遗漏深层导入或触发不必要的分包边界。

- `vendor-vue`：`vue`
- `vendor-mermaid`：`mermaid`
- `vendor-echarts`：所有路径中包含 `echarts` 的模块
- `vendor-markdown`：`markdown-it`、`highlight.js`

其他依赖继续交给 Rollup 默认策略处理。

### 行为边界

这次只调整构建产物的 chunk 拆分，不调整组件加载时机。

原因：

- 当前告警来自构建体积，不是功能失败。
- Mermaid 和 ECharts 已与现有测试、流式渲染时序耦合较多。
- 构建分包能降低入口 chunk 压力，且风险小于动态 import。

## 验证

后端：

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ChatControllerTest" test
```

前端：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

最终回归可按 `docs/待优化.md` 中基线执行：

```powershell
cd backend
mvn "-Dfrontend.skip=true" test

cd ..\frontend
npm.cmd run test
npm.cmd run build
```

## 风险

- 如果前端 chunk 仍有超过 500 kB 的单个第三方依赖包，manual chunks 可能降低主入口大小但无法完全消除所有告警。
- 如果使用对象形式 `manualChunks`，按需引入的 `echarts/*` 子路径可能没有稳定落到同一个 vendor chunk；本次实现必须使用函数形式按 `id` 匹配。
- 如果未来要进一步压缩首屏资源，需要再单独设计 Mermaid 或 ECharts 的动态加载。
- 删除接口改为严格 `owns()` 后，服务重启导致内存归属丢失时，旧 session 将不能被当前 token 删除；这是本次安全修正的预期取舍。

## 完成标准

- `DELETE /api/chat/{sessionId}` 不再调用 `claimOrVerify()`。
- 未登记 session 删除请求返回 `403`，且不会清理内存或释放归属。
- `ChatControllerTest` 覆盖成功和拒绝路径。
- 前端构建产物通过函数形式 `manualChunks(id)` 将 Mermaid、ECharts、Markdown 相关依赖拆为独立 vendor chunk。
- 后端定向测试、前端测试和前端构建通过。
