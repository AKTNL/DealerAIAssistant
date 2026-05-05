# 经销商 AI 分析平台

一个面向经销商经营分析场景的 Agent POC 项目。仓库采用 `Vue 3 + Vite` 前端和 `Spring Boot 3` 后端，当前已经打通登录、聊天、数据查询和前后端联调的主链路。

## 当前状态

目前这份代码已经具备以下能力：

- 登录页支持中英文切换、口令校验、加载态、失败提示、输入框抖动和 `sessionStorage` 认证标记
- 前端聊天工作台支持 SSE 流式响应、`<think>` 展示、追问提取和会话清空
- 后端已提供真实的 H2 + JPA 数据实体与查询接口，不再是空占位返回
- 聊天接口支持“双轨模式”：
  - 配好 `OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL` 时，直接调用兼容 OpenAI 的模型
  - 未配置模型时，分析类问题回退到内置样例数据的规则分析，普通聊天返回配置指引
- 启动时会优先尝试读取 `APP_EXCEL_PATH` 指向的 Excel；如果文件不存在或解析失败，会自动回退到内置样例数据
- 前端构建产物会输出到 `backend/src/main/resources/static`，可由后端统一托管

当前规则分析支持的主题包括：

- 目标达成率
- 商机漏斗
- 跟进任务
- 营销活动表现
- 线索来源
- 经销商对标

## 技术栈

- 前端：`Vue 3`、`Vite`
- 后端：`Java 21`、`Spring Boot 3.4.x`
- 数据：`H2`、`Spring Data JPA`、`Apache POI`
- 模型接入：`Spring AI 1.0.0`
- 通信：`HTTP + SSE`

## 目录结构

```text
.
├─ frontend/
│  ├─ public/
│  ├─ src/
│  │  ├─ api/
│  │  ├─ components/
│  │  ├─ composables/
│  │  ├─ constants/
│  │  ├─ i18n/
│  │  ├─ utils/
│  │  └─ views/
├─ backend/
│  └─ src/main/
│     ├─ java/com/brand/agentpoc/
│     │  ├─ ai/
│     │  ├─ config/
│     │  ├─ controller/
│     │  ├─ dto/
│     │  ├─ entity/
│     │  ├─ repository/
│     │  └─ service/
│     └─ resources/
├─ Agent-POC开发文档.md
└─ README.md
```

## 运行方式

### 环境要求

- `Java 21`
- `Maven 3.9+`
- `Node.js 18+`
- `npm`

### 方式一：前后端分开开发

后端：

```bash
cd backend
mvn -s settings.xml "-Dfrontend.skip=true" spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

启动后可访问：

- 前端开发地址：`http://127.0.0.1:5173` 或 `http://localhost:5173`
- 后端地址：`http://127.0.0.1:8081`
- H2 Console：`http://127.0.0.1:8081/h2-console`

说明：

- Vite 会将 `/api` 代理到 `http://127.0.0.1:8081`
- `-Dfrontend.skip=true` 表示跳过 Maven 生命周期中的 `npm ci` 和前端构建步骤

### 方式二：构建一体化产物

先构建前端：

```bash
cd frontend
npm install
npm run build
```

再构建后端：

```bash
cd backend
mvn -s settings.xml "-Dfrontend.skip=true" clean package
```

运行 Jar：

```bash
cd backend
java -jar target/agent-poc-backend-0.0.1-SNAPSHOT.jar
```

运行后可直接访问：`http://127.0.0.1:8081`

## 配置项

主配置文件位于 `backend/src/main/resources/application.yml`。

至少支持以下配置项：

- `SERVER_PORT`：服务端口，默认 `8081`
- `APP_ACCESS_KEY`：访问口令，默认 `demo123`
- `APP_API_KEY`：报表接口 `X-API-Key`，默认 `poc-api-key`
- `OPENAI_API_KEY`：OpenAI 兼容模型 API Key
- `OPENAI_BASE_URL`：OpenAI 兼容模型 Base URL，默认 `https://api.openai.com`
- `OPENAI_MODEL`：OpenAI 兼容模型名称，默认 `gpt-4o-mini`
- `APP_EXCEL_PATH`：Excel 样例数据路径，默认 `classpath:Sample Data.xlsx`

