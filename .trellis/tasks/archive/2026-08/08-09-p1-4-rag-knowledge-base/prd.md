# P1-4 RAG 知识库

## Goal

在 P1-3 受控 Agent 基础上交付首版可验证的业务知识检索能力，让对话能够引用 SOP、政策、指标口径和产品知识解释业务问题，同时继续把结构化 KPI 查询留在现有指标/数据服务中。

## What I already know

- 路线图明确：结构化 KPI 不进入 RAG；RAG 只承载业务制度、口径说明、SOP、产品知识等非结构化资料。
- RAG 结果只作为解释上下文，不得改写或覆盖结构化 KPI 事实。
- P1-3 已完成受控 Agent 运行时和四个只读业务工具；`retrieveKnowledge` 目前尚未注册，预留给本任务。
- 新能力应归属 `knowledge` 模块，通过 application port 暴露给 `agent`，不得把向量检索实现塞入现有 Agent 工具门面。
- 当前授权边界仍是已认证请求、session ownership 与 active batch；完整组织/角色/多租户授权尚未实现。

## Requirements

- 建立独立 `knowledge` 模块及清晰的 domain/application/infrastructure 边界。
- 首版只摄取随应用发布、经过代码审查的 Markdown 知识文档；至少覆盖 KPI 口径、销售运营 SOP、经销商政策和产品/活动规则。
- 支持知识 manifest 校验、确定性切片、稳定 chunk id、版本替换、索引和受限 Top-K 检索。
- 检索结果包含来源、片段定位或等价引用信息，不只返回无出处文本。
- 向 Agent 注册受控的 `retrieveKnowledge` 工具，并保持现有 HTTP/SSE 协议兼容。
- 明确隔离结构化经营数据与非结构化业务知识；知识结果不得覆盖现有事实锚点。
- 覆盖无结果、embedding/索引不可用、越权或非法参数、调用超预算等失败路径。
- 默认 local/test 不依赖外部模型或 PostgreSQL即可完成确定性检索回归；生产环境保留 PostgreSQL/PGvector 语义检索适配路径。
- 为未来 tenant/scope metadata、管理员摄取和索引重建保留 application port，但首版不公开对应 HTTP/UI。

## Acceptance Criteria

- [ ] 对仓库内样例 SOP、政策、口径或产品资料可以建立索引，并返回稳定的 Top-K 相关片段。
- [ ] 每个结果都包含 `documentId`、来源、版本、章节、chunk id、excerpt 和相关性信息。
- [ ] 缺失/重复文档标识、空内容、非法 query 或越界 Top-K 被明确拒绝；无命中返回显式 no-match 语义。
- [ ] `retrieveKnowledge` 只通过 knowledge application port 检索，不直接访问底层存储实现。
- [ ] Agent 仅在既有受控策略内调用知识检索，且同步与 SSE 路径语义一致。
- [ ] 知识库不可用或无命中时，不伪造答案、不影响结构化 KPI 查询，并按既有降级语义返回。
- [ ] 默认 H2 开发/测试启动不依赖外部 embedding 服务；生产 PGvector 配置缺失或不可用时明确失败且不静默回退到未声明存储。
- [ ] 后端编译、PMD、单元测试与集成测试通过。

## Definition of Done (team quality bar)

- Tests added/updated (unit/integration where appropriate)
- Lint / typecheck / CI green
- Docs/notes updated if behavior changes
- Rollout/rollback considered if risky

## Out of Scope (explicit)

- 用 RAG 存储或计算结构化 KPI、明细事实或 active batch 数据。
- 完整组织树、角色权限与多租户知识隔离。
- 管理员上传/编辑 UI/API、任意 URL 或文件系统路径摄取、PDF/DOCX/OCR 解析。
- cross-encoder reranker、在线反馈学习和大规模文档调度。
- 自动报告生成、导出与历史管理。
- 自由 SQL、任意文件系统访问或模型可写知识库。

## Technical Approach

1. 在 `knowledge.domain` 定义文档、chunk、query、hit、引用和失败语义；在 `knowledge.application` 定义摄取与检索 use case/port。
2. 建立共享的受控 Markdown/manifest 摄取管线：元数据校验、标题优先切片、稳定 id、版本替换和来源保留。
3. 采用端口隔离的双适配器：默认 local/test 使用确定性内存检索；`prod` 使用 Spring AI `PgVectorStore` 与独立 embedding 配置。
4. 将 `retrieveKnowledge` 增加到 P1-3 受控 Agent 注册表，通过 knowledge application port 执行，并复用 scope、预算与 trace。
5. 扩展业务知识意图识别与 prompt 约束：知识片段仅作解释上下文，结构化指标/规则事实优先；无命中时明确说明。
6. 以共用契约测试覆盖检索排序/过滤/引用，以 Agent/Chat 回归覆盖白名单、同步/SSE、fallback 和现有协议兼容。

## Decision (ADR-lite)

**Context**: 生产环境已采用 PostgreSQL + Flyway，但默认开发和测试依赖零外部服务的 H2；Spring AI 官方明确 `SimpleVectorStore` 只适合演示，而 PGvector 需要数据库扩展和 embedding 模型。

**Decision**: 首版采用 `knowledge` application port 隔离的双适配器。受控 Markdown、切片和引用契约保持一致；local/test 使用确定性内存检索，prod 使用 Spring AI PGvector 语义检索。首版不开放上传入口，也不引入 LangChain4j。

**Consequences**:

- 本地和 CI 可稳定回归，生产又不被 demo-only 存储锁死。
- 需要维护两种检索适配器和共用契约测试，但 Agent 与业务层无需感知差异。
- PGvector 上线前必须明确安装扩展、embedding 模型、维度和迁移/重建流程。
- 当前聊天模型、`@Tool` 和 request-scoped callbacks 已使用 Spring AI；继续复用可避免双框架并存或重写 P1-3。knowledge application port 保持框架无关，后续若检索评测证明有必要，可只替换 infrastructure adapter 为 LangChain4j。
- 未来加入 tenant filter、上传和重建索引时只扩展 knowledge 模块，不改变外部聊天协议。

## Implementation Plan

- 切片 1：knowledge 领域契约、manifest/Markdown 摄取、样例知识与确定性检索测试。
- 切片 2：PGvector/embedding 配置与适配边界、版本替换和失败语义测试。
- 切片 3：`retrieveKnowledge` 受控 Agent 工具、意图/prompt、同步/SSE 与 fallback 回归。
- 切片 4：配置/README/架构文档、全量质量门禁和提交计划。

## Technical Notes

- 任务目录：`.trellis/tasks/08-09-p1-4-rag-knowledge-base/`
- 路线图：`docs/07-生产化升级路线图.md`
- 模块边界：`docs/08-架构模块边界与包结构.md`
- P1-3 基线：`.trellis/tasks/archive/2026-08/08-09-p1-3-controlled-agent-system/prd.md`
- 主要候选改动区域：`backend/src/main/java/com/brand/agentpoc/knowledge/**`、`agent/application`、`agent/domain`、`agent/infrastructure`、相关配置与测试。

## Research References

- [`research/rag-storage-and-ingestion.md`](research/rag-storage-and-ingestion.md) — 对比 PGvector、SimpleVectorStore 与端口隔离双适配器，推荐兼顾离线回归和生产试点的方案。
