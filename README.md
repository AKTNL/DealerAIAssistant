# 经销商 AI 分析助手

面向汽车经销商集团运营场景的 AI 分析应用。系统支持用户用中文或英文自然语言查询经营数据，通过 SSE 流式返回 Markdown 分析报告，并在回答中展示分析进度、可见思考链、分析口径、数据来源、限制说明、置信度、数据表格、图表和追问问题。

## 当前交付状态

截至 2026-08-11，P0 MVP、P1 首版与 P2-1A 身份/RBAC/会话基础已完成：

| 阶段 | 状态 | 已交付范围 |
| --- | --- | --- |
| P1-1 持久化数据库与迁移 | 已完成 | PostgreSQL `prod` profile、Flyway schema、active batch 持久化语义 |
| P1-2 架构模块化 | 已完成 | 模块化单体边界、包结构和 Agent 模块迁移基线 |
| P1-3 受控 Agent | 已完成 | 白名单只读工具、scope/预算/trace 约束、同步与 SSE fallback |
| P1-4 RAG 知识库 | 已完成 | 受控 Markdown 知识、带引用检索、内存与 PGvector adapters |
| P1-5 自动报告 | 已完成 | 确定性 Markdown 报告草稿、HTTP/Agent 入口、内存与 JDBC 记录 |
| P2-1A 身份、RBAC 与会话 | 已完成 | 数据库用户、可配置角色、opaque access/refresh、管理 API、安全审计、统一业务授权 |

这里的“已完成”指相应代码、回归和文档范围。真实 PostgreSQL 凭据环境仍需部署时手工验收；组织树/数据范围、权限管理图形界面、多租户、报告 PDF/Word 导出、任意文档上传与知识隔离属于后续增强。

## 项目定位

本项目从“规则分析引擎 + 可选外部大模型”的 POC 演进为可持续试点的经销商经营分析应用：

- **未配置模型时**：系统使用内置规则引擎和 Excel 样板数据，直接生成可复现的经营分析结果。
- **配置模型后**：后端先生成事实锚点和 fallback 报告，再把这些 grounded reference 交给外部模型润色，并通过回答元数据暴露分析口径、数据限制和置信度，降低 KPI 被改写或幻觉扩散的风险。
- **业务知识问答**：随应用发布的受控 Markdown 资料覆盖 KPI 口径、销售 SOP、经销商政策和产品/活动规则；回答必须携带来源与版本，且知识片段不能覆盖结构化 KPI 事实。
- **前端体验**：登录后进入聊天工作台，可切换中英文，使用左侧快捷问题，也可配置模型连接并发送自定义问题；分析类回答顶部会展示范围、指标口径、来源、限制与置信度。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、markdown-it、highlight.js、Mermaid、Vitest |
| 后端 | Java 21、Spring Boot 3.4、Spring AI 1.0、Spring Web MVC |
| 数据 | 默认 H2 + Spring Data JPA；`prod` 使用 PostgreSQL + Flyway + PGvector；Apache POI |
| 通信 | REST、Server-Sent Events (SSE) |

## 核心能力

- 用户名/密码登录与可撤销 opaque 会话：`/api/auth/login`、`refresh`、`me`、`password`、`logout`
- 流式聊天：`POST /api/chat/stream` 返回 `step`、`analysis_metadata`、`progress`、`message`、`done`、`error` 事件
- 同步聊天：`POST /api/chat`
- 会话清理：`DELETE /api/chat/{sessionId}`
- 模型连接配置：后端可提供默认 `baseUrl`、`apiKey`、`model`，浏览器本地设置可随聊天请求发送并覆盖默认值
- 中英文输出：前端文案可切换，后端按用户消息语言生成回答
- Markdown 渲染：代码高亮、HTML 表格白名单渲染、Mermaid 图表渲染、空图表状态提示
- 思考时间线：分析过程以 `step` 事件流式推送（数据加载、过滤、计算、工具调用、模型思考、洞察），前端通过统一时间线面板展示
- 分析元数据：分析类回答正文前推送 `analysis_metadata`，用于展示分析范围、指标口径、数据来源、关键限制和高/中/低置信度
- 结构化数据 API：原始数据查询、指标聚合、分页详情查询
- 受控 RAG：本地/test 使用确定性内存检索，`prod` 使用 PGvector 语义检索；无命中时明确返回 no-match，不从常识补写制度内容

