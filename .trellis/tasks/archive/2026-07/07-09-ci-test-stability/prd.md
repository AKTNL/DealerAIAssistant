# 修复 CI 测试稳定性

## Goal

让刚推送到 `origin/main` 的 GitHub Actions CI 在远端稳定通过，优先修复测试对运行环境的脆弱假设，不改变业务行为。

## Requirements

* 修复后端 `ChatServiceTest.streamsConfiguredModelRepliesAcrossMultipleMessageEvents` 在 Linux CI 上因换行符差异失败的问题。
* 修复前端 `AssistantMessage.spec.js` 中 `renders chart-json fences through the lazy ECharts adapter` 在 CI 上因 lazy/async 组件等待不足导致的失败。
* 改动应聚焦测试稳定性，不重写业务逻辑。
* 本地运行相关后端和前端测试通过。
* 推送后重新检查 GitHub Actions CI 状态。

## Acceptance Criteria

* [x] 后端相关单测和完整后端测试通过。
* [x] 前端相关单测和完整前端测试通过。
* [ ] GitHub Actions `CI` workflow 的 backend/frontend jobs 通过。
* [ ] 工作树收尾干净，任务归档并记录 journal。

## Definition of Done

* 测试修复提交完成。
* 任务归档提交和 journal 提交完成。
* 变更推送到 `origin/main` 并触发远端 CI。

## Technical Approach

后端测试不要写死 `\r\n`，改为先将 SSE payload 标准化为 `\n` 后断言事件顺序。前端测试等待 chart-json lazy adapter 渲染到 DOM，而不是在一次 flush 后立即断言异步组件存在。

## Decision (ADR-lite)

**Context**: 远端 CI run `29003597404` 在 rerun 后仍失败。后端日志显示 payload 实际是 Linux 风格换行；前端日志显示 `.chart-json-block` 已存在但 lazy adapter 断言过早失败。

**Decision**: 只加固测试断言与等待逻辑，不改生产组件/服务逻辑。

**Consequences**: 测试更能反映真实契约：SSE 消息顺序不依赖 OS 换行，lazy 组件测试等待 UI 完成渲染。

## Out of Scope

* 不调整 CI workflow 结构或运行矩阵。
* 不修改 ChatService 的流式输出协议。
* 不修改 AssistantMessage 的生产渲染逻辑，除非测试证明现有实现本身有缺陷。

## Technical Notes

* GitHub Actions run: `https://github.com/AKTNL/DealerAIAssistant/actions/runs/29003597404`
* Backend failure: `ChatServiceTest.streamsConfiguredModelRepliesAcrossMultipleMessageEvents` line 649.
* Frontend failure: `frontend/src/components/__tests__/AssistantMessage.spec.js` line 814.
* Local full suites passed before this task:
  * `mvn "-Dfrontend.skip=true" test` -> 263 tests passed.
  * `npm test` -> 202 tests passed.
* After the fix, local verification passed:
  * `mvn "-Dfrontend.skip=true" "-Dtest=ChatServiceTest#streamsConfiguredModelRepliesAcrossMultipleMessageEvents" test`
  * `npm test -- AssistantMessage.spec.js`
  * `mvn "-Dfrontend.skip=true" test` -> 263 tests passed.
  * `npm test` -> 202 tests passed.
  * `git diff --check`