说明：

- 仓库当前未包含真实 Excel 文件
- 如果 `APP_EXCEL_PATH` 对应文件不可用，系统会自动写入内置样例数据，保证项目可运行
- 只要配置好 `OPENAI_API_KEY`、`OPENAI_BASE_URL` 和 `OPENAI_MODEL`，聊天接口就会直接调用你自己的兼容模型
- 仓库根目录额外提供了 `.env.example`，方便快速查看这 7 个配置项的最小示例

## 接口概览

### 认证与聊天

- `POST /api/auth/verify`：校验登录口令
- `POST /api/chat`：同步聊天接口，返回 `{"reply":"...markdown..."}` 
- `POST /api/chat/stream`：SSE 流式聊天接口
- `DELETE /api/chat/{sessionId}`：清空会话

`/api/chat/stream` 当前会输出这些事件：

- `progress`
- `message`
- `done`

### 数据查询

`/api/auth/**` 和 `/api/chat/**` 已加入白名单，不需要 `X-API-Key`。

以下接口需要请求头：

```text
X-API-Key: poc-api-key
```

接口列表：

- `GET /api/v1/data/dealers`
- `GET /api/v1/data/opportunities`
- `GET /api/v1/data/campaigns`
- `GET /api/v1/data/tasks`
- `GET /api/v1/data/targets`
- `GET /api/v1/data/leads`

响应结构统一为：

```json
{
  "dataset": "targets",
  "filters": {
    "city": "Beijing"
  },
  "totalCount": 0,
  "items": []
}
```

常见筛选参数示例：

- `dealers`：`keyword`、`dealerCode`、`city`、`dealerGroupName`
- `opportunities`：`dealerCode`、`city`、`dealerGroupName`、`productModel`、`stageName`、`leadSource`、`startDate`、`endDate`
- `campaigns`：`dealerCode`、`city`、`dealerGroupName`、`productModel`、`campaignType`、`startDate`、`endDate`
- `tasks`：`dealerCode`、`city`、`dealerGroupName`、`opportunityId`、`status`、`startDate`、`endDate`
- `targets`：`dealerCode`、`city`、`dealerGroupName`、`productModel`、`targetYear`、`targetMonth`
- `leads`：`dealerCode`、`city`、`dealerGroupName`、`leadSource`、`stageName`、`productModel`、`isConverted`、`startDate`、`endDate`

## 示例请求

同步聊天：

```bash
curl -X POST http://127.0.0.1:8081/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"demo-session\",\"message\":\"本月哪些经销商目标达成率最低？\"}"
```

流式聊天：

```bash
curl -N -X POST http://127.0.0.1:8081/api/chat/stream \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"demo-session\",\"message\":\"Which dealers have the lowest target achievement this month?\"}"
```

数据查询：

```bash
curl "http://127.0.0.1:8081/api/v1/data/targets?city=Beijing" \
  -H "X-API-Key: poc-api-key"
```

## 已验证内容

已在当前仓库完成以下验证：

- `frontend` 执行 `npm run build` 通过
- `backend` 执行 `mvn -s settings.xml "-Dfrontend.skip=true" clean install` 通过
- `GET /actuator/health` 返回 `200`
- 未配置模型时，`POST /api/chat` 的普通聊天会返回配置指引
- 未配置模型时，`POST /api/chat` 的分析类问题会回退到内置样例数据分析
- `POST /api/chat/stream` 按 `progress -> message -> done` 顺序返回 SSE 事件

## 当前限制

- 规则分析回复目前基于样例数据和启发式判断，不是真正的大模型推理
- Excel 解析已经接入，但仍依赖实际样例文件列名与当前解析规则匹配
- 还没有自动化测试覆盖聊天分析和 Excel 导入
- 前端 Markdown 渲染仍是轻量实现

## 下一步建议

1. 先补一份可直接复制的模型配置模板，方便你接自己的兼容模型
2. 接着用真实 `OPENAI_API_KEY / BASE_URL / MODEL` 实测同步和流式聊天
3. 等拿到 `Sample Data.xlsx` 后，再把真实报表数据接入分析链路
4. 后续再考虑做前端“模型配置面板”，让你不改环境变量也能切模型
