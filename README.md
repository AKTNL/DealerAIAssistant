# 经销商 AI 分析平台

一个面向经销商经营分析场景的 Agent POC 项目。仓库采用 `Vue 3 + Vite` 前端和 `Spring Boot 3 + Spring AI` 后端，目标是逐步落地一个支持口令登录、SSE 流式对话、会话管理、数据查询和 AI 分析的单页 Web 应用。

当前项目已经打通了前后端基础链路，并完成了登录页、聊天工作台和流式对话的骨架实现；Excel 导入、真实数据查询、模型调用和 Tool 分析能力仍在后续开发中。

## 当前进度

### 已完成

- 访问口令登录页与 `sessionStorage` 认证标记
- 中英文界面切换
- 登录失败提示、输入框清空与抖动反馈
- 单会话聊天工作台
- `/api/chat/stream` 的 SSE 流式链路
- `<think>` 思路区展示与 follow-up 提取
- 会话清空与本地 `sessionId` 管理
- 前端构建产物输出到后端 `static/` 目录
- 后端基础配置、认证接口与 API Key 过滤骨架

### 待完成

- Excel 样例数据导入
- H2 表结构与真实查询逻辑
- `/api/v1/data/*` 数据接口真实返回
- Spring AI 模型接入
- Tool 调用与业务分析链路
- 更完整的 Markdown 渲染与代码高亮
- 自动化测试与交付文档补全

## 技术栈

- 前端：`Vue 3`、`Vite`
- 后端：`Java 21`、`Spring Boot 3.4.x`
- AI：`Spring AI 1.0.0`
- 数据：`H2`、`Spring Data JPA`、`Apache POI`
- 通信：`HTTP + SSE`

## 目录结构

```text
.
├─ frontend/                         # Vue 3 前端
│  ├─ public/
│  └─ src/
│     ├─ api/
│     ├─ components/
│     ├─ composables/
│     ├─ constants/
│     ├─ i18n/
│     ├─ utils/
│     └─ views/
├─ backend/                          # Spring Boot 后端
│  └─ src/main/
│     ├─ java/com/brand/agentpoc/
│     │  ├─ ai/
│     │  ├─ config/
│     │  ├─ controller/
│     │  ├─ dto/
│     │  └─ service/
│     └─ resources/
├─ Agent-POC开发文档.md
└─ README.md
```

## 快速开始

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

启动后访问：

- 前端开发服务：`http://localhost:5173`
- 后端服务：`http://localhost:8081`

说明：

- Vite 会将 `/api` 代理到 `http://127.0.0.1:8081`
- `-Dfrontend.skip=true` 表示启动后端时跳过 Maven 生命周期中的前端构建步骤

### 方式二：构建一体化产物

先构建前端静态资源：

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

运行后可直接通过 `http://localhost:8081` 访问页面。

## 配置说明

后端主配置文件位于：

- `backend/src/main/resources/application.yml`

当前常用环境变量：

- `APP_ACCESS_KEY`：访问口令，默认 `demo123`
- `APP_API_KEY`：数据接口 API Key，默认 `poc-api-key`
- `APP_EXCEL_PATH`：Excel 路径，默认 `classpath:Sample Data.xlsx`
- `OPENAI_API_KEY`：模型服务 API Key
- `OPENAI_BASE_URL`：模型服务地址，默认 `https://api.openai.com`
- `OPENAI_MODEL`：模型名称，默认 `gpt-4o-mini`

说明：

- 当前聊天服务仍为占位实现，尚未真实调用模型
- 当前仓库未包含 `Sample Data.xlsx`
- `APP_EXCEL_PATH` 现阶段主要为后续 Excel 导入预留

## 接口概览

### 认证与聊天接口

- `POST /api/auth/verify`：校验访问口令
- `POST /api/chat`：同步聊天接口，当前返回占位内容
- `POST /api/chat/stream`：SSE 流式聊天接口
- `DELETE /api/chat/{sessionId}`：清空当前会话

### 数据接口

- `GET /api/v1/data/dealers`
- `GET /api/v1/data/opportunities`
- `GET /api/v1/data/campaigns`
- `GET /api/v1/data/tasks`
- `GET /api/v1/data/targets`
- `GET /api/v1/data/leads`

说明：

- `/api/auth/**` 和 `/api/chat/**` 已加入白名单，不需要 `X-API-Key`
- `/api/v1/data/**` 受 `ApiKeyFilter` 保护，手动调试时需要携带：

```text
X-API-Key: poc-api-key
```

- 当前 `DataQueryService` 仍返回空结果集

## 当前限制

- `ChatService` 目前输出的是占位分析结果，不是真实 AI 回复
- `ExcelImportService` 当前只记录配置路径，不做实际导入
- 数据查询接口尚未接入真实 Repository / JPA 查询
- 前端 Markdown 渲染仍是轻量实现，不是完整 Markdown 解析器
- 部分中文历史文案曾有编码问题，README 已整理为 UTF-8 版本

## 开发文档

仓库内可参考以下文档：

- `Agent-POC开发文档.md`
- `Agent-POC解读 .docx`
- `Agent-POC-需求文档.docx`

## 最近验证

已在当前仓库验证通过的命令：

```bash
cd frontend
npm run build

cd backend
mvn -s settings.xml "-Dfrontend.skip=true" clean install
```

## 下一步建议

建议优先按下面顺序继续推进：

1. 完成 Excel 导入与 H2 数据初始化
2. 补齐 `/api/v1/data/*` 的真实查询逻辑
3. 接入 Spring AI 实际模型调用
4. 打通 Tool 查询到分析结果的完整链路
5. 补充测试、README 示例和交付说明
