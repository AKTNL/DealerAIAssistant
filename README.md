# 经销商 AI 分析助手

一个面向经销商经营分析场景的 Agent POC 脚手架。项目采用 `Vue 3 + Vite` 前端和 `Spring Boot 3 + Spring AI` 后端，目标是提供一个支持口令登录、SSE 流式回复、会话管理和后续数据分析能力接入的单页 Web 应用。

当前仓库更接近“可联调骨架”而不是“完整业务系统”：前后端链路、登录、流式聊天 UI 和会话清理已经打通，Excel 导入、真实数据查询、模型调用和 Tool 分析能力仍处于占位实现阶段。

## 项目现状

已完成：

- 口令登录页与会话进入流程
- 中英文界面切换
- 示例问题侧栏与单会话工作区
- `/api/chat/stream` 的 SSE 流式链路
- `<think>` 思考区折叠展示与 follow-up 提取
- 会话清空与本地 `sessionId` 管理
- 后端 API Key 过滤器基础骨架
- 前端构建产物输出到后端 `static/` 目录

待完善：

- Excel 样例数据导入
- H2 真实数据表与查询逻辑
- `/api/v1/data/*` 真实返回
- Spring AI 模型调用与 Tool 接入
- 更完整的 Markdown 渲染与代码高亮
- 自动化测试

## 技术栈

- 前端：`Vue 3`、`Vite`
- 后端：`Java 21`、`Spring Boot 3.4.x`
- AI 相关：`Spring AI 1.0.0`
- 数据侧：`H2`、`Spring Data JPA`、`Apache POI`
- 通信方式：`HTTP + SSE`

## 目录结构

```text
.
├─ frontend/   # Vue 3 前端
│  ├─ src/api
│  ├─ src/components
│  ├─ src/composables
│  └─ src/views
├─ backend/    # Spring Boot 后端
│  ├─ src/main/java/com/brand/agentpoc
│  │  ├─ ai
│  │  ├─ config
│  │  ├─ controller
│  │  ├─ dto
│  │  └─ service
│  └─ src/main/resources
└─ Agent-POC开发文档.md
```

## 快速开始

### 环境要求

- `Java 21`
- `Maven 3.9+`
- `Node.js 18+`
- `npm`

### 1. 前后端分开开发

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

- Vite 已将 `/api` 代理到 `http://127.0.0.1:8081`
- `-Dfrontend.skip=true` 表示只启动后端，不在 Maven 生命周期里重复构建前端

### 2. 构建一体化产物

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

运行：

```bash
cd backend
java -jar target/agent-poc-backend-0.0.1-SNAPSHOT.jar
```

说明：

- `frontend/vite.config.js` 会将构建结果输出到 `backend/src/main/resources/static`
- 运行 Jar 后可直接通过 `http://localhost:8081` 访问页面

## 配置说明

后端默认配置位于 `backend/src/main/resources/application.yml`。

核心配置项：

- `APP_ACCESS_KEY`：访问口令，默认 `demo123`
- `APP_API_KEY`：数据接口 API Key，默认 `poc-api-key`
- `APP_EXCEL_PATH`：Excel 路径，默认 `classpath:Sample Data.xlsx`
- `OPENAI_API_KEY`：模型 API Key
- `OPENAI_BASE_URL`：模型服务地址，默认 `https://api.openai.com`
- `OPENAI_MODEL`：模型名，默认 `gpt-4o-mini`

注意：

- 当前聊天能力仍是占位实现，未真正调用模型
- 当前仓库未包含 `Sample Data.xlsx`
- `APP_EXCEL_PATH` 现阶段主要用于后续 Excel 导入能力接入

## 接口概览

认证与聊天接口：

- `POST /api/auth/verify`：校验访问口令
- `POST /api/chat`：同步聊天接口，当前返回占位文本
- `POST /api/chat/stream`：SSE 流式聊天接口
- `DELETE /api/chat/{sessionId}`：清空当前会话

数据接口：

- `GET /api/v1/data/dealers`
- `GET /api/v1/data/opportunities`
- `GET /api/v1/data/campaigns`
- `GET /api/v1/data/tasks`
- `GET /api/v1/data/targets`
- `GET /api/v1/data/leads`

说明：

- `/api/auth/**` 和 `/api/chat/**` 已加入白名单，不需要 `X-API-Key`
- `/api/v1/data/**` 受 `ApiKeyFilter` 保护，手动调试时需要携带 `X-API-Key: poc-api-key`
- 当前 `DataQueryService` 仍返回空结果集

## 当前限制

- `ChatService` 目前输出的是占位分析结果，不是真实 AI 回复
- `ExcelImportService` 目前只记录配置路径，不做实际导入
- 数据查询接口已留好入口，但尚未接入真实 Repository / JPA 查询
- 前端 Markdown 渲染为轻量自实现版本，不是完整 Markdown 解析器
- 部分中文占位文案存在编码问题，后续建议统一按 UTF-8 重新整理

## 开发文档

项目内已包含一份更详细的开发说明：

- `Agent-POC开发文档.md`

## 构建验证

已在当前仓库完成以下命令验证：

```bash
cd frontend
npm run build

cd backend
mvn -s settings.xml "-Dfrontend.skip=true" clean install
```
