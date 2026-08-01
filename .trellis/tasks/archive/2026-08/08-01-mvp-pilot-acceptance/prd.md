# MVP 试点验收脚本和反馈表

## Goal

在 P0 Dashboard MVP 已落地之后，补齐一套可执行的 MVP 试点验收材料，让项目能从“功能完成”进入“业务用户可演示、可反馈、可决定 P1 优先级”的阶段。

## What I Already Know

* 用户认可下一步先做 MVP 试点验收，而不是直接进入完整 P1 工程化。
* `docs/07-生产化升级路线图.md` 建议 Dashboard MVP 后进入“阶段 3：MVP 试点验收”。
* `docs/08-MVP产品需求文档.md` 显示 P0 Dashboard 首版已落地，质量命令已通过。
* `docs/08-MVP产品需求文档.md` 中仍有一项未完成：PRD 中的验收问题可以用于人工演示。
* `docs/08-MVP产品需求文档.md` 已列出演示流程、成功指标和 5 个验收问题示例。
* `docs/06-数据导入MVP实施说明.md` 已列出 5 类模拟业务故事、数据表现和对应验收问题。
* `docs/05-测试验收方案.md` 已把“人工验收模板”列为 P1 待拆任务，并强调人工验收不能替代自动化回归。
* `docs/` 下当前没有独立的 MVP 试点验收脚本或反馈表文档。

## Assumptions

* 本任务优先产出文档化验收材料，不新增复杂产品功能。
* 验收对象是当前 Dashboard MVP、现有聊天/规则分析链路和模拟业务数据。
* 验收材料应能帮助判断 P1 中筛选、趋势、下钻、报告草稿、真实数据接入等任务的优先级。
* 验收材料以内部演示验收为主，同时保留真实业务用户试点反馈字段。
* 本任务会实际启动前后端跑一遍 MVP 演示流程，并把通过结果或阻塞原因记录进文档。
* 实际试跑记录以文字结果和观察结论为主；只有遇到启动失败或明显 UI 异常时再记录截图线索。

## Open Questions

* None.

## Requirements

* 提供一套可跑通的端到端 MVP 演示/验收脚本。
* 覆盖用户是否能从 Dashboard 发现经营问题。
* 覆盖 AI/规则分析是否能解释 Dashboard 异常。
* 提供内部演示记录和真实业务用户反馈字段，用于形成下一阶段工程化任务清单。
* 将 `docs/08-MVP产品需求文档.md` 的验收问题和 `docs/06-数据导入MVP实施说明.md` 的业务故事映射到同一张验收表。
* 明确每个验收问题的 Dashboard 入口、预期观察、需要记录的反馈和可能导向的 P1 能力。
* 实际启动前后端，按验收脚本跑一遍核心 MVP 路径，并记录执行结果；如果环境启动失败，则记录命令、失败现象和后续处理建议。
* 更新 docs 索引或相关状态说明，让后续开发者知道 MVP 试点验收材料的位置。

## Acceptance Criteria

* [x] 验收脚本包含从登录、查看 Dashboard、识别异常、触发分析到记录结论的完整路径。
* [x] 验收问题能映射到当前模拟数据和 Dashboard 模块。
* [x] 反馈表能记录洞察是否有用、口径是否清晰、是否需要 P1 能力支持。
* [x] 输出下一阶段 P1 候选任务排序依据。
* [x] 文档能说明仅改文档时不需要运行完整前后端质量命令，但不得替代已有自动化回归。
* [x] 试跑记录包含执行日期、环境、命令、结果、阻塞项和下一步建议。
* [x] `docs/README.md` 或相关当前优先级文档能指向新验收材料。

## Definition of Done

* 验收文档/脚本已新增或更新。
* 实际 MVP 试跑完成，或记录清晰的本地环境阻塞。
* 如仅改文档，确认没有修改代码行为；可跳过完整前后端 lint/test/build，但必须记录未运行原因。
* 如发现文档口径变化，同步更新相关 MVP/路线图文档。
* 记录本任务对 P1 拆分的明确建议。

## Out of Scope

* 不实现持久化数据库、迁移工具、真实数据接入、受控 Agent、RAG 或自动报告。
* 不重做 Dashboard UI/API。
* 不用人工验收替代已有自动化回归。
* 不把截图包作为必交付物。

## Decision (ADR-lite)

**Context**: MVP Dashboard 已落地，下一步需要把功能完成转成可重复演示、可记录反馈、可决定 P1 优先级的验收闭环。

**Decision**: 本任务的验收材料以内部演示验收为主，同时保留真实业务用户试点反馈字段。

**Consequences**: 交付可以快速落地，不需要完整试点治理流程；反馈表仍保留“用户是否愿意提供真实/脱敏数据继续试点”“哪些能力阻塞业务使用”等字段，便于后续进入 P1。

**Decision 2**: 本任务交付文档模板，并实际跑一遍 MVP 演示流程，把通过结果或阻塞原因记录进文档。

**Consequences 2**: 验收脚本本身会被验证，不只是静态模板；任务可能受本地端口、依赖安装、数据导入或登录配置影响，相关问题会被记录为试跑阻塞而不是扩大本任务范围去做生产化修复。

## Technical Approach

* 新增 `docs/09-MVP试点验收脚本.md`，包含演示准备、端到端验收步骤、业务故事映射表、验收问题记录表、反馈表和 P1 排序规则。
* 更新 `docs/README.md`，把新文档加入推荐阅读顺序。
* 如合适，更新 `docs/08-MVP产品需求文档.md` 的未完成验收项状态或指向新文档。
* 使用 README 的本地启动链路进行实际试跑：
  * 后端：设置 `APP_ACCESS_KEY`、`APP_SESSION_SECRET`、`APP_API_KEY` 后运行 `mvn "-Dfrontend.skip=true" spring-boot:run`。
  * 前端：`npm ci` 后运行 `npm run dev`。
  * 浏览器访问 `http://localhost:5173`，用本地访问密钥登录并查看 Dashboard/AI 分析。

## Technical Notes

* Core docs: `docs/07-生产化升级路线图.md`, `docs/08-MVP产品需求文档.md`.
* Supporting docs: `docs/05-测试验收方案.md`, `docs/06-数据导入MVP实施说明.md`.
* Likely deliverable: new `docs/09-MVP试点验收脚本.md`, plus small index/status updates in existing docs if needed.
* Startup source: `README.md` local startup section.
* Task directory: `.trellis/tasks/08-01-mvp-pilot-acceptance`.

## Implementation Plan

1. Draft the new MVP pilot acceptance document from existing business stories and acceptance questions.
2. Update docs index/status links so the new material is discoverable.
3. Start backend and frontend locally with documented demo environment variables.
4. Use the browser to log in, inspect Dashboard, trigger at least one analysis question, and record pass/blocker notes.
5. Review docs-only diff and record why full automated tests were or were not needed.
