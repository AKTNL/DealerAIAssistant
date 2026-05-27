# 门店经营活跃度 — 设计说明

**日期**: 2026-05-19
**状态**: 已确认

---

## 概述

新增"门店经营活跃度"（Dealer Business Activity）作为独立的分析类型，基于 Target 表中 4 个核心维度对门店进行综合活跃度评分与五级分区。

与现有的"销售跟进活跃度"（基于 Task 完成率）并行存在，互不替代。

---

## 核心字段

| # | 字段 | 业务含义 |
|---|------|---------|
| 1 | `AaKTarget__c`（asKTarget） | 月度目标台数 — 有稳定目标说明门店在正常运营 |
| 2 | `OpportunityCreateCount__c`（opportunityCreateCount） | 商机创建数 — 多说明门店在主动获客、录单 |
| 3 | `OpportunityWonCount__c`（opportunityWonCount） | 成交数 — 持续成交说明真正在卖车 |
| 4 | 当月是否有任何有效 Target 记录 | 数据覆盖度 — 连续有值说明长期活跃 |

---

## 统计窗口

**时间范围**：以当前业务月为截止月的最近 12 个自然月。窗口 = `[currentMonth - 11, currentMonth]`。

**月份判定基准**：基于 Target 的 `targetYear` / `targetMonth` 业务月份字段，不基于记录创建时间（`created_at` 等系统字段）。例如当前业务月为 2026-05，窗口为 2025-06 至 2026-05。

---

## 统计范围

### 常规活跃度排名

统计对象为在 12 个月窗口内存在至少一条有效 Target 记录的门店。各维度按月度判定达标情况。

### 休眠门店识别

当分析目标包含"休眠门店识别"时，统计对象扩展为**符合筛选范围条件的全部在册门店**（查询 Dealer 表），而非仅近 12 个月存在 Target 记录的门店。对于在窗口内无任何有效 Target 记录的门店，四维度均补齐为 0 分，归入"休眠"等级。

### 新开门店

新开门店仍按最近 12 个月统一口径评分——未开业月份视为无数据。评分结果标注：

> "观察期不足 X 个月，新店结果偏保守"

其中 X = min(12, 该门店首次出现有效 Target 记录的月份到当前业务月之间的月数)。

---

## "有数据"的定义

"有数据"指该月存在**有效的 Target 业务记录**。以下不计入：

- 软删除或逻辑删除的记录
- 标记为测试数据的记录
- Excel 导入失败或校验未通过的数据行

（注：当前系统仅持久化导入成功的记录，故 `dealer_targets` 表中数据默认均有效。此定义为后续可能引入的数据状态字段预留。）

---

## 评分公式（方案 A：月度达标计数法）

### 逐月判定规则

对每个门店，在 12 个月窗口内逐月判定 4 个维度是否"达标"：

| # | 维度 | 该月达标条件 |
|---|------|-------------|
| 1 | 有目标 | 该月**任意一条**有效 Target 记录的 `asKTarget >= 1` |
| 2 | 有商机 | 该月**任意一条**有效 Target 记录的 `opportunityCreateCount >= 1` |
| 3 | 有成交 | 该月**任意一条**有效 Target 记录的 `opportunityWonCount >= 1` |
| 4 | 有数据 | 该月存在**任何**有效 Target 记录 |

- 门店一个月内可能有多条 Target 记录（不同车型），**按月聚合去重判定，多车型不重复计分**
- 每个维度满分 12 分（12 个月 × 1 分/月）
- 总分 = 4 个维度得分之和，**满分 48 分**

### 五级分区

| 等级 | 分数区间 | 业务含义 |
|------|---------|---------|
| 非常活跃 | 40-48 | 全年稳定有目标、有商机、有成交，持续运营 |
| 活跃 | 30-39 | 大部分月份活跃，偶有断档 |
| 一般 | 20-29 | 约一半月份有数据/有产出，不稳定 |
| 低活跃 | 10-19 | 少数月份有数据，大量空档 |
| 休眠 | 0-9 | 几乎无数据，或仅个别月份有记录 |

---

## 数据模型变更

### Target 实体

新增字段：

```java
@Column(nullable = false)
private Integer opportunityCreateCount;
```

### 数据库

`dealer_targets` 表新增列：

```sql
ALTER TABLE dealer_targets ADD COLUMN opportunity_create_count INT NOT NULL DEFAULT 0;
```

### Excel 导入

现有导入逻辑已解析 `OpportunityCreateCount__c` 列但未持久化 — 补上实体映射即可。

### 历史数据兼容

