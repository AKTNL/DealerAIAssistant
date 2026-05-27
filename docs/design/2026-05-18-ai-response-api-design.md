# AI 回复增强与 REST API 落地设计

## 背景

当前 AI 回复内容偏少，需增加问题诊断、改进建议、数据汇总三个段落。同时 `openapi.json` 中的字段名来自 CSV 表头（如 `Retailer__r_Name`），与 Spring/Jackson 实际序列化的 camelCase 不一致，服务器地址、鉴权描述、响应结构也需对齐。

## 目标

1. AI 回复从 3 段扩展为 6 段 + 追问块
2. 按 openapi.json 路径落地 10 个真实 REST 端点（metrics + details）
3. 修复 openapi.json：server URL、鉴权、字段映射、响应包装
4. 不改动现有 `/api/v1/data/*`、流式推送、Mermaid 渲染、降级逻辑

## 非目标

- 不改 UI 布局
- 不改 Mermaid 图表渲染
- 不改 H2/CSV 取数流程
- 不删除旧 `/api/v1/data/*` 端点

---

## 一、AI 回复结构：6 段 + 追问块

```
## 核心结论              ← 保留
## 数据支撑              ← 保留（HTML table + Mermaid 图表）
## 经营分析              ← 保留（数据归因 2-3 条 + 可执行建议 2-3 条）
## 问题诊断与解决         ← 新增
## 改进建议              ← 新增
## 数据汇总              ← 新增
追问：                  ← 保留，不计入段落数
```

### 各段生成规则

**## 核心结论**（保留）
- 2-4 条核心发现，引用具体数值，给出优先级判断。

**## 数据支撑**（保留）
- HTML `<table>` + 按场景选择 Mermaid 图表类型。

**## 经营分析**（保留）
- 数据归因 2-3 条：`[指标变化] + [具体对象] + [可能原因]`
- 可执行建议 2-3 条：`[动作] + [对象] + [预期目标]`

**## 问题诊断与解决**（新增）
- 从达成率/转化率/积压量中识别 1-2 个最大短板
- 每条格式：`[差距] + [根因] + [解决动作]`
- 若全部达标：说明当前最大优势及下一个潜在瓶颈

**## 改进建议**（新增）
- 结合达成率分支：
  - 达成率 ≥ 80%：如何拉开差距、复制到其他门店/车型
  - 达成率 < 80%：分阶段路径（本月内 / 下季度 / 年度），带量化里程碑
- 每条建议包含：`[动作] + [对象] + [预期结果] + [时间范围]`

**## 数据汇总**（新增）
- 从对应 `_metrics` 接口提取 5-8 项核心 KPI，HTML `<table>` 呈现
- 列：指标名称 | 数值 | 范围 | 对比基准
- 不可与 `## 数据支撑` 表格重复

**追问：**（保留）
- 精确 2 条编号追问

---

## 二、后端 REST API 设计

### 2.1 端点清单