## 分析场景

系统面向需求文档定义的 6 类一级分析场景：

| 场景 | 中文标签 | 说明 |
| --- | --- | --- |
| `TARGET_ACHIEVEMENT` | 目标达成分析 | 对比目标销量与实际赢单数，计算门店或区域目标达成率 |
| `OPPORTUNITY_FUNNEL` | 商机漏斗与转化分析 | 统计商机阶段、赢单/丢单、高概率商机和转化表现 |
| `SALES_FOLLOW_UP` | 销售跟进分析 | 统计任务完成、逾期、积压和门店跟进效率 |
| `CAMPAIGN_PERFORMANCE` | 市场活动规划与效果分析 | 对比活动目标与实际商机产出，评估活动达成率 |
| `DEALER_BENCHMARK` | 经营对标分析 | 组合多门店指标，找出领先与落后门店的差距 |
| `LEAD_SOURCE` | 线索来源与自然流量趋势分析 | 按来源统计线索量、转化率和来源结构 |

分析回答通常包含：

- `## Conclusion`：结论摘要
- `## Data Support`：数据表格或图表
- `## Short Analysis`：原因拆解和行动建议
- `FOLLOW_UP_QUESTIONS:` / `追问：`：0-2 个可点击的后续问题；答案完整时可省略，证据不足或口径会改变结论时只保留必要追问

回答口径遵循“证据优先”：系统会区分样板数据已经支持的事实、需要验证的假设和当前数据缺口；低样本、零分母、字段缺失或图表被隐藏时，会在回答顶部和正文中明确说明限制。

## 项目结构

```text
.
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/brand/agentpoc/
│       │   │   ├── agent/            # 受控工具、请求 scope、Spring AI callbacks、回答守卫
│       │   │   ├── ai/               # Spring AI 工具、语言检测、提示词工厂
│       │   │   ├── config/           # 应用配置、API Key 过滤器、CORS
│       │   │   ├── controller/       # Auth、Chat、DataQuery、Analytics、ModelConfig API
│       │   │   ├── dto/              # request、response、metrics、detail DTO
│       │   │   ├── entity/           # Dealer、Opportunity、Campaign、Task、Target、Lead
│       │   │   ├── knowledge/        # 文档/切片合同、检索应用服务、内存与 PGvector adapters
│       │   │   ├── reporting/        # 报告类型、确定性 Markdown 草稿、记录与导出
│       │   │   ├── repository/       # Spring Data JPA Repository
│       │   │   └── service/          # 聊天、规则分析、数据查询、Excel 导入、会话记忆
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── Sample Data.xlsx
│       │       └── static/           # 前端构建产物输出目录
│       └── test/
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                      # auth、chat、modelConfig、client
│       ├── components/               # chat、layout、common 组件
│       ├── composables/              # useAuth、useChat、useModelSettings、useSseParser
│       ├── constants/                # 快捷问题流、localStorage key
│       ├── i18n/                     # 中英文文案
│       ├── utils/                    # Markdown、聊天和存储工具
│       └── views/                    # LoginView、ChatView
├── docs/                             # 设计文档与计划
└── mockservice/                      # 原始样板数据
```

## 快速开始

### 环境要求

- Java 21
- Maven 3.9+
- Node.js 24+

### 文档编码提示

README 使用 UTF-8 编码。若在 Windows PowerShell 中直接运行 `Get-Content README.md` 看到中文乱码，请指定编码读取：

```powershell
Get-Content -Raw -Encoding UTF8 README.md
```

### 1. 启动后端

PowerShell：

```powershell
cd backend
$env:APP_AUTH_BOOTSTRAP_USERNAME="admin"
$env:APP_AUTH_BOOTSTRAP_PASSWORD="temporary-password"
$env:APP_AUTH_BOOTSTRAP_DISPLAY_NAME="Local Administrator"
mvn "-Dfrontend.skip=true" spring-boot:run
```

Bash：