旧数据 `opportunity_create_count` 默认为 0，"有商机"维度对历史数据得分偏保守。随 12 个月滚动窗口推进，新数据将自然覆盖。

---

## 实现结构

### 涉及文件

```
backend/
├── AnalyticsPlan.java                  # Scenario 枚举新增 DEALER_BUSINESS_ACTIVITY
├── AnalyticsScenarioCatalog.java       # 新增场景条目（中文："门店经营活跃度"）
├── Target.java                         # 新增 opportunityCreateCount 字段
├── Dealer.java / DealerRepository      # 休眠识别时查询全部在册门店
├── RuleBasedAnalyticsService.java      # 新增核心计算逻辑
├── schema.sql / 迁移脚本               # dealer_targets 表新增列
├── ExcelImportService.java             # 补上 opportunityCreateCount 映射
├── RuleBasedAnalyticsServiceTest.java  # 新增测试用例
```

> 不加入 `frontend/src/constants/sidebarFlows.js` 侧边栏 — 仅通过意图识别响应用户自然语言提问。

### 场景定义

| 项目 | 内容 |
|------|------|
| Scenario 枚举 | `DEALER_BUSINESS_ACTIVITY` |
| 中文名 | 门店经营活跃度 |
| 英文名 | Dealer Business Activity |
| 示例问法 | "哪些门店经营活跃度最高？" / "哪些门店处于休眠状态？" / "华东区门店活跃度排名" |
| 分析流程 | 确定范围（全部门店 or 有数据门店）→ 查询 Target 窗口数据 → 按门店分组 → 逐月四维判定 → 汇总评分 → 五级分区 → 输出排名 |
| 意图关键词 | 经营活跃度、门店活跃、活跃度排名、休眠门店、经销商活跃度、门店经营状况 |

### 核心方法

```
analyzeDealerBusinessActivity()         # 入口
  ├─ resolveScope()                     # 休眠识别→查 Dealer 表全量；否则仅查有 Target 数据的门店
  ├─ computeMonthlyMatrix()             # 逐月判定 4 维达标（返回 Map<Dealer, 12×4 boolean>）
  │    └─ 按 (targetYear, targetMonth) 过滤窗口
  │    └─ 按门店+月份 GROUP BY，去重多车型
  ├─ aggregateScores()                  # 汇总：每个维度 = 达标月数求和 → 0-48
  ├─ classifyTier(score)                # 五级分区
  └─ buildActivityResult()              # 组装报告/图表/属性/建议
```

### 报告输出

- **排名表**：按总分降序 — 门店名称、四维分项得分、总分、活跃等级、标注（新店/休眠）
- **Top N & Bottom N** 高亮
- **图表**：Mermaid 柱状图（按等级分组或 Top/Bottom N 对比）
- **建议**：休眠门店提示重点关注，低活跃门店指出短板维度

---

## 边界情况与数据质量

| 场景 | 处理方式 |
|------|---------|
| 门店 12 个月完全无 Target 记录 | 休眠识别时从 Dealer 表查出并补齐 0 分；常规排名中不出现 |
| 门店只有目标从未成交 | 目标维度有分，成交维度 0 — 标注"有任务无产出" |
| 门店仅 1-2 个月有数据 | 得分 4-8 → 休眠，标注"仅零星数据" |
| 多月多车型 | 按（门店 + 业务月）去重判定，不重复计分 |
| 新开门店（首次数据 < 12 个月前） | 统一口径评分，标注"观察期不足 X 个月，新店结果偏保守" |
| 全部门店均高分（区分度低） | `classifyCountQuality` 校验，提示区分度不足 |
| 空数据库 | 优雅降级，返回 NO_DATA 质量标记 |
| 无效/测试/删除记录 | 不计入"有数据"判定，当前系统默认所有已持久化记录有效 |

---

## 测试覆盖

| 测试用例 | 验证点 |
|---------|--------|
| 满分门店（12 月 × 4 维全达标） | 48 分 → "非常活跃" |
| 零分门店（完全无 Target 记录） | 0 分 → "休眠" |
| 休眠识别 — 无记录门店 | Dealer 表全量查询，补齐 0 分 |
| 临界值：10/20/30/40 分 | 分区边界正确 |
| 多月多车型去重 | 不重复计分 |
| 跨年滚动窗口 | 窗口起止月计算正确 |
| 仅部分维度达标 | 各维度独立计分 |
| 新店（数据 < 12 个月） | 标注观察期不足 |
| 空列表 / 无在册门店 | 无异常，降级返回 |
