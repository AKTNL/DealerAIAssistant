# Agent POC - 星曜汽车 AI 分析助手

面向汽车经销商集团运营场景的 AI 分析助手 POC。系统支持用户用中文或英文自然语言查询经营数据，通过 SSE 流式返回 Markdown 分析报告，并在回答中展示分析进度、可见思考链、数据表格、图表和追问问题。

## 项目定位

本项目用于验证“规则分析引擎 + 可选外部大模型”的经销商经营分析体验：

- **未配置模型时**：系统使用内置规则引擎和 Excel 样板数据，直接生成可复现的经营分析结果。
- **配置模型后**：后端先生成事实锚点和 fallback 报告，再把这些 grounded reference 交给外部模型润色，降低 KPI 被改写或幻觉扩散的风险。
- **前端体验**：登录后进入聊天工作台，可切换中英文，使用左侧快捷问题，也可配置模型连接并发送自定义问题。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、markdown-it、highlight.js、Mermaid、Vitest |
| 后端 | Java 21、Spring Boot 3.4、Spring AI 1.0、Spring Web MVC |
| 数据 | H2 内存数据库、Spring Data JPA、Apache POI |
| 通信 | REST、Server-Sent Events (SSE) |

## 核心能力

- 访问密钥登录：`POST /api/auth/verify`
- 流式聊天：`POST /api/chat/stream` 返回 `step`、`progress`、`message`、`done`、`error` 事件
- 同步聊天：`POST /api/chat`
- 会话清理：`DELETE /api/chat/{sessionId}`
- 浏览器本地模型配置：`baseUrl`、`apiKey`、`model` 随聊天请求发送
- 中英文输出：前端文案可切换，后端按用户消息语言生成回答
- Markdown 渲染：代码高亮、HTML 表格白名单渲染、Mermaid 图表渲染、空图表状态提示
- 思考时间线：分析过程以 `step` 事件流式推送（数据加载、过滤、计算、工具调用、模型思考、洞察），前端通过统一时间线面板展示
- 结构化数据 API：原始数据查询、指标聚合、分页详情查询

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

规则分析回答通常包含：

- `## Conclusion`：结论摘要
- `## Data Support`：数据表格或图表
- `## Short Analysis`：原因拆解和行动建议
- `FOLLOW_UP_QUESTIONS:` / `追问：`：两个可点击的后续问题

## 项目结构

```text
.
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/brand/agentpoc/
│       │   │   ├── ai/               # Spring AI 工具、语言检测、提示词工厂
│       │   │   ├── config/           # 应用配置、API Key 过滤器、CORS
│       │   │   ├── controller/       # Auth、Chat、DataQuery、Analytics、ModelConfig API
│       │   │   ├── dto/              # request、response、metrics、detail DTO
│       │   │   ├── entity/           # Dealer、Opportunity、Campaign、Task、Target、Lead
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

```bash
cd backend
export APP_ACCESS_KEY="change-me-login-key"
export APP_SESSION_SECRET="change-me-session-secret-at-least-32-chars"
export APP_API_KEY="change-me-internal-api-key"
mvn "-Dfrontend.skip=true" spring-boot:run
```

后端默认监听 `http://localhost:8081`，启动时会从 `classpath:Sample Data.xlsx` 导入样板数据到 H2 内存数据库。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，Vite 会把 `/api` 请求代理到 `http://127.0.0.1:8081`。

### 3. 登录

打开 `http://localhost:5173`，输入你通过 `APP_ACCESS_KEY` 配置的访问密钥。

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
| `app.auth.access-key` | `APP_ACCESS_KEY` | 空 | 前端登录访问密钥；为空时登录校验失败 |
| `app.auth.session-secret` | `APP_SESSION_SECRET` | 空 | session token HMAC 签名密钥；必须显式配置后才能签发 token |
| `app.auth.session-ttl` | `APP_SESSION_TTL` | `8h` | 登录 session token 有效期 |
| `app.security.api-key` | `APP_API_KEY` | 空 | 受保护后端接口的 `X-API-Key`；为空时内部 API key 校验失败 |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | CORS 允许来源，逗号分隔 |
| `app.excel.path` | `APP_EXCEL_PATH` | `classpath:Sample Data.xlsx` | 启动时导入的 Excel 数据源 |
| `app.model.allowed-hosts` | `APP_MODEL_ALLOWED_HOSTS` | 空 | 可选模型 Base URL 主机允许列表，支持 `api.example.com,*.example.com` |
| `app.model.allow-private-hosts` | `APP_MODEL_ALLOW_PRIVATE_HOSTS` | `false` | 是否允许模型 Base URL 指向 localhost 或内网地址 |