```bash
cd backend
export APP_AUTH_BOOTSTRAP_USERNAME="admin"
export APP_AUTH_BOOTSTRAP_PASSWORD="temporary-password"
export APP_AUTH_BOOTSTRAP_DISPLAY_NAME="Local Administrator"
mvn "-Dfrontend.skip=true" spring-boot:run
```

后端默认监听 `http://localhost:8081`，启动时会从 `classpath:Sample Data.xlsx` 导入样板数据到 H2 内存数据库。导入前会校验五个必需 Sheet，并按字段类型清洗空白、文本空值、数字、日期和分类值。缺失目标分母或预计关闭日期会保留为 `null`，不会伪造为 `0` 或推测日期。

本地/demo 模式默认允许在工作簿不可用时切换到内置样例数据，前端聊天区会显示明确警告；`prod` 配置关闭该回退，导入失败会阻止应用启动。登录后可通过 `GET /api/data-status` 查看数据来源、回退状态以及各 Sheet 的处理、导入、规范化、跳过和问题计数。

### 持久化数据库模式

生产形态使用 PostgreSQL + Flyway。Flyway 会在启动时执行 `backend/src/main/resources/db/migration/` 和 `backend/src/main/resources/db/postgresql/` 下尚未应用的迁移，生产环境的 Hibernate 只校验 schema，不自动修改表结构。

PowerShell 示例：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="prod"
$env:APP_DB_URL="jdbc:postgresql://localhost:5432/agentpoc"
$env:APP_DB_USERNAME="agentpoc"
$env:APP_DB_PASSWORD="change-me"
mvn "-Dfrontend.skip=true" spring-boot:run
```

`APP_DB_URL`、`APP_DB_USERNAME` 和 `APP_DB_PASSWORD` 必须指向可访问的 PostgreSQL 实例。迁移失败或数据库不可用时，`prod` 不会回退到 H2 或内置样例；本地快速开发仍使用默认 H2 配置。

### RAG 知识库模式

默认 profile 使用 `app.knowledge.vector-store=memory`：启动时校验 `backend/src/main/resources/knowledge/catalog.json`，确定性切分随应用发布的 Markdown，并建立无需 embedding 服务或 PostgreSQL 的本地索引。该路径适合开发和测试，不是生产语义检索实现。

`prod` 默认切换到 `pgvector`。上线前必须在目标 PostgreSQL 安装 `vector` 扩展，并通过 Spring AI 标准 OpenAI embedding 配置提供 `EmbeddingModel`。以默认 1536 维模型为例：

```powershell
$env:SPRING_AI_OPENAI_API_KEY="change-me"
$env:SPRING_AI_OPENAI_BASE_URL="https://api.openai.com"
$env:SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL="text-embedding-ada-002"
$env:APP_KNOWLEDGE_EMBEDDING_DIMENSIONS="1536"
```

embedding 模型输出维度必须与 `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS` 及 Flyway 建表语句中的 `VECTOR(...)` 一致。若要改变维度，必须新增前向迁移重建向量列/HNSW 索引并重新摄取知识；不要修改已经应用的迁移，也不要只改环境变量。`prod` 缺少 `EmbeddingModel`、PGvector 扩展、表结构或数据库连接时会明确启动失败，不会静默回退到内存索引。

### 2. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

前端默认监听 `http://localhost:5173`，Vite 会把 `/api` 请求代理到 `http://127.0.0.1:8081`。

### 3. 登录

打开 `http://localhost:5173`，使用初始化管理员用户名和临时密码登录。首次登录只允许查看当前身份、修改密码和退出；修改密码后旧会话会立即撤销，请使用新密码重新登录。初始化只在用户表为空时执行，重启不会覆盖已有账号或密码。

### 4. 配置模型连接

登录后点击右上角 `Settings`，填写：

- `Base URL`：兼容 OpenAI Chat Completions 的模型服务地址
- `API Key`：模型服务访问密钥
- `Model`：模型名称

点击 `Test Connection` 验证，通过后点击 `Save`。未保存模型配置时，经营分析类问题仍会由内置规则引擎回答。

## 配置

