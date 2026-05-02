## 🤖 Assistant

可以，下面我直接给你一份**适合拿来开工的详细开发文档**。  
我会尽量按“**你真的要开始做**”的角度写，而不是只做概念分析。

---

# 经销商 AI 分析助手 POC 开发文档
版本：v1.0  
建议技术路线：**Spring AI + Vue3**  
适用对象：实习生开发小组 / POC 开发人员

---

# 1. 文档目标

本文档用于指导“经销商 AI 分析助手 POC”的实际开发落地，目标是帮助开发同学：

- 快速理解系统边界
- 明确模块划分
- 按顺序推进开发
- 对齐前后端接口
- 降低联调成本
- 最终交付一个**本地可运行、可演示、可联调**的 Web POC

---

# 2. 项目目标

实现一个本地运行的 Web 单页应用，支持以下能力：

1. 用户通过访问口令进入系统
2. 进入单聊天页面
3. 用户输入中文或英文自然语言问题
4. 系统基于本地 Excel 样例数据进行分析
5. AI 以流式方式返回 Markdown 结果
6. 支持：
   - 思考过程折叠展示
   - 追问按钮
   - 中英文 UI 切换
   - 清空会话
7. AI 输出语言严格跟随用户提问语言

---

# 3. 本次开发建议选型

由于需求文档明确允许二选一，建议本次只做：

## 3.1 前端
- Vue 3
- Vite
- TypeScript
- 原生 fetch + ReadableStream 解析 SSE
- markdown-it
- highlight.js

## 3.2 后端
- Java 21
- Spring Boot 3
- Spring AI
- Spring Data JPA
- H2 内存数据库
- Apache POI

## 3.3 模型接入
- OpenAI 兼容接口
- 通过配置项指定：
  - api-key
  - base-url
  - model-name

---

# 4. 总体实现策略

本 POC 不应从“复杂 Agent”切入，而应该采用以下顺序：

## 阶段顺序
1. **先做数据底座**
   - Excel 导入
   - H2 落库
   - 6 类数据查询 API

2. **再做最小可用前端**
   - 登录页
   - 聊天页骨架
   - i18n

3. **再打通最小 SSE 聊天链路**
   - 后端固定流式文本
   - 前端能解析并显示

4. **接入真实模型**
   - 模型返回普通结构化 Markdown

5. **接入 Tool 调用**
   - AI 调用数据查询工具分析业务问题

6. **最后补体验细节**
   - think 折叠
   - 跟进问题按钮
   - 错误处理
   - 清空会话

---

# 5. 功能范围

---

## 5.1 范围内
- 单页 Web POC
- 登录口令校验
- 单聊天工作区
- 基于 SSE 的流式问答
- 基于 Excel 样例数据分析
- 6 类业务数据查询
- AI 多轮对话
- Markdown 结果展示
- 思考过程折叠
- 2 个追问问题按钮
- 中英文切换

---

## 5.2 范围外
- 用户体系 / RBAC / SSO
- 真实 CRM/DMS/CDP 对接
- 数据权限隔离
- 持久化聊天记录
- 生产级安全治理
- 高并发处理
- 复杂监控告警
- 移动端适配

---

# 6. 业务分析场景

本 POC 聚焦 6 类一级分析场景：

1. 目标达成分析
2. 商机漏斗与转化分析
3. 销售跟进分析
4. 市场活动规划与效果分析
5. 经营对标分析
6. 线索来源与自然流量趋势分析

AI 不需要“无所不知”，而是围绕以上 6 类场景稳定回答。

---

# 7. 系统架构设计

---

## 7.1 架构总览

```text
[Vue3 Frontend]
    |
    |  HTTP / SSE
    v
[Spring Boot Backend]
    |
    +--> Auth Module
    +--> Data Query APIs
    +--> Chat Service (Spring AI)
    +--> Tool Layer
    +--> Session Memory
    |
    v
[H2 In-Memory Database]
    ^
    |
[Excel Import via Apache POI]
```

