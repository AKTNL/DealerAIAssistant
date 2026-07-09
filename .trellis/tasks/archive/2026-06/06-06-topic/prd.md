# 修复准确率测试题库 17/51 低准确率问题

## Goal

将 51 道测试题库的准确率从 17/51 (33%) 提升到 45+/51 (88%+)，修复后端 topic 检测、边界检测、线索分析等核心逻辑缺陷。

## What I already know

### 根因分析（已完成）

对 51 道题进行了完整的 pipeline 模拟（detectTopic → detectScope → 分析函数），34 道错误分类如下：

**A. Topic 检测路由错误 (6 题)** — `RuleBasedAnalyticsService.detectTopic()` 关键词匹配覆盖不全：

| Row | 题目 | 期望 | 实际 |
|-----|------|------|------|
| 4 | 全国范围内赢单数量最多的经销商是谁？ | TARGET | BENCHMARK |
| 7 | 全国范围内哪个车型赢单最多？ | TARGET | BENCHMARK |
| 19 | 商机主要来源于哪些线索来源？ | OPPORTUNITY | LEAD_SOURCE |
| 20 | 哪个线索来源的商机赢单率最高？ | OPPORTUNITY | LEAD_SOURCE |
| 22 | 哪个车型赢单率最高？ | OPPORTUNITY | BENCHMARK |
| 25 | 购车周期年龄主要集中在哪个区间？ | OPPORTUNITY | BENCHMARK |

**B. 缺少"数据概况"场景 (1 题)** — 无跨域汇总分析能力

**C. 线索管理分析维度不足 (8 题)** — `analyzeLeadSource` 只做来源分布+转化率，但题目要求：
- 线索总数和状态分布（New/Qualified）
- 意向车型分布
- 按经销商分组（线索量、转化率）
- 特定来源（XY Online/XY）的转化详情

**D. 边界检测缺口 (1 题)** — `mentionsUnknownDemoEntity` 只检测 "客户X"，不检测未知经销商

**E. 边界问题 (5 题)** — ChatService 层应拦截但部分可能未正确拦截

### 已验证
- 样例数据与标准答案数值完全一致（total_target=5928, won=6463, rate=109.0% 等）
- 数据层无问题，根因在逻辑层

## Requirements

1. **修复 detectTopic 关键词覆盖**：补充 "赢单"、"最多"、"来源"（在商机语境）、"年龄"/"购车周期" 等关键词
2. **新增数据概况场景**：处理跨域汇总查询（经销商数/客户数/商机数/活动数）
3. **增强线索分析多维能力**：支持按状态、车型、经销商分组查询
4. **扩展未知实体检测**：支持未知经销商/门店的检测
5. **修复边界问题路由**：确保 out-of-scope/greeting/unknown-entity 在 ChatService 层正确拦截

## Acceptance Criteria

- [ ] 51 道题库中 target 达成率 >= 88% (45/51)
- [ ] Topic 检测 6 道错误全部修复
- [ ] 数据概况题（Row 2）正确回答
- [ ] 线索管理 8 道题正确回答（含状态分布、车型分布、经销商分组）
- [ ] 边界题 6 道正确拦截/回答
- [ ] 不影响已有正确的 17 题

## Definition of Done

- 修改的 Java 文件编译通过
- 51 道题重新测试，准确率 >= 88%
- 不引入新的回归问题

## Out of Scope

- 不重构整个分析框架
- 不修改前端
- 不修改数据导入逻辑
- 不外接 LLM 测试（本次聚焦 rule-based fallback 路径）

## Decision (ADR-lite)

**Context**: 需要修复 detectTopic 关键词匹配导致的 6 道路由错误 + 线索分析维度不足等
**Decision**: 方案 A — 在现有关键词基础上打补丁，扩展关键词覆盖 + 调整优先级
**Consequences**: 
- 改动小、风险低、不破坏现有正确路由
- 关键词方法有天花板，未来可演进到方案 B（语义意图分类）作为长期方向

## Implementation Plan

### PR1: 修复 detectTopic 关键词覆盖（6 题）
- 增加 "赢单" → TARGET 匹配（Row 4, 7）
- 增加 "商机" + "来源" 组合检测 → OPPORTUNITY 优先于 LEAD_SOURCE（Row 19, 20）
- 增加 "赢单率" → OPPORTUNITY 匹配（Row 22）
- 增加 "购车周期"、"年龄"、"区间" → OPPORTUNITY 匹配（Row 25）
- 新增 `analyzeDataOverview` 处理跨域汇总（Row 2）

### PR2: 增强线索分析多维能力（8 题）
- 扩展 `analyzeLeadSource` 支持按状态分组（New/Qualified 分布）
- 支持按意向车型分组（productModel）
- 支持按经销商分组（dealerName）
- 支持特定来源过滤（XY Online, XY）
- 支持转化率排名

### PR3: 修复边界检测（6 题）
- 扩展 `mentionsUnknownDemoEntity` 支持未知经销商/门店检测
- 确保 out-of-scope/greeting 在 ChatService 层正确拦截

## Technical Notes

- 主改文件：`RuleBasedAnalyticsService.java` (~4000 行，重点修改 detectTopic、analyzeLeadSource、新增 analyzeDataOverview)
- 辅助改动：`ChatService.java`（mentionsUnknownDemoEntity 扩展）
- 样例数据位置：`mockservice/SampleData/Sample Data - 星曜汽车.xlsx`
- 题库位置：`mockservice/DealerAIAssistant_准确率测试题库.xlsx`