新建 `AnalyticsApiController`，10 个端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/targets/metrics` | 目标达成指标汇总 |
| GET | `/api/targets/details` | 目标达成明细（分页） |
| GET | `/api/opportunities/metrics` | 商机漏斗指标汇总 |
| GET | `/api/opportunities/details` | 商机明细（分页） |
| GET | `/api/leads/metrics` | 线索转化指标汇总 |
| GET | `/api/leads/details` | 线索明细（分页） |
| GET | `/api/tasks/metrics` | 销售跟进指标汇总 |
| GET | `/api/tasks/details` | 任务明细（分页） |
| GET | `/api/campaigns/metrics` | 市场活动指标汇总 |
| GET | `/api/campaigns/details` | 活动明细（分页） |

### 2.2 统一响应体

#### 成功

```json
{
  "code": 200,
  "data": { ... },
  "message": "success"
}
```

- `metrics` 端点：`data` 为指标汇总对象
- `details` 端点：`data` 为 `ApiPage<T>` 分页对象

#### 错误

```json
{
  "code": 401,
  "data": null,
  "message": "Invalid API key"
}
```

```json
{
  "code": 400,
  "data": null,
  "message": "Parameter 'page' must be >= 1"
}
```

统一错误码：`400` 参数错误、`401` 鉴权失败、`404` 数据不存在、`500` 服务内部错误。

### 2.3 分页参数规范

`details` 端点统一支持：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码，从 1 开始 |
| `pageSize` | int | 50 | 每页条数，上限 200 |
| `sortBy` | string | 实体默认排序字段 | 排序字段名 |
| `sortOrder` | string | desc | asc 或 desc |

当 `page` 超过总页数时，返回 `items: []`，`total` 保持真实总数。

### 2.4 鉴权

`ApiKeyFilter` 当前逻辑：白名单路径 **跳过** 鉴权（`shouldNotFilter` 返回 true = 公开访问）。

| 路径类型 | 鉴权行为 | 应加入白名单？ |
|----------|----------|----------------|
| `/`、`/index.html`、`/assets/**` | 公开 | 是 |
| `/api/auth/**`、`/api/chat/**`、`/api/model-config/**` | 公开 | 是 |
| `/swagger-ui/**`、`/v3/api-docs/**`、`/h2-console/**` | 公开 | 是 |
| `/api/targets/**`、`/api/opportunities/**` 等 | **受保护** | **否** |

新 analytics 端点属于受保护路径，**不加入** 白名单，因此会被 `ApiKeyFilter.doFilterInternal` 拦截并要求 `X-API-Key`。

### 2.5 指标定义（metrics schema 级别约束）

每个 `metrics` 端点明确返回的核心字段：

**`/api/targets/metrics`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalDealers` | int | 覆盖门店数 |
| `totalAsKTarget` | int | 目标总数 |
| `totalOpportunityWon` | int | 实际赢单总数 |
| `averageAchievementRate` | double | 平均达成率（%） |
| `lowestDealer` | object | 最低达成门店 {dealerCode, dealerName, achievementRate} |
| `highestDealer` | object | 最高达成门店 {dealerCode, dealerName, achievementRate} |

**`/api/opportunities/metrics`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalOpportunities` | int | 商机总数 |
| `wonCount` | int | 已赢单 |
| `lostCount` | int | 已丢单 |
| `openCount` | int | 进行中 |
| `winRate` | double | 赢单率（%） |
| `stageDistribution` | object | 各阶段数量分布 `{"stageName": count}` |
| `topLossReasons` | array | 战败原因 Top 5 `[{reason, count}]` |

**`/api/leads/metrics`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalLeads` | int | 线索总数 |
| `convertedCount` | int | 已转化 |
| `conversionRate` | double | 转化率（%） |
| `sourceDistribution` | object | 各来源数量 `{"source": count}` |
| `bestConversionSource` | object | 转化率最高来源 {source, conversionRate} |

**`/api/tasks/metrics`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalTasks` | int | 任务总数 |
| `completedCount` | int | 已完成 |
| `openCount` | int | 未完成 |
| `overdueCount` | int | 逾期数 |
| `completionRate` | double | 完成率（%） |
| `highestBacklogDealer` | object | 积压最高门店 {dealerCode, dealerName, openCount, overdueCount} |

**`/api/campaigns/metrics`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalCampaigns` | int | 活动总数 |
| `averageAttainment` | double | 平均达成率（%） |
| `bestCampaign` | object | 最佳活动 {campaignId, campaignName, attainmentRate} |
| `totalActualOpportunities` | int | 实际带来商机总数 |
| `totalTarget` | int | 目标商机总数 |

### 2.6 数据缺失统一策略

当查询条件无匹配数据时：

- `code` 仍为 200
- `data` 中各数值字段返回 0 或空数组（非 null）
- `message` 为 `"success"`（非错误）
- 不在 response body 中写"暂无数据"等 UI 文案

零值策略：
- 计数类字段 → `0`
- 比率类字段（分母为 0）→ `0.0`
- 对象类字段（如 `lowestDealer`）→ `null`
- 数组类字段 → `[]`

---

## 三、DTO 设计

新建以下 DTO，不直接暴露 Entity：

```
dto/
  response/
    ApiResult.java        // 泛型统一响应 {code, data, message}
    ApiPage.java           // 泛型分页 {items, total, page, pageSize}
  metrics/
    TargetMetrics.java
    OpportunityMetrics.java
    LeadMetrics.java
    TaskMetrics.java
    CampaignMetrics.java
  detail/
    TargetDetail.java
    OpportunityDetail.java
    LeadDetail.java
    TaskDetail.java
    CampaignDetail.java
```

`ApiResult<T>` 结构：

```java
public class ApiResult<T> {
    private int code;
    private T data;
    private String message;

    public static <T> ApiResult<T> success(T data) { ... }
    public static <T> ApiResult<T> error(int code, String message) { ... }
}
```

`ApiPage<T>` 结构：

```java
public class ApiPage<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
}
```

---

## 四、openapi.json 修复

### 4.1 服务器地址

```json
"servers": [
  {
    "url": "http://localhost:8080",
    "description": "本地开发环境"
  },
  {
    "url": "https://your-api-domain.com",
    "description": "生产环境"
  }
]
```

### 4.2 鉴权声明

```json
"components": {
  "securitySchemes": {
    "ApiKeyAuth": {
      "type": "apiKey",
      "in": "header",
      "name": "X-API-Key"
    }
  }
},
"security": [
  { "ApiKeyAuth": [] }
]
```

### 4.3 字段映射规则

**原则：schema property 名必须与 Spring/Jackson 实际序列化的 JSON key 一致，不能以 Java 字段名为准而忽略 `@JsonProperty` 或默认序列化行为。**

当前项目未使用 `@JsonProperty` 重命名，Jackson 默认将 getter 转为 camelCase JSON key：
- `getDealerCode()` → `dealerCode`
- `getAsKTarget()` → `asKTarget`

因此 openapi.json 中所有 CSV 原始字段名（`Retailer__r_Name`、`AaKTarget__c` 等）必须替换为实际 REST 响应中的 camelCase 属性名。

| 旧（CSV 表头） | 新（实际 JSON key） |
|---|---|
| `Retailer__r_Name` | `dealerName` |
| `Retailer__r_DealerCode__c` | `dealerCode` |
| `Name` | `name`（目标记录名） |
| `AaKTarget__c` | `asKTarget` |
| `OpportunityCreateCount__c` | `opportunityCreateCount` |
| `OpportunityWonCount__c` | `opportunityWonCount` |
| `Opportunity_Type__c` | `opportunityType` |
| `ProductModel__c` | `productModel` |
| `TargetDate__c` | `targetDate` |
| `Month__c` | `month` |
| `Year__c` | `year` |
| `DealerGroupName__c` | `dealerGroupName` |
| `SalesRetailer__r_Name` | `dealerName` |
| `SalesRetailer__r_DealerCode__c` | `dealerCode` |
| `ClosedReason__c` | `closedReason` |
| `Opportunity_Count__c` | `opportunityCount` |
| `Won_Opportunity_Count__c` | `wonOpportunityCount` |
| `Model__c` | `model` |
| `Purchase_Horizon__c` | `purchaseHorizon` |
| `EnquiryType__c` | `enquiryType` |
| `VehicleOfInterest__r_RangeProduct__r_Model__c` | `vehicleOfInterestModel` |
| `CampaignID__c` | `campaignId` |
| `Target_Opportunity_Amount__c` | `targetOpportunityAmount` |
| `Target_Order_Amount__c` | `targetOrderAmount` |
| `CampaignType__c` | `campaignType` |
| `Product_Model__c` | `productModel` |

### 4.4 响应结构修复

`_details` 路径的 `responses.200.content` 从裸数组改为 Result Wrapper + ApiPage：

```json
"responses": {
  "200": {
    "description": "成功",
    "content": {
      "application/json": {
        "schema": {
          "$ref": "#/components/schemas/ApiResult_DetailPage"
        }
      }
    }
  }
}
```

`_metrics` 路径补充 schema 指向对应的 metrics DTO。

### 4.5 补充 components/schemas

```json
"components": {
  "schemas": {
    "ApiResult_DetailPage": {
      "type": "object",
      "properties": {
        "code": { "type": "integer" },
        "message": { "type": "string" },
        "data": {
          "type": "object",
          "properties": {
            "items": { "type": "array", "items": { "$ref": "#/components/schemas/..." } },
            "total": { "type": "integer" },
            "page": { "type": "integer" },
            "pageSize": { "type": "integer" }
          }
        }
      }
    }
  }
}
```

---

## 五、RuleBasedAnalyticsService 降级输出

当模型不可用时（fallback 模式），`RuleBasedAnalyticsService` 需用确定性规则生成全部 6 段。

### 降级回复模板

每个 scenario（TARGET_ACHIEVEMENT、OPPORTUNITY_FUNNEL 等）的 `ScenarioResult` 扩展为 6 段输出。新增 3 段根据 metrics 计算结果确定性生成：

**问题诊断与解决（降级规则）：**
- 取 `averageAchievementRate` 或 `winRate` 或 `completionRate` 与基准比较
- 低于基准：`"[门店/指标] 达成率仅 [X]%，主要原因是 [从数据中提取的指标]，建议 [具体动作]"` 
- 高于基准：`"当前 [指标] 表现良好（[X]%），下一阶段应关注 [次弱指标]"`

**改进建议（降级规则）：**
- 从 validMetrics 中取最低达成 Top 2 → 各生成一条改进路径
- 从 validMetrics 中取最高达成 Top 1 → 生成一条可复制经验
- 每条带量化时间节点（本月 / 下季度）

**数据汇总（降级规则）：**
- 用 `TABLE_LABELS_ZH` 中已有字段拼 HTML table
- 至少包含：覆盖门店数、总目标、总达成、平均达成率、最佳/最差门店

实现方式：在 `buildGroundedReference` 中追加 `DataSummary` 行，或在 `ScenarioResult.reply()` 中直接拼接新段落。

---

## 六、PromptFactory 改动

### buildSystemPrompt

输出结构声明改为 6 段（中英文均改）：

```
中文：
输出结构必须是：
## 核心结论
## 数据支撑
## 经营分析
## 问题诊断与解决
## 改进建议
## 数据汇总
追问：

英文：
Output structure must be:
## Conclusion
## Data Support
## Short Analysis
## Problem Diagnosis & Solutions
## Improvement Suggestions
## Data Summary
FOLLOW_UP_QUESTIONS:
```

### buildGroundedModelPrompt

在 `经营分析` 段落后、`追问：` 之前插入 3 段的生成指令。沿用现有编号风格，新增第 10-12 条要求。

---

## 七、ChatService 校验适配

`isValidAnalyticsReply` 方法中提取 `##` 标题的白名单更新：

```java
// 英文
List.of("Conclusion", "Data Support", "Short Analysis",
        "Problem Diagnosis & Solutions", "Improvement Suggestions", "Data Summary")

// 中文
List.of("核心结论", "数据支撑", "经营分析",
        "问题诊断与解决", "改进建议", "数据汇总")
```

`ensureFollowUpQuestions` 不需改动——它通过 `FOLLOW_UP_QUESTIONS:` / `追问：` 定位，与段落数量无关。

---

## 八、文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `openapi.json` | 修改 | 修 server/auth/字段/响应体/补充 schemas |
| `AnalyticsApiController.java` | **新增** | 10 个 REST 端点 |
| `AnalyticsApiService.java` | **新增** | metrics 聚合 + details 分页 |
| `ApiResult.java` | **新增** | 泛型统一响应体 |
| `ApiPage.java` | **新增** | 泛型分页包装 |
| `TargetMetrics.java` 等 5 个 | **新增** | metrics DTO |
| `TargetDetail.java` 等 5 个 | **新增** | detail DTO |
| `ApiKeyFilter.java` | 不改 | 新端点不在白名单中，自动受 X-API-Key 保护 |
| `PromptFactory.java` | 修改 | 新增 3 段指令 + 输出结构 |
| `ChatService.java` | 修改 | 标题校验白名单 |
| `RuleBasedAnalyticsService.java` | 修改 | fallback 输出扩展为 6 段 |

## 九、测试策略

- `AnalyticsApiServiceTest`：metrics 聚合正确性、分页边界（超页返回空）、零值策略
- `AnalyticsApiControllerTest`：HTTP 结构、Result Wrapper 形状、401 拦截
- `PromptFactoryTest`：新段落在 system prompt 和 grounded prompt 中均出现
- `ChatServiceTest`：`isValidAnalyticsReply` 接受 6 段标题、拒绝少段回复
- `RuleBasedAnalyticsServiceTest`：fallback 输出含 6 段、含数据汇总 table

## 十、验收标准

- 10 个新端点返回正确的 Result Wrapper 格式
- openapi.json 字段名与实际 JSON 响应一致
- 未配置 API Key 时新端点返回 401
- AI 回复（模型模式）包含 6 段 + 追问块
- Fallback 降级回复同样包含 6 段
- 分页超限返回空 items 数组
- 数据缺失时各字段返回 0/空值，不报错
- 现有测试全部通过