---

## 7.2 核心链路

### 数据链路
```text
Sample Data.xlsx
  -> Apache POI 解析
  -> Entity 映射
  -> H2 数据库
  -> JPA Repository
  -> Data Query Service
  -> REST API / AI Tool
```

### AI 聊天链路
```text
用户输入问题
  -> 前端调用 /api/chat/stream
  -> 后端根据 sessionId 构建上下文
  -> Spring AI 调用模型
  -> 模型按需调用 tools
  -> tools 查询 H2 数据
  -> 模型组织 Markdown 输出
  -> SSE 流式返回前端
  -> 前端实时渲染
```

---

# 8. 模块拆分

---

## 8.1 后端模块

### 1）配置模块
职责：
- application.yml 读取
- CORS 配置
- API Key 过滤
- Spring AI 配置

### 2）认证模块
职责：
- 访问口令校验

接口：
- `POST /api/auth/verify`

### 3）数据初始化模块
职责：
- 启动时读取 Excel
- 样例数据落库到 H2

### 4）数据查询模块
职责：
- 提供 6 个数据查询 API
- 同时作为 AI tools 数据源

### 5）AI 工具模块
职责：
- 封装 6 类查询能力供大模型调用

### 6）聊天模块
职责：
- 管理多轮上下文
- 调用模型
- 流式输出 SSE
- 清理 session 记忆

---

## 8.2 前端模块

### 1）登录模块
职责：
- 输入访问口令
- 调用校验接口
- 保存登录态

### 2）主布局模块
职责：
- 顶部导航
- 语言切换
- 清空会话

### 3）聊天模块
职责：
- 消息列表渲染
- 发送问题
- 接收 SSE
- 追问点击发送

### 4）消息渲染模块
职责：
- Markdown 渲染
- 代码高亮
- think 折叠
- follow-up 按钮提取

### 5）国际化模块
职责：
- 管理 zh/en 文案
- 语言持久化

---

# 9. 推荐项目结构

---

## 9.1 后端目录结构

```text
src/main/java/com/brand/agentpoc
├── config
│   ├── CorsConfig.java
│   ├── ApiKeyFilter.java
│   ├── AiConfig.java
│   └── AppProperties.java
├── controller
│   ├── AuthController.java
│   ├── ChatController.java
│   └── DataQueryController.java
├── service
│   ├── AuthService.java
│   ├── ChatService.java
│   ├── SessionMemoryService.java
│   ├── ExcelImportService.java
│   └── DataQueryService.java
├── ai
│   ├── DealerTools.java
│   ├── OpportunityTools.java
│   ├── CampaignTools.java
│   ├── TaskTools.java
│   ├── TargetTools.java
│   ├── LeadTools.java
│   ├── PromptFactory.java
│   └── LanguageDetector.java
├── entity
│   ├── Dealer.java
│   ├── Opportunity.java
│   ├── Campaign.java
│   ├── Task.java
│   ├── Target.java
│   └── Lead.java
├── repository
│   ├── DealerRepository.java
│   ├── OpportunityRepository.java
│   ├── CampaignRepository.java
│   ├── TaskRepository.java
│   ├── TargetRepository.java
│   └── LeadRepository.java
├── dto
│   ├── request
│   └── response
└── AgentPocApplication.java
```

---

## 9.2 前端目录结构

```text
src
├── api
│   ├── auth.ts
│   ├── chat.ts
│   └── data.ts
├── components
│   ├── layout
│   │   ├── TopNav.vue
│   │   └── ExampleSidebar.vue
│   ├── chat
│   │   ├── ChatInput.vue
│   │   ├── ChatMessageList.vue
│   │   ├── UserMessage.vue
│   │   ├── AssistantMessage.vue
│   │   └── FollowUpButtons.vue
│   └── common
│       ├── LoadingSpinner.vue
│       └── LanguageSwitcher.vue
├── composables
│   ├── useAuth.ts
│   ├── useChat.ts
│   ├── useSseParser.ts
│   └── useI18nState.ts
├── views
│   ├── LoginView.vue
│   └── ChatView.vue
├── utils
│   ├── markdown.ts
│   ├── messageParser.ts
│   └── storage.ts
├── locales
│   ├── zh.ts
│   └── en.ts
├── types
│   └── chat.ts
├── App.vue
└── main.ts
```

