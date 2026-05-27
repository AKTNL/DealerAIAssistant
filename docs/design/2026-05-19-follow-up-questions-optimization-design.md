# 追问（Follow-Up Questions）优化设计

## 目标

解决当前追问功能三个痛点：
1. **追问时有时无** — 后端异常处理导致追问丢失
2. **追问内容不相关** — 模型生成追问缺乏上下文校验
3. **默认追问不智能** — 静态兜底追问不随对话场景变化

## 数据流

```
用户提问 → 模型生成回复 → 正则提取追问
                              ↓
                      ┌─ 有追问 → 相关性校验 ─┬─ 通过 → 前端展示
                      │                      └─ 不通过 → 上下文追问替换
                      └─ 无追问/格式残缺 → 上下文追问生成 → 前端展示
```

## 改动文件

| 文件 | 改动 |
|---|---|
| `PromptFactory.java` | 优化追问生成指令，提供相关性指南 |
| `ChatService.java` | 修复 Bug、新增 `validateFollowUpRelevance()`、改进默认追问生成 |
| `chat.js`（前端） | 改进解析容错性 |

---

## 1. Prompt 优化

### PromptFactory.java — 系统提示词

中文版追问段从：
```
结尾必须保留一个 `追问：` 段落，并提供 2 个编号追问。
```
改为：
```
`追问：` 段落要求：
- 必须包含且只包含 2 个编号追问
- 每个追问必须与当前分析的具体门店、指标或场景直接相关
- 追问应引导用户深化分析，例如：下钻到更细维度（城市/车型/时间）、对比其他门店、查看关联指标（商机/线索/任务）
- 禁止生成笼统的泛泛追问（如"你想了解什么"、"需要进一步分析吗"）
```

英文版同理。

### PromptFactory.java — 用户提示词（Grounded / Conversation）

中文版 grounded prompt 第9条从：
```
9. `追问：` 必须包含且只包含 2 个编号追问
```
改为：
```
9. `追问：` 必须包含且只包含 2 个编号追问，每个追问必须与当前分析的具体门店、指标或场景直接相关，禁止泛泛追问
```

英文版同理。

---

## 2. Bug 修复

### ChatService.java — repairPartialFollowUpQuestions

**问题**：当 FOLLOW_UP_QUESTIONS 段存在但格式不符合预期时（非空、非 1 条编号追问），方法直接抛 `IllegalStateException`，导致整个流式回复失败。

**修复**：移除异常抛出，改为静默降级——用默认追问替换残缺的 FOLLOW_UP_QUESTIONS 段。

```java
// 修改前：最后 fall through 到 throw
throw new IllegalStateException("Reply ended with an invalid follow-up section.");

// 修改后：返回用默认追问替换后的前缀
return prefix + "\n1. " + defaults.getFirst() + "\n2. " + defaults.getLast();
```

### ChatService.java — ensureFollowUpQuestions

**问题**：当 trimmed 已包含 FOLLOW_UP_QUESTIONS 标记但 `hasExactlyTwoFollowUpQuestions` 返回 false 且 `analyticsRequested` 为 true 时，直接返回原文本（可能格式残缺），前端解析不到追问。

**修复**：去掉 analytics 场景的特殊短路，统一走修复/替换路径。

---

## 3. 追问相关性校验器

### 新增方法：`validateFollowUpRelevance`

```
validateFollowUpRelevance(reply, followUps, language, analyticsTopic)
```

#### 3.1 提取主题关键词

从用户问题和模型回复中提取三类关键词：

| 类别 | 中文示例 | 英文示例 |
|---|---|---|
| 实体（门店名） | 从回复中匹配 `searchDealers` 返回的门店名 | Same |
| 核心指标 | 达成率、转化率、商机、线索、任务、活动、ROI、销量 | achievement rate, conversion, opportunity, lead, task, campaign, ROI, sales |
| 场景标签 | 目标达成、商机漏斗、活动效果、门店对标、销售跟进 | target achievement, opportunity funnel, campaign performance, dealer benchmark, sales follow-up |

#### 3.2 三层命中判定

对每条追问：