后端配置在 `backend/src/main/resources/application.yml`，支持环境变量覆盖：

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8081` | 后端服务端口 |
| `app.auth.access-token-ttl` | `APP_AUTH_ACCESS_TOKEN_TTL` | `30m` | opaque access token 有效期；数据库仅保存 SHA-256 摘要 |
| `app.auth.refresh-token-ttl` | `APP_AUTH_REFRESH_TOKEN_TTL` | `7d` | refresh token 有效期；原始值仅保存在 HttpOnly Cookie |
| `app.auth.cookie-secure` | `APP_AUTH_COOKIE_SECURE` | `false`（`prod` 为 `true`） | refresh Cookie 是否仅允许 HTTPS |
| `app.auth.cookie-same-site` | `APP_AUTH_COOKIE_SAME_SITE` | `Lax` | refresh Cookie 的 SameSite 策略 |
| `app.auth.bootstrap.required` | `APP_AUTH_BOOTSTRAP_REQUIRED` | `false`（`prod` 为 `true`） | 空用户库是否必须提供初始化管理员凭据 |
| `app.auth.bootstrap.username` | `APP_AUTH_BOOTSTRAP_USERNAME` | 空 | 仅在用户表为空时创建的初始化管理员用户名 |
| `app.auth.bootstrap.password` | `APP_AUTH_BOOTSTRAP_PASSWORD` | 空 | 初始化管理员临时密码；不会以明文持久化或记录日志 |
| `app.auth.bootstrap.display-name` | `APP_AUTH_BOOTSTRAP_DISPLAY_NAME` | `System Administrator` | 初始化管理员显示名 |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | CORS 允许来源，逗号分隔 |
| `app.excel.path` | `APP_EXCEL_PATH` | `classpath:Sample Data.xlsx` | 启动时导入的 Excel 数据源 |
| `app.excel.fallback-enabled` | `APP_EXCEL_FALLBACK_ENABLED` | `true`（`prod` 为 `false`） | 工作簿失败时是否允许使用内置样例数据；生产环境应关闭 |
| `app.model.base-url` | `APP_MODEL_BASE_URL` | 空 | 可选默认模型服务地址，兼容 OpenAI Chat Completions |
| `app.model.api-key` | `APP_MODEL_API_KEY` | 空 | 可选默认模型 API Key；不会写入日志 |
| `app.model.name` | `APP_MODEL_NAME` | 空 | 可选默认模型名称 |
| `app.model.allowed-hosts` | `APP_MODEL_ALLOWED_HOSTS` | 空 | 可选模型 Base URL 主机允许列表，支持 `api.example.com,*.example.com` |
| `app.model.allow-private-hosts` | `APP_MODEL_ALLOW_PRIVATE_HOSTS` | `false` | 是否允许模型 Base URL 指向 localhost 或内网地址 |
| `app.knowledge.vector-store` | `APP_KNOWLEDGE_VECTOR_STORE` | `memory`（`prod` 为 `pgvector`） | 知识检索 adapter；生产配置不可用时不会自动回退 |
| `app.knowledge.schema-name` | `APP_KNOWLEDGE_SCHEMA_NAME` | `public` | PGvector 表所在 schema，仅接受 SQL 标识符 |
| `app.knowledge.table-name` | `APP_KNOWLEDGE_TABLE_NAME` | `knowledge_vector_store` | PGvector 表名，仅接受 SQL 标识符 |
| `app.knowledge.dimensions` | `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS` | `1536` | embedding 维度，必须与模型和迁移表结构一致 |
| `app.knowledge.similarity-threshold` | `APP_KNOWLEDGE_SIMILARITY_THRESHOLD` | `0.45` | PGvector 余弦相似度阈值，范围 `0..1` |

安全边界说明：

- 登录、refresh、静态资源和健康检查显式公开；其余 `/api/**` 默认需要短期 opaque Bearer 会话。
- refresh token 只通过 HttpOnly Cookie 传输，并在每次刷新时轮换；旧 refresh token 重放会撤销整个会话族。前端对并发 401 只发起一个 refresh 请求。
- Dashboard、数据、Chat、知识、报告、模型测试和管理 API 分别检查固定权限键；权限和账号状态来自数据库，变更会即时撤销受影响会话。
- `ADMIN`、`ANALYST`、`VIEWER` 是幂等预置角色；自定义角色只能组合代码内固定的权限目录。系统拒绝停用或移除最后一个有效管理员的管理能力。
- 可通过 `APP_MODEL_BASE_URL`、`APP_MODEL_API_KEY`、`APP_MODEL_NAME` 配置后端默认模型连接；浏览器 `localStorage` 中的模型设置会随聊天请求发送，并优先覆盖后端默认值。
- 模型 `Base URL` 会拒绝 localhost、内网地址和未进入允许列表的主机，允许列表可通过 `APP_MODEL_ALLOWED_HOSTS` 配置。

## API 概览

### 认证与聊天

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/login` | POST | 用户名/密码登录，返回短期 access token 并设置 refresh Cookie |
| `/api/auth/refresh` | POST | 轮换 refresh token 并恢复 access token |
| `/api/auth/me` | GET | 返回当前用户、角色和权限 |
| `/api/auth/password` | POST | 修改密码并撤销该用户全部会话 |
| `/api/auth/logout` / `/api/auth/logout-all` | POST | 通过受 Origin 保护的 refresh Cookie 撤销当前会话族 / 通过 Bearer 撤销全部会话 |
| `/api/admin/users/**` | GET/POST/PATCH/PUT | 用户查询、创建、启停、重置密码与角色分配 |
| `/api/admin/roles/**` | GET/POST/PUT | 角色查询、创建和权限组合更新 |
| `/api/chat` | POST | 同步聊天 |
| `/api/chat/stream` | POST | SSE 流式聊天 |
| `/api/chat/{sessionId}` | DELETE | 清空指定会话记忆 |
| `/api/model-config/test` | POST | 测试模型连接配置 |
| `/api/data-status` | GET | 查看当前导入来源、样例回退状态和质量汇总 |

### 报告草稿 API

报告 API 复用 Dashboard 的 active-batch 指标服务，默认只允许 `GLOBAL` scope。草稿保存生成时间、数据批次、模型名和 prompt 版本，可直接导出 Markdown；PDF/Word 不在 P1-5 范围内。

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/reports/drafts` | POST | 生成日报、周报、月报或专题 Markdown 草稿 |
| `/api/reports/drafts` | GET | 列出当前运行实例可见的报告记录 |
| `/api/reports/drafts/{id}` | GET | 获取草稿及其批次、scope、模型和 prompt 版本 |
| `/api/reports/drafts/{id}/markdown` | GET | 以 `text/markdown` 下载草稿 |

生成请求示例：

```json
{
  "reportType": "weekly",
  "language": "zh",
  "scopeType": "GLOBAL",
  "topic": "本周经营概况与待跟进事项"
}
```

SSE 流式聊天事件类型：

| 事件 | 说明 |
| --- | --- |
| `step` | 分析步骤事件，包含 `type`（data_load/filter/calculation/tool_call/model_thought/insight）、`status`、`label`、`detail` 等字段 |
| `analysis_metadata` | 分析元数据事件，包含 `scenarioLabel`、`scopeLabel`、`metricLens`、`dataSources`、`limitations`、`confidence`，会在分析类回答正文前发送 |
| `progress` | 分析进度文本，前端渲染为加载占位步骤 |
| `message` | Markdown 文本块，模型思考内容通过 `<think>` 标签包裹 |
| `done` | 流式传输完成 |
| `error` | 错误信息 |

认证成功响应体：

```json
{
  "success": true,
  "sessionToken": "v1...",
  "expiresAt": "2026-05-21T16:00:00Z"
}
```

聊天请求体：

```json
{
  "sessionId": "demo-session",
  "message": "本月哪些经销商目标达成率最低？",
  "baseUrl": "https://example.com/v1",
  "apiKey": "sk-...",
  "model": "your-model-name"
}
```

### 原始数据查询

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/data/dealers` | GET | 经销商数据 |
| `/api/v1/data/opportunities` | GET | 商机数据 |
| `/api/v1/data/campaigns` | GET | 活动数据 |
| `/api/v1/data/tasks` | GET | 任务数据 |
| `/api/v1/data/targets` | GET | 目标数据 |
| `/api/v1/data/leads` | GET | 线索数据 |

这些接口接收查询参数 Map，由 `DataQueryService` 按数据集字段进行过滤。

### 指标与详情 API

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/targets/metrics` | GET | 目标达成聚合指标 |
| `/api/targets/details` | GET | 目标明细分页 |
| `/api/opportunities/metrics` | GET | 商机漏斗聚合指标 |
| `/api/opportunities/details` | GET | 商机明细分页 |
| `/api/leads/metrics` | GET | 线索来源聚合指标 |
| `/api/leads/details` | GET | 线索明细分页 |
| `/api/tasks/metrics` | GET | 跟进任务聚合指标 |
| `/api/tasks/details` | GET | 跟进任务明细分页 |
| `/api/campaigns/metrics` | GET | 活动效果聚合指标 |
| `/api/campaigns/details` | GET | 活动明细分页 |

详情接口通用分页参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `page` | `1` | 页码 |
| `pageSize` | `50` | 每页数量，最大 200 |
| `sortBy` | 各接口默认字段 | 排序字段 |
| `sortOrder` | `asc` 或 `desc` | 排序方向 |

## 构建与测试

### 前端

```bash
cd frontend
npm run lint
npm run test
npm run build
```

`npm run lint` 使用 ESLint flat config 检查 JavaScript/Vue 代码。`npm run build` 会先清理生成的 `assets/` 目录，再把构建产物输出到 `backend/src/main/resources/static`，同时保留后端维护的 `openapi.json`、`logo.png`、`background.jpg` 等静态文件。

### 后端

```bash
cd backend
mvn "-Dfrontend.skip=true" pmd:check
mvn "-Dfrontend.skip=true" test
mvn "-Dfrontend.skip=true" clean install
```

`pmd:check` 使用 `backend/config/pmd-ruleset.xml` 中的 PMD error-prone 基线规则。GitHub Actions 会先跑前端 lint 和后端 PMD，再跑测试与构建。

### 准确率题库回归

题库文件位于 `mockservice/DealerAIAssistant_准确率测试题库.xlsx`，样板数据位于 `mockservice/SampleData/Sample Data - 星曜汽车.xlsx`。题库用于覆盖目标达成、商机漏斗、线索分析、边界问题和数据概况等自然语言查询，重点验证规则引擎在未配置外部模型时的可复现回答。

达成率题目采用可比样本口径：只有分母和配对分子都可用的行才参与比率计算，但总赢单、总商机等观测值仍保留全部有效行。回答和指标 API 会同时给出总观测值与可比样本分子，避免把部分目标与全部实际数直接相除。

完整后端回归可直接运行 `mvn "-Dfrontend.skip=true" test`。若只想快速验证题库和规则引擎相关逻辑，可运行：

```bash
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=AccuracyWorkbookRegressionTest,RuleBasedAnalyticsServiceTest,ChatServiceTest,DirectQuestionMatcherTest" test
```

如需人工抽查，可启动前后端后按题库逐条提问；未保存模型配置时，系统会走内置规则引擎 fallback 路径，适合验证题库准确率。

### 后端打包时构建前端

```bash
cd backend
mvn clean install
```

默认 Maven 构建会在 `generate-resources` 阶段执行前端的 `npm ci --no-audit --no-fund` 和 `npm run build`。Windows 环境下会自动使用 `npm.cmd`。

## 关键实现文件

| 文件 | 职责 |
| --- | --- |
| `backend/src/main/java/com/brand/agentpoc/service/ChatService.java` | 聊天主流程、SSE 输出、step 与 analysis_metadata 事件流式推送、模型与规则引擎分流 |
| `backend/src/main/java/com/brand/agentpoc/service/SseEventWriter.java` | SSE 事件写入工具，负责分析元数据和 Markdown 分块事件输出 |
| `backend/src/main/java/com/brand/agentpoc/service/AnalyticsMetadata.java` | 分析元数据 record，描述场景、范围、指标口径、数据来源、限制和置信度 |
| `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java` | 规则分析引擎、fallback 报告生成、实时 step 回调 |
| `backend/src/main/java/com/brand/agentpoc/service/analytics/AnalyticsTopicClassifier.java` | 纯文本分析主题识别，维护场景路由优先级 |
| `backend/src/main/java/com/brand/agentpoc/service/StepEvent.java` | SSE step 事件 record（traceId、seq、type、status、label、detail、meta） |
| `backend/src/main/java/com/brand/agentpoc/service/StepType.java` | step 类型枚举（data_load/filter/calculation/tool_call/model_thought/insight） |
| `backend/src/main/java/com/brand/agentpoc/service/AnalyticsScenarioCatalog.java` | 分析场景目录、示例问题、工具链说明 |
| `backend/src/main/java/com/brand/agentpoc/service/AnalyticsApiService.java` | 指标聚合与详情分页 API 逻辑 |
| `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java` | 系统提示词、thinking_protocol、证据边界和 0-2 个追问约束 |
| `backend/src/main/java/com/brand/agentpoc/agent/ChatReplyGuard.java` | 模型回答守卫，修正过度确定、追问数量和结构化回答格式 |
| `backend/src/main/java/com/brand/agentpoc/agent/infrastructure/ControlledAgentToolCallbacks.java` | 为已认证请求注册六个受控业务工具，并共享单请求四次调用预算 |
| `backend/src/main/java/com/brand/agentpoc/knowledge/application/KnowledgeService.java` | 框架中立的知识检索应用入口，校验 query/Top-K 并返回引用信息 |
| `backend/src/main/java/com/brand/agentpoc/knowledge/infrastructure/KnowledgeBootstrap.java` | 校验知识目录、确定性切片并在启动时替换 bundled catalog 索引 |
| `backend/src/main/java/com/brand/agentpoc/knowledge/infrastructure/PgVectorKnowledgeIndex.java` | 生产 PGvector 语义检索 adapter |
| `backend/src/main/java/com/brand/agentpoc/reporting/application/ReportService.java` | 复用 Dashboard active-batch 快照生成并记录报告草稿 |
| `backend/src/main/java/com/brand/agentpoc/reporting/infrastructure/JdbcReportDraftStore.java` | 生产报告记录 JDBC adapter；local/test 使用内存 adapter |
| `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java` | Excel 字段级清洗、必需 Sheet 校验、严格/样例回退导入 |
| `backend/src/main/java/com/brand/agentpoc/service/ImportQualityService.java` | 保存最近一次导入来源和质量汇总 |
| `backend/src/main/java/com/brand/agentpoc/controller/DataStatusController.java` | 登录态数据质量状态接口 |
| `backend/src/main/java/com/brand/agentpoc/auth/` | 用户身份、RBAC、opaque access/refresh 会话、管理 API 与安全审计 |
| `frontend/src/composables/useChat.js` | 前端聊天状态、SSE 解析（step/analysis_metadata/progress/message/done/error）、`<think>` 标签流式解析、streamPhase 管理 |
| `frontend/src/utils/markdown.js` | Markdown、HTML 表格、Mermaid fence 渲染 |
| `frontend/src/components/chat/AssistantMessage.vue` | AI 消息、分析口径横幅、统一时间线面板、追问按钮和 Mermaid 图表交互 |
| `frontend/src/components/layout/ModelSettingsPanel.vue` | 模型连接配置面板 |
| `frontend/src/constants/sidebarFlows.js` | 左侧快捷问题配置 |

## 开发注意事项

- 默认开发模式使用 H2 内存数据库，应用重启后会重新导入 Excel 样板数据；`prod` 使用 PostgreSQL 持久化数据，并由 Flyway 管理 schema。
- `0` 只表示源数据确认的真实零值；缺失数值保留为 `null`，未知分类使用“未知”或“未分配”。
- 目标/活动达成率只使用可比样本计算，总观测值与可比样本值必须分别展示。
- 规则引擎输出数据来自样板数据或聚合计算，外部模型只负责在事实锚点基础上润色。
- RAG 只保存业务制度、口径、SOP 和产品/活动知识；当前 KPI、排名、明细和 active batch 事实仍由结构化服务提供。
- 分析元数据由后端生成，前端只渲染 `analysis_metadata` 事件提供的字段，不从 Markdown 正文反推业务口径。
- 前端开发时优先通过 Vite 代理访问后端；如果直接部署后端静态资源，则访问 `http://localhost:8081`。
- 本地启动也需要显式设置访问密钥、session 签名密钥和内部 API key；不要提交真实密钥。