---

# 10. 数据模型设计

本 POC 推荐最小 6 张核心表：

---

## 10.1 Dealer

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| dealerCode | String | 经销商编码 |
| dealerName | String | 经销商名称 |
| city | String | 城市 |
| dealerGroupName | String | 集团名称 |

---

## 10.2 Opportunity

| 字段 | 类型 |
|---|---|
| id | Long |
| opportunityId | String |
| dealerCode | String |
| dealerName | String |
| city | String |
| dealerGroupName | String |
| productModel | String |
| stageName | String |
| leadSource | String |
| createdDate | LocalDate |
| expectedCloseDate | LocalDate |
| probability | Integer |

---

## 10.3 Campaign

| 字段 | 类型 |
|---|---|
| id | Long |
| campaignId | String |
| dealerCode | String |
| dealerName | String |
| city | String |
| dealerGroupName | String |
| productModel | String |
| campaignType | String |
| createdDate | LocalDate |
| actualOpportunityCount | Integer |
| totalNewCustomerTarget | Integer |

---

## 10.4 Task

| 字段 | 类型 |
|---|---|
| id | Long |
| taskId | String |
| dealerCode | String |
| dealerName | String |
| city | String |
| dealerGroupName | String |
| opportunityId | String |
| status | String |
| createdDate | LocalDate |

---

## 10.5 Target

| 字段 | 类型 |
|---|---|
| id | Long |
| dealerCode | String |
| dealerName | String |
| city | String |
| dealerGroupName | String |
| productModel | String |
| targetYear | Integer |
| targetMonth | Integer |
| asKTarget | Integer |
| opportunityWonCount | Integer |

---

## 10.6 Lead

| 字段 | 类型 |
|---|---|
| id | Long |
| leadId | String |
| dealerCode | String |
| dealerName | String |
| city | String |
| dealerGroupName | String |
| leadSource | String |
| stageName | String |
| productModel | String |
| createdDate | LocalDate |
| isConverted | Boolean |

---

# 11. Excel 导入设计

---

## 11.1 导入时机
后端服务启动时自动导入。

推荐方式：
- `CommandLineRunner`
- 或 `ApplicationRunner`

---

## 11.2 Excel 文件位置
- `classpath:Sample Data.xlsx`

---

## 11.3 需解析的 Sheet
至少包含：
- Campaign
- AE Target Data
- Opportunity
- Lead
- Task

---

## 11.4 导入流程

```text
应用启动
 -> 读取 Excel
 -> 遍历 Sheet
 -> 标准化表头
 -> 行数据映射为 Entity
 -> 批量保存到 H2
 -> 构建 Dealer 表
 -> 输出导入日志
```

---

## 11.5 表头处理建议

Excel 表头很容易不规范，建议统一标准化：

- trim 去首尾空格
- 转小写
- 去掉空格和换行
- 可维护别名映射表

示例：

```java
private String normalizeHeader(String header) {
    return header == null ? "" : header.trim().replaceAll("\\s+", "").toLowerCase();
}
```

---

## 11.6 日期处理建议

Excel 日期可能出现三种形式：
- 真正的 Excel 日期单元格
- 字符串日期
- 数字序列日期

应统一封装一个日期解析工具类。

---

## 11.7 容错策略

建议：
- 非关键字段为空时允许导入
- 某一行异常时记录日志并跳过
- 某一整个 sheet 失败时要明确输出错误
- 启动后打印导入统计：
  - campaign 导入条数
  - target 导入条数
  - opportunity 导入条数
  - lead 导入条数
  - task 导入条数
  - dealer 构建条数

