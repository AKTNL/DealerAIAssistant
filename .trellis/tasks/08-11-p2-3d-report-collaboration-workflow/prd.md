# P2-3D 报告协作工作流

## Goal

让 tenant/组织范围内的用户对报告进行负责人分配、处理状态更新和受控协作，并保留可追溯历史。

## What I Already Know

* 生产化路线图和 P2-3 父任务都明确要求报告评论、处理状态、负责人、操作历史和范围内协作通知。
* 当前 `ReportDraft` 是 tenant/组织范围内的不可变报告快照，尚无协作状态、版本或评论模型。
* 当前报告读取统一经过 `OrganizationAuthorizationService` 和 `ReportService` 的 tenant/组织范围检查；协作读写必须复用同一边界。
* `TenantMemberDirectory` 已能验证 tenant 内启用且具有 `REPORT_READ` 权限的成员，可扩展为负责人候选校验，但还需要验证候选人的组织范围覆盖报告范围。
* P2-3C 已提供 `ReportDeliveryPort` 和持久化投递能力；协作服务只能调用端口/应用服务，不能直连 SMTP provider。
* 前端现有报告入口是 `ChatView` 中受 `REPORT_READ` 控制的报告工作区，可在此增加报告协作列表和详情，而无需引入新的路由框架。

## Dependencies

* 前置：P2-3A 订阅范围、P2-2B tenant/组织隔离、P2-1C 管理/权限前端模式。
* 可与 P2-3B/P2-3C 在接口稳定后并行。

## Requirements

* 定义最小状态机，例如 `OPEN -> IN_PROGRESS -> RESOLVED/CLOSED`，非法回退或终态变更稳定拒绝。
* assignee 必须是当前 tenant 且拥有报告范围访问权的有效成员。
* 首版包含按时间排序的单层不可编辑评论；不支持回复层级、`@mention`、编辑或删除。
* 状态、负责人、评论和版本变更都记录 actor、时间、traceId 和前后值摘要。
* 并发更新使用版本控制，返回可理解冲突，不静默覆盖他人修改。
* 报告列表支持按状态、负责人、组织和时间筛选，始终服从 tenant/组织范围。
* 协作通知通过 P2-3C 端口发送，不在业务服务中直连 provider。
* 新增 `REPORT_COLLABORATE` 权限：管理员和分析员可变更状态、负责人和评论，只有 `REPORT_READ` 的用户保持只读。
* 协作通知只发送给当前负责人；负责人本人触发的操作不向本人发送通知。

## Acceptance Criteria

* [x] 状态机、并发冲突、越权 assignee 和跨范围评论/更新具有允许/拒绝测试。
* [x] 禁用用户后的历史保留，新的分配和操作停止。
* [x] 页面具备 empty/loading/error/conflict 状态并与后端版本一致。
* [x] 审计可还原报告从创建到关闭的处理时间线。

## Definition of Done

* 后端单元/集成测试、前端组件/composable 测试覆盖核心允许与拒绝路径。
* 后端测试、PMD，前端测试、lint 和生产构建通过。
* OpenAPI、数据库迁移和中英文界面文案与实现保持一致。
* 新发现的协作、审计或并发契约沉淀到 `.trellis/spec/`。

## Feasible MVP Approaches

### A. 状态和负责人

* 只实现状态机、负责人、版本冲突、筛选、时间线与通知。
* 交付最小，但不满足路线图中已经明确的“报告评论”能力。

### B. 状态、负责人和单层评论（推荐）

* 评论是按时间排序的不可编辑事件，不支持回复层级和 `@mention`。
* 满足既有路线图，数据/API/UI 边界清晰，并为以后增加线程保留独立 comment id。

### C. 状态、负责人和线程化评论

* 评论支持 parent id、回复层级和提及通知。
* 协作更完整，但显著扩大权限校验、通知收件人、前端交互和测试矩阵。

## Decision (ADR-lite)

**Context**: 路线图要求评论能力，但线程、提及和可编辑评论会扩大权限、通知、数据模型与前端交互范围。

**Decision**: 首版采用方案 B，实现状态、负责人和按时间排序的单层不可编辑评论，并为每条评论分配独立 id。

**Consequences**: 首版满足既有路线图且控制交付复杂度；未来可在不改变现有评论语义的前提下增加 parent id 和提及关系。

## Technical Approach

* 保持 `ReportDraft` 内容不可变，以独立协作记录关联 report id；协作记录保存 tenant、报告范围摘要、状态、负责人和乐观锁版本。
* 以追加式事件/评论记录保存 actor、时间、traceId、事件类型和前后值摘要；禁用用户后保留显示快照，不再允许其执行新操作或被新分配。
* 服务层先加载报告并校验当前 actor 范围，再校验 assignee 对报告范围的覆盖；所有仓储查询显式带 tenant id。
* 更新 API 要求客户端提交 version；版本不匹配返回 HTTP 409 和当前版本，前端保留用户上下文并支持刷新后重试。
* 列表筛选在仓储层按 tenant/status/assignee/time 收窄，组织范围仍由授权上下文强制约束。
* 协作通知通过 P2-3C 应用端口异步持久化，不让 provider 失败回滚已提交的协作状态。
* API 使用 `/api/report-collaborations`：GET 需要 `REPORT_READ`，PATCH/POST 需要 `REPORT_COLLABORATE`；冲突响应携带当前版本。

## Technical Notes

* 后端相关入口：`ReportController`、`ReportService`、`ReportDraftStore`、`OrganizationAuthorizationService`、`TenantMemberDirectory`、`ReportDeliveryPort`。
* 前端相关入口：`ChatView.vue`、现有 reporting API/composable/component 分层和 `messages.js`。
* 数据库需要新增版本化协作记录、追加式事件/评论以及 tenant/report/筛选索引；不能把可变协作字段直接塞入不可变报告正文。

## Out of Scope

* 不实现通用工单、审批流、附件或实时协同编辑。
* 不允许跨 tenant 协作者。
* 除非选择方案 C，否则不实现评论回复层级、`@mention`、评论编辑或删除。