| 层级 | 条件 | 判定 |
|---|---|---|
| 强相关 | 命中具体实体名（门店/城市/车型），或命中核心指标词，或命中场景标签 | 通过 |
| 弱相关 | 只有泛词（"表现""情况""数据""怎么样""想了解"），无实体/指标/场景支撑 | 不通过 |
| 不相关 | 和当前分析主题毫无关联 | 不通过 |

泛词黑名单（中）：表现、情况、数据、怎么样、如何、方面、内容、信息、问题、了解
泛词黑名单（英）：performance, data, how, what, about, information, details, tell me

#### 3.3 替换策略

| 追问命中结果 | 处理 |
|---|---|
| 两条都强相关 | 全部保留 |
| 一条强相关、一条弱/不相关 | 保留强相关，替换弱/不相关的为上下文默认追问 |
| 两条都弱/不相关 | 全部替换为上下文默认追问 |

---

## 4. 上下文感知默认追问生成

### 新增方法：`buildContextualFollowUps`

根据 `analyticsTopic`（来自 `AnalyticsScenarioCatalog`）动态生成：

| 场景 | 中文默认追问 | English Default |
|---|---|---|---|
| 目标达成分析 | 1. {门店}的达成短板主要在哪个车型？ 2. 要不要对比同城市其他店的达成率？ | 1. Which model is dragging down {dealer}'s achievement? 2. Compare achievement rates across other city dealers? |
| 商机漏斗与转化 | 1. 哪个阶段的商机流失最严重？ 2. 要不要按销售顾问拆分转化率？ | 1. Which funnel stage has the highest drop-off? 2. Break down conversion rate by sales consultant? |
| 销售跟进 | 1. 逾期任务集中在哪些门店？ 2. 要不要查看任务完成率的月度趋势？ | 1. Which dealers have the most overdue tasks? 2. Check monthly task completion trends? |
| 活动效果 | 1. 本次活动 ROI 和去年同期比如何？ 2. 要不要看各门店的活动参与度排名？ | 1. How does this campaign ROI compare to last year? 2. Rank dealers by campaign participation? |
| 经营对标 | 1. 要不要下钻到车型维度对比？ 2. 这些门店的线索跟进时效如何？ | 1. Drill down by model dimension? 2. How is lead follow-up turnaround at these dealers? |
| 线索来源 | 1. 高意向线索主要来自哪个渠道？ 2. 要不要对比各门店的线索跟进速度？ | 1. Which channel generates the highest-intent leads? 2. Compare lead follow-up speed across dealers? |
| 经营活跃度 | 1. 活跃度最低的门店在哪个维度失分最多？ 2. 要不要对比活跃度和目标达成率的关系？ | 1. Which dimension drags down the lowest-activity dealers? 2. Correlate activity score with target achievement? |
| 通用（兜底） | 1. 你想继续看这个问题对应的商机、线索还是任务状态？ 2. 要不要我再按城市、门店或车型细分对比一层？ | 1. Do you want to drill into the related opportunities, leads, or tasks? 2. Should I break this down further by city, dealer, or model? |

如果回复中提取到了门店名，替换 `{门店}` 为实际名称。

---

## 5. 前端解析容错

### chat.js — parseFollowUpLines

- 增加去重：相同追问只保留一条
- 放宽匹配：支持模型输出 `追问：` 后紧跟的内容即使格式略有偏差也能提取
- 兜底渲染：如果后端返回的 followUps 为空数组但正文末尾疑似有追问段，尝试客户端提取

---

## 测试要点

1. 模型正常输出 2 条强相关追问 → 前端正常展示
2. 模型输出 2 条弱相关追问 → 后端替换为上下文默认追问
3. 模型输出 1 条强相关 + 1 条弱相关 → 后端混合替换
4. 模型输出格式残缺的 FOLLOW_UP_QUESTIONS → 后端修复而不是抛异常
5. 模型完全没输出 FOLLOW_UP_QUESTIONS → 后端追加上下文默认追问
6. 非分析类对话 → 使用通用默认追问
7. 流式响应过程中 → 追问在流式结束后完整展示