---

# 12. 后端接口设计

---

## 12.1 认证接口

### POST `/api/auth/verify`

请求：
```json
{
  "key": "访问口令"
}
```

响应：
```json
{
  "success": true
}
```

失败：
```json
{
  "success": false
}
```

逻辑：
- 与配置文件中的固定访问口令比对
- 不做复杂鉴权

---

## 12.2 数据查询接口统一前缀
`/api/v1/data`

---

## 12.3 GET `/dealers`

功能：
- 按关键字搜索门店
- 不传 keyword 时返回全部

参数：
- keyword

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName

---

## 12.4 GET `/opportunities`

参数：
- dealerCode
- city
- dealerGroupName
- productModel
- startDate
- endDate
- raw

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName
- productModel
- stageName
- leadSource
- createdDate
- expectedCloseDate
- probability
- totalCount 或等价字段

---

## 12.5 GET `/campaigns`

参数：
- dealerCode
- city
- dealerGroupName
- productModel
- startDate
- endDate
- raw

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName
- productModel
- campaignType
- createdDate
- campaignCount
- actualOpportunityCount
- totalNewCustomerTarget

---

## 12.6 GET `/tasks`

参数：
- dealerCode
- city
- dealerGroupName
- startDate
- endDate
- raw

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName
- opportunityId
- status
- createdDate
- totalTaskCount

---

## 12.7 GET `/targets`

参数：
- dealerCode
- city
- dealerGroupName
- productModel
- targetYear
- targetMonth

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName
- productModel
- targetYear
- targetMonth
- asKTarget
- opportunityWonCount

---

## 12.8 GET `/leads`

参数：
- dealerCode
- city
- dealerGroupName
- leadSource
- productModel
- startDate
- endDate
- raw

返回字段至少包含：
- dealerCode
- dealerName
- city
- dealerGroupName
- leadSource
- stageName
- productModel
- createdDate
- isConverted
- totalCount

---

# 13. 数据查询实现建议

由于是 POC，数据量不会太大，建议两种方式二选一：

## 方案 A：快速实现
- repository 查询基础数据
- service 层 Java 过滤/聚合

优点：
- 快
- 容易改

缺点：
- 不优雅，但 POC 可接受

## 方案 B：规范实现
- 使用 JPA Specification 动态查询

优点：
- 更规范
- 更利于扩展

建议：
- 如果时间紧，先用 A
- 后期再逐步替换成 B

---

# 14. API Key 过滤设计

---

## 14.1 目的
保护除白名单外的接口，避免未授权调用。

---

