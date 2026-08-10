# P1-4 RAG 存储与摄取方案研究

## 研究问题

如何在当前 Spring Boot 3.4 / Spring AI 1.0 / 本地 H2 + 生产 PostgreSQL 架构中，交付既能离线回归、又保留试点生产路径的首版 RAG 知识库。

## 仓库约束

- 默认开发模式依赖 H2 内存库并在启动时载入样例数据；`prod` 使用 PostgreSQL + Flyway。
- P1-3 已建立受控 Agent 工具白名单、request scope、调用预算、最小 trace 和规则 fallback。
- 路线图要求知识结果包含来源和版本、无命中时明确说明，且不得改写结构化 KPI。
- `knowledge` 是独立 owning module；`agent` 只能依赖其 application port。
- 当前没有管理员后台、组织树、多租户隔离或文件上传治理。

## 对比方案

### A. 所有环境直接使用 PGvector

- Spring AI 1.0 提供 `spring-ai-starter-vector-store-pgvector`，支持余弦距离、HNSW/IVFFlat、metadata filter。
- 生产路径直接、语义检索完整，来源/版本可保存在 metadata 中。
- PostgreSQL 必须启用 `vector`、`hstore`、`uuid-ossp` 扩展，并提供 embedding 模型；维度变化还要求重建向量表。
- 会破坏仓库当前“默认 H2 即可本地启动”的体验，也让普通单元/启动测试依赖外部基础设施。

### B. 所有环境使用 Spring AI `SimpleVectorStore`

- 接入成本低，可保存/加载 JSON 快照，适合 demo 和测试。
- Spring AI 官方明确标注它只适合测试或演示，不适合生产。
- 仍需要 embedding 模型，且缺少数据库级并发、迁移、索引和租户过滤能力，不符合 P1 试点方向。

### C. 端口隔离的双适配器（推荐）

- `knowledge.application` 定义摄取/检索端口和稳定结果 DTO；Agent 不感知底层检索实现。
- 默认 local/test 使用仓库内受控 Markdown 目录和确定性内存检索，保证无外部模型/数据库也能稳定回归来源、版本、Top-K、无命中和引用行为。
- `prod` 适配 Spring AI `PgVectorStore` + 单独的 embedding 配置，使用 metadata 保存 `documentId`、`source`、`version`、`section`、`chunkIndex`、`scopeType`；用 Flyway/显式配置管理数据库前置条件。
- 摄取流程共享资源发现、校验、切片、稳定 chunk id 和版本替换语义；只替换索引适配器。
- 代价是维护两个 adapter，但模块端口和共用契约测试可以限制漂移；未来上传、租户过滤和重建索引无需修改 Agent 工具协议。

## 摄取与引用约定

1. 首版只摄取随应用发布、经过代码审查的 Markdown 文档，不开放任意路径、URL 或管理员上传 API。
2. 用 manifest 或受控元数据明确记录 `documentId`、标题、知识类型、版本和资源路径；拒绝缺失/重复标识与空内容。
3. 按标题边界优先、最大 token/字符上限兜底切片；chunk id 由 `documentId + version + section + index` 稳定生成。
4. 检索请求限制 query 长度与 Top-K；空查询、非法 Top-K、不可用索引明确失败，不回传底层异常和敏感配置。
5. 每个命中返回 excerpt、source、version、section、chunk id 和 score；模型提示中明确“仅作解释上下文，结构化事实优先”。
6. 无命中返回显式 `noMatch` 语义；不得让模型依据常识伪造制度、口径或政策。

## Agent 集成影响

- 新增 `retrieveKnowledge` 到 `AgentToolName` 和受控 adapter；复用现有 session ownership、白名单、调用预算和 trace。
- `ControlledAgentToolService` 通过 knowledge application port 调用检索，不直接引用 VectorStore、JPA repository 或资源加载器。
- 现有回调注册实现要求 enum 与 `@Tool` 方法一一对应，因此新增工具必须同时更新 adapter、索引校验与测试。
- 当前 `ChatService` 只给“经营分析”请求挂载工具；业务制度/SOP/口径问题的意图边界和 prompt 必须同步扩展，否则新工具即使注册也不会被调用。

## 推荐 MVP 边界

- 包含：4 类受控样例知识、启动摄取、确定性 local/test 检索、生产 PgVector 适配边界、引用、无命中、受控 Agent 集成和完整自动化测试。
- 预留：tenant/scope metadata、替换版本、重建索引、管理员摄取 application use case。
- 不包含：上传 UI/API、任意 URL/文件摄取、PDF/DOCX 解析、OCR、租户后台、自动报告、线上知识编辑和 reranker。

## Spring AI 与 LangChain4j 判断

- 当前仓库已经用 Spring AI 1.0 实现动态聊天客户端、`@Tool` 适配、request-scoped callbacks 和 P1-3 受控 Agent。LangChain4j 若只用于 RAG，会形成两套模型、工具和文档抽象；若全面替换，则会扩大为 P1-3 重写和聊天协议回归任务。
- P1-4 所需的 Document/ETL、EmbeddingModel、VectorStore 和 PGvector 能力在 Spring AI 中已有对应接口，没有发现必须切换框架才能满足的验收项。
- 决策是继续使用 Spring AI infrastructure，同时让 `knowledge.application` 和 `knowledge.domain` 不引用 Spring AI 类型。这样未来只有在离线检索评测、供应商兼容或运维约束给出明确证据时，才需要新增或替换 LangChain4j adapter，无需改变 Agent 工具与业务契约。

## 来源

- 项目路线图：`docs/07-生产化升级路线图.md`，RAG 范围与验收约束。
- 项目模块边界：`docs/08-架构模块边界与包结构.md`。
- Spring AI 1.0 Vector Database API：`https://docs.spring.io/spring-ai/reference/1.0/api/vectordbs.html`。
- Spring AI 1.0 PGvector：`https://docs.spring.io/spring-ai/reference/1.0/api/vectordbs/pgvector.html`。
- Spring AI 1.0 ETL Pipeline：`https://docs.spring.io/spring-ai/reference/1.0/api/etl-pipeline.html`。