安全边界说明：

- `/api/auth/**`、静态资源、H2 Console 和健康检查保持演示白名单行为。
- `/api/chat/**` 与 `/api/model-config/**` 需要登录后返回的 `Authorization: Bearer <sessionToken>`。
- `/api/v1/data/**` 与 `/api/*/metrics`、`/api/*/details` 仍需要请求头 `X-API-Key`。
- 模型 API Key 保存在浏览器 `localStorage` 中，适合本地演示，不适合作为生产密钥托管方案。
- 模型 `Base URL` 会拒绝 localhost、内网地址和未进入允许列表的主机，允许列表可通过 `APP_MODEL_ALLOWED_HOSTS` 配置。

## API 概览

### 认证与聊天

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/verify` | POST | 校验访问密钥，成功时返回 `sessionToken` 和 `expiresAt` |
| `/api/chat` | POST | 同步聊天 |
| `/api/chat/stream` | POST | SSE 流式聊天 |
| `/api/chat/{sessionId}` | DELETE | 清空指定会话记忆 |
| `/api/model-config/test` | POST | 测试模型连接配置 |

SSE 流式聊天事件类型：

| 事件 | 说明 |
| --- | --- |
| `step` | 分析步骤事件，包含 `type`（data_load/filter/calculation/tool_call/model_thought/insight）、`status`、`label`、`detail` 等字段 |
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
npm run test
npm run build
```

`npm run build` 会把构建产物输出到 `backend/src/main/resources/static`。

### 后端

```bash
cd backend
mvn "-Dfrontend.skip=true" test
mvn "-Dfrontend.skip=true" clean install
```

### 准确率题库回归

题库文件位于 `mockservice/DealerAIAssistant_准确率测试题库.xlsx`，样板数据位于 `mockservice/SampleData/Sample Data - 星曜汽车.xlsx`。题库用于覆盖目标达成、商机漏斗、线索分析、边界问题和数据概况等自然语言查询，重点验证规则引擎在未配置外部模型时的可复现回答。

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
| `backend/src/main/java/com/brand/agentpoc/service/ChatService.java` | 聊天主流程、SSE 输出、step 事件流式推送、模型与规则引擎分流 |
| `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java` | 规则分析引擎、fallback 报告生成、实时 step 回调 |
| `backend/src/main/java/com/brand/agentpoc/service/StepEvent.java` | SSE step 事件 record（traceId、seq、type、status、label、detail、meta） |
| `backend/src/main/java/com/brand/agentpoc/service/StepType.java` | step 类型枚举（data_load/filter/calculation/tool_call/model_thought/insight） |
| `backend/src/main/java/com/brand/agentpoc/service/AnalyticsScenarioCatalog.java` | 分析场景目录、示例问题、工具链说明 |
| `backend/src/main/java/com/brand/agentpoc/service/AnalyticsApiService.java` | 指标聚合与详情分页 API 逻辑 |
| `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java` | 系统提示词、thinking_protocol 和追问约束 |
| `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java` | Excel 样板数据导入 |
| `backend/src/main/java/com/brand/agentpoc/config/ApiKeyFilter.java` | 受保护接口的 `X-API-Key` 校验 |
| `frontend/src/composables/useChat.js` | 前端聊天状态、SSE 解析（step/progress/message/done/error）、`<think>` 标签流式解析、streamPhase 管理 |
| `frontend/src/utils/markdown.js` | Markdown、HTML 表格、Mermaid fence 渲染 |
| `frontend/src/components/chat/AssistantMessage.vue` | AI 消息、统一时间线面板、追问按钮和 Mermaid 图表交互 |
| `frontend/src/components/layout/ModelSettingsPanel.vue` | 模型连接配置面板 |
| `frontend/src/constants/sidebarFlows.js` | 左侧快捷问题配置 |

## 开发注意事项

- H2 使用内存数据库，应用重启后会重新导入 Excel 样板数据。
- 规则引擎输出数据来自样板数据或聚合计算，外部模型只负责在事实锚点基础上润色。
- 前端开发时优先通过 Vite 代理访问后端；如果直接部署后端静态资源，则访问 `http://localhost:8081`。
- 本地启动也需要显式设置访问密钥、session 签名密钥和内部 API key；不要提交真实密钥。