## 14.2 白名单路径
- `/api/auth/**`
- `/api/chat/**`（如果按文档允许）
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/h2-console/**`

---

## 14.3 过滤逻辑
- 读取请求头 `X-API-Key`
- 与配置值比对
- 缺失或错误则返回 401 JSON

返回示例：
```json
{
  "code": 401,
  "message": "Invalid API key"
}
```

---

# 15. 聊天接口设计

建议只实现 Spring AI 方案。

---

## 15.1 POST `/api/chat`

同步接口，便于调试。

请求：
```json
{
  "sessionId": "xxx",
  "message": "本月哪些经销商目标达成率最低？"
}
```

响应：
```json
{
  "reply": "..."
}
```

---

## 15.2 POST `/api/chat/stream`

流式 SSE 接口。

请求：
```json
{
  "sessionId": "xxx",
  "message": "本月哪些经销商目标达成率最低？"
}
```

响应类型：
- `text/event-stream`

事件类型：
- `progress`
- `message`
- `done`

示例：
```text
event: progress
data: 正在识别分析主题

event: progress
data: 正在查询目标数据

event: message
data: <think>正在整理分析步骤...</think>

event: message
data: ## 结论

event: message
data: 本月目标达成率最低的经销商为...

event: done
data: [DONE]
```

---

## 15.3 DELETE `/api/chat/{sessionId}`

功能：
- 清空 Spring AI 会话记忆

响应：
```json
{
  "success": true
}
```

---

# 16. 会话管理设计

---

## 16.1 前端
使用：
- `localStorage` 保存 `brand_session_id`
- `sessionStorage` 保存登录状态
- `localStorage` 保存语言偏好

---

## 16.2 后端
使用内存 Map 保存聊天上下文：

```text
Map<String, List<Message>>
```

key:
- sessionId

value:
- 最近若干轮消息历史

建议：
- 最多保留最近 10~20 轮
- 避免 token 无限增长

---

# 17. AI 工具层设计

本 POC 的本质是“工具查询型 Agent”。

---

## 17.1 必须提供的工具

1. 搜索 Dealer
2. 查询 Opportunity
3. 查询 Campaign
4. 查询 Task
5. 查询 Target
6. 查询 Lead

---

## 17.2 工具设计原则
不要给模型开放过底层的能力，例如：
- 不要直接给 SQL 执行工具
- 不要让模型拼 JPQL

要给模型的是**语义化工具**。

---

## 17.3 推荐工具签名

```java
searchDealers(String keyword, String city, String dealerGroupName)

queryOpportunities(
  String dealerCode,
  String city,
  String dealerGroupName,
  String productModel,
  String startDate,
  String endDate,
  Boolean raw
)

queryCampaigns(...)

queryTasks(...)

queryTargets(
  String dealerCode,
  String city,
  String dealerGroupName,
  String productModel,
  Integer targetYear,
  Integer targetMonth
)

queryLeads(...)
```

---

# 18. Prompt 设计

Prompt 是这个 POC 成败的核心之一。

---

## 18.1 System Prompt 目标
要求模型做到：

1. 优先调用工具获取数据
2. 不编造数据
3. 输出结构化 Markdown
4. 语言严格跟随用户
5. 不暴露内部工具名/API 名
6. 最后附带两个追问问题

---

## 18.2 推荐 System Prompt 结构

你们可以按这个方向实现：

```text
You are an AI business analysis assistant for dealer sales and marketing scenarios.

Rules:
1. Always prioritize tool-based data before answering business analysis questions.
2. Do not fabricate metrics or facts not supported by tool results.
3. First provide a concise conclusion.
4. Then provide supporting data and short analysis.
5. Use Markdown headings and bullet points.
6. Never expose internal tool names, API names, SQL, repository names, or implementation details.
7. The answer language must strictly follow the user's language.
8. If data is insufficient, say so clearly.
9. At the end, always append:

FOLLOW_UP_QUESTIONS:
1. ...
2. ...

If you need a visible reasoning block for UI rendering, output a brief, safe, presentation-friendly block inside:
<think>...</think>

Focus analysis themes:
- target achievement
- opportunity funnel and conversion
- sales follow-up
- campaign planning and performance
- dealer benchmark comparison
- lead source and trend analysis
```

---

## 18.3 输出格式建议

```markdown
<think>
1. 识别问题类型
2. 查询相关数据
3. 比较关键指标
4. 生成结论与建议
</think>

## 结论
...

## 数据支撑
- ...
- ...

## 分析
- ...
- ...

## 建议
- ...
- ...

FOLLOW_UP_QUESTIONS:
1. ...
2. ...
```

---

# 19. 语言跟随策略

文档要求 AI 回答语言必须跟随用户提问语言。

建议不要完全依赖模型自己判断，应该做后端辅助判断。

---

## 19.1 简单检测方案
规则：
- 如果用户消息中中文字符占比明显，则 lang = zh
- 否则 lang = en

然后将结果写入 prompt：
- `The user's language is Chinese. You must answer in Chinese.`
- `The user's language is English. You must answer in English.`

---

# 20. SSE 协议实现建议

---

## 20.1 后端
统一输出：

- `event: progress`
- `event: message`
- `event: done`

建议每个事件块以空行分隔。

---

## 20.2 前端解析规则
前端按行解析：
- `event:` 取事件类型
- `data:` 取内容
- 空行表示事件块结束

注意：
- chunk 可能被截断
- 需要维护 buffer
- done 或 `[DONE]` 时结束流

---

## 20.3 建议前端消息状态
assistant 消息对象建议包含：

```ts
type ChatMessage = {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string
  steps?: string[]
  followUps?: string[]
  streaming?: boolean
  error?: boolean
}
```

---

# 21. 前端页面设计

---

## 21.1 登录页

功能：
- 品牌 Logo
- 标题/副标题
- 口令输入框
- 登录按钮
- 中英文切换

交互：
- 输入框为 password
- Enter 提交
- 提交中按钮 loading
- 失败时：
  - 清空输入框
  - 显示错误提示
  - 输入框抖动

登录成功：
- `sessionStorage.setItem("auth_verified", "true")`

---

## 21.2 主页面

页面组成：
- 顶部导航
- 示例问题侧栏
- 聊天区
- 底部输入区

---

## 21.3 顶部导航
包含：
- Logo
- 标题
- 副标题
- 语言切换按钮
- 清空会话按钮
- 当前链路名（可选）

---

## 21.4 示例问题侧边栏
支持：
- 默认隐藏
- 按分组显示示例问题
- 点击后填入输入框
- 提供中英文两套示例问题

---

## 21.5 聊天区
支持：
- 用户消息
- assistant 消息
- Markdown 渲染
- 自动滚动到底部
- 流式中显示光标效果

---

## 21.6 输入区
支持：
- Enter 发送
- Shift+Enter 换行
- loading 时禁用
- 空文本禁用发送
- 自适应高度

---

# 22. assistant 消息解析规则

---

## 22.1 think 解析
如果消息中包含：

```html
<think>...</think>
```

则：
- 提取 think 内容到 `thinking`
- 默认折叠
- think 外内容作为正式回答展示

若流式中先收到 think 内容但正文未到：
- 显示“思考中”状态

---

## 22.2 跟进问题解析
若结尾包含：

```text
FOLLOW_UP_QUESTIONS:
1. xxx
2. xxx
```

或变体：
- `Follow-up Questions:`
- `跟进问题`
- `后续问题`
- `下一步问题`

则：
- 从正文中移除这部分
- 提取最多 2 条问题生成按钮
- 点击按钮后直接作为新问题发送

---

# 23. 国际化设计

---

## 23.1 需覆盖内容
- 登录页文案
- 顶部导航文案
- 输入框 placeholder
- 欢迎语
- 示例问题
- 系统状态词：
  - 思考中
  - 展开
  - 收起
  - 请求失败
  - 会话已清空

---

## 23.2 持久化
- 语言存 localStorage
- 默认 `zh`

---

# 24. 配置设计

建议统一放在 `application.yml`，并支持环境变量覆盖。

---

## 24.1 后端配置项

```yaml
server:
  port: 8081

app:
  auth:
    access-key: demo123
  security:
    api-key: poc-api-key
  excel:
    path: classpath:Sample Data.xlsx

spring:
  datasource:
    url: jdbc:h2:mem:agentpoc
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true

ai:
  openai:
    api-key: ${OPENAI_API_KEY:xxx}
    base-url: ${OPENAI_BASE_URL:https://xxx}
    model: ${OPENAI_MODEL:gpt-4o-mini}
```

---

# 25. 开发顺序建议

---

## 第 1 阶段：初始化工程
目标：
- 前后端项目创建
- 基础依赖加入
- 配置文件可运行

交付：
- Spring Boot 可启动
- Vue 项目可启动

---

## 第 2 阶段：数据底座
目标：
- Excel 成功导入
- H2 存在数据
- 6 个数据接口可调试

交付：
- Postman 可访问全部数据 API

---

## 第 3 阶段：认证和页面骨架
目标：
- 登录页
- 主页面框架
- 中英文切换

交付：
- 口令登录可进入主页面

---

## 第 4 阶段：最小聊天链路
目标：
- `/api/chat/stream` 先返回固定流
- 前端能正确显示

交付：
- 聊天页面能看到流式返回

---

## 第 5 阶段：模型接入
目标：
- 接通真实模型
- 普通问答可返回 Markdown

交付：
- 中英文问题能得到结构化结果

---

## 第 6 阶段：Tool 接入
目标：
- 模型调用 6 类 tools
- 能回答业务分析问题

交付：
- 典型业务问题基于数据回答

---

## 第 7 阶段：体验增强
目标：
- think 折叠
- follow-up 按钮
- 清空会话
- 错误提示
- loading / 禁用状态

交付：
- 满足展示验收要求

---

# 26. 任务拆分建议

---

## 26.1 后端任务清单
- [ ] 初始化 Spring Boot 项目
- [ ] 配置 H2/JPA
- [ ] 配置 CORS
- [ ] 配置 API Key Filter
- [ ] 实现 `/api/auth/verify`
- [ ] 实现 Excel 导入器
- [ ] 建立 6 张表实体
- [ ] 实现 6 个 Repository
- [ ] 实现 6 个数据查询接口
- [ ] 接入 Spring AI
- [ ] 实现 SessionMemoryService
- [ ] 实现 6 个 AI Tool
- [ ] 实现 `/api/chat`
- [ ] 实现 `/api/chat/stream`
- [ ] 实现 `/api/chat/{sessionId}` delete
- [ ] 编写 prompt 构造逻辑
- [ ] 编写语言检测逻辑

---

## 26.2 前端任务清单
- [ ] 初始化 Vue3 项目
- [ ] 配置 i18n
- [ ] 实现登录页
- [ ] 实现顶部导航
- [ ] 实现聊天列表
- [ ] 实现输入框
- [ ] 实现 SSE 解析 composable
- [ ] 实现 Markdown 渲染
- [ ] 实现代码高亮
- [ ] 实现 think 折叠
- [ ] 实现 follow-up 提取与按钮
- [ ] 实现清空会话
- [ ] 实现欢迎语逻辑
- [ ] 实现错误提示
- [ ] 实现自动滚动

---

## 26.3 联调测试任务清单
- [ ] 验证 Excel 数据导入
- [ ] 验证数据接口筛选
- [ ] 验证认证成功/失败
- [ ] 验证 SSE 正常结束
- [ ] 验证 SSE 中断报错
- [ ] 验证中英文问答
- [ ] 验证 follow-up 点击发送
- [ ] 验证清空会话
- [ ] 验证 think 折叠展示

---

# 27. 演示问题建议

---

## 中文
1. 本月哪些经销商目标达成率最低？
2. 北京地区的商机转化漏斗表现如何？
3. 哪些门店的销售跟进活跃度偏低？
4. 最近市场活动带来的商机效果怎么样？
5. 华东区域哪些经销商经营表现最好？
6. 当前主要线索来源是什么？自然流量趋势如何？

---

## English
1. Which dealers have the lowest target achievement this month?
2. How does the opportunity funnel perform in Beijing?
3. Which dealers show low sales follow-up activity?
4. How effective are recent campaigns in generating opportunities?
5. Which dealers are outperforming others in East China?
6. What are the major lead sources and how is organic traffic trending?

---

# 28. 风险点与规避建议

---

## 风险 1：Excel 导入失败
原因：
- 表头不统一
- 日期格式混乱
- 空值异常

规避：
- 做表头标准化
- 做容错日志
- 每个 sheet 分开导入和统计

---

## 风险 2：SSE 不稳定
原因：
- chunk 拆包
- done 未正确结束
- 前端 buffer 解析错

规避：
- 先做最小固定文本 SSE
- 前后端共同约定事件格式
- done 时明确返回 `[DONE]`

---

## 风险 3：AI 不查数据直接胡说
规避：
- prompt 中强约束必须优先依赖工具
- tool 返回不足时要求明确说明“数据不足”
- 典型问题提前测试

---

## 风险 4：清空会话后上下文没清掉
规避：
- 前端更新 sessionId
- 后端删除 memory
- UI 消息列表同步重置

---

## 风险 5：中英文逻辑混乱
规避：
- UI 语言与 AI 回复语言分开处理
- UI 看按钮状态
- AI 回复看用户消息语言

---

# 29. 验收标准映射

---

## 29.1 基础验收
- [ ] 后端启动成功
- [ ] 前端启动成功
- [ ] 未登录时只能看到口令页
- [ ] 正确口令可进入主页面
- [ ] 中英文切换有效
- [ ] 欢迎语显示正常

---

## 29.2 聊天链路验收
- [ ] 可正常发送问题
- [ ] 可流式返回
- [ ] 页面可展示思考中/进度状态
- [ ] 失败时有错误提示

---

## 29.3 数据能力验收
- [ ] Excel 数据成功导入
- [ ] 6 类数据接口均可访问
- [ ] 支持基础筛选
- [ ] AI 可基于数据回答问题

---

## 29.4 前端展示验收
- [ ] Markdown 标题、列表、表格正常
- [ ] 代码块高亮正常
- [ ] think 折叠可用
- [ ] FOLLOW_UP_QUESTIONS 可提取
- [ ] 点击 follow-up 可再次发问
- [ ] 清空会话后生成新 session

---

# 30. 交付物清单

最终至少提交：

1. 前端源码
2. 后端源码
3. 启动说明文档
4. 配置说明文档
5. 演示问题列表
6. 如果可以，附带：
   - 接口说明
   - 演示截图
   - 已知问题说明

---

# 31. 启动说明模板

---

## 后端启动
1. 配置 `application.yml`
2. 放置 `Sample Data.xlsx`
3. 启动 Spring Boot
4. 检查日志确认 Excel 已导入

默认地址：
- `http://localhost:8081`

---

## 前端启动
1. 安装依赖
2. 配置后端地址
3. 启动 Vite

默认地址：
- `http://localhost:5173`

---

# 32. 推荐里程碑排期

如果你们是一个小组，我建议按下面节奏：

## 第 1 天
- 定技术方案
- 起前后端工程
- 定目录结构

## 第 2~3 天
- Excel 导入
- H2 落库
- 6 个查询接口

## 第 4 天
- 登录页
- 主页面骨架
- i18n

## 第 5 天
- SSE 最小链路
- 前端流式解析

## 第 6~7 天
- 模型接入
- Prompt 初版
- 同步/流式接口联调

## 第 8~9 天
- Tool 接入
- 业务问题调优

## 第 10 天
- think / follow-up / 清空会话 / 异常处理
- 演示准备

---

# 33. 最小可交付版本定义

如果时间紧，以下是必须完成项：

## 必须完成
- 登录页
- 主聊天页
- Excel 导入
- 6 个查询接口
- `/api/chat/stream`
- SSE 前端流式显示
- Markdown 渲染
- 中英文切换
- 跟进问题按钮
- 清空会话

## 可弱化项
- 示例问题侧栏可默认隐藏
- think 可先做简化版
- progress 可先做文本级
- 样式可先朴素但完整

---

如果你愿意，我下一步可以继续直接帮你补两份最实用的内容之一：

### 方案 A
我继续给你写：
**《后端开发文档（Spring Boot + Spring AI 详细版）》**
包括：
- Maven 依赖
- application.yml
- 实体设计
- Controller/Service 结构
- SSE 写法
- Tool 写法

### 方案 B
我继续给你写：
**《前端开发文档（Vue3 + SSE + Markdown 详细版）》**
包括：
- 页面结构
- 状态设计
- SSE 解析代码思路
- think/follow-up 解析逻辑

我建议你下一步先让我写 **后端开发文档详细版**，因为这个 POC 的地基在后端数据和 AI 链路。
