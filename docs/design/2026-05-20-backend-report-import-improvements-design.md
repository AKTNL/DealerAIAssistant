# 后端报告与导入改进设计

## 背景

`docs/改进方案.md` 的本轮范围只包含后端可验证三项：

- 在最终分析前展示清晰的接口调用链，让用户看到取数与计算过程。
- 精简分析报告，删除末尾 `数据汇总`，把重点放在问题诊断和未来改进。
- 在代码导入层处理 Excel 缺失值，尤其是 campaign 表 `活动` / `campaignType` 为空时不能漏读整行。

本轮不修改 Excel 文件，不做前端配色调整，不新增前端 SSE 协议。

## 输出结构

中文经营分析报告的二级标题顺序必须是：

1. `## 接口调用链`
2. `## 核心结论`
3. `## 数据支撑`
4. `## 经营分析`
5. `## 问题诊断与解决`
6. `## 改进建议`
7. `追问：`

英文经营分析报告的二级标题顺序必须是：

1. `## Interface Call Chain`
2. `## Conclusion`
3. `## Data Support`
4. `## Short Analysis`
5. `## Problem Diagnosis & Solutions`
6. `## Improvement Suggestions`
7. `FOLLOW_UP_QUESTIONS:`

`数据汇总`、`Data Summary` 以及任何同义末尾汇总段都不再允许出现。客观数字、表格、图表只保留在 `数据支撑` / `Data Support` 中；诊断和建议只能引用这些已展示事实，不再额外创造“总览”“尾注”“摘要”。

## 调用链生成机制

调用链由 `RuleBasedAnalyticsService` 动态生成，而不是写死为固定文案。

推荐实现：

- `plan(...)` 在完成 `detectTopic(...)`、`detectScope(...)`、`mapScenario(...)` 和实际场景分析后，调用一个新的私有方法生成 4-5 行 Markdown。
- 该方法基于 `AnalysisTopic`、`AnalyticsPlan.Scenario`、`AnalysisScope`、`AnalyticsScenarioCatalog.ScenarioWorkflow`、`ScenarioResult.traceSteps()` 和当前 `Clock` 拼装调用链。
- 默认把 `Clock.instant()` 按应用时区格式化为 `yyyy-MM-dd`，但封装为独立 formatter/helper，后续可扩展为 `yyyy-MM-dd HH:mm:ss`，避免用户在需要更高时间精度时误解“当前时间”的基准。
- 生成结果作为变量注入到 `buildEnrichedReply(...)`、`noDataResult(...)`、`lowConfidenceResult(...)` 等最终回复构建路径，确保正常、低置信和无数据结果都有同样的前置调用链。
- 每行写用户可理解的业务执行步骤，避免暴露隐藏推理、密钥、内部异常栈或不必要的实现细节。

中文示例结构：

```markdown
## 接口调用链

1. 获取当前时间：2026-05-20，作为本月/近期等时间范围的计算基准。
2. 实体与意图解析：识别为目标达成分析，范围为 2026年5月 / 北京 / 经销商AJ(沈阳)。
3. 主数据编码映射：命中经销商 `7036`，使用该编码过滤目标与商机数据。
4. 数据调用：读取 Target、Opportunity 两类数据，按门店、月份和车型过滤。
5. 聚合对比：对比 asKTarget 与 opportunityWonCount，计算目标达成率并排序。
```

没有命中特定经销商时，第 3 行应说明“未命中单一经销商，按城市/集团/全样本范围汇总经销商主数据”。不同场景的第 4-5 行应根据实际 Data Category 变化，例如 Campaign、Lead、Task、Target、Opportunity。

## ChatService 校验机制

`ChatService` 不能再用严格的 `List.equals(...)` 或标题字符串全匹配校验模型输出。模型可能输出轻微标题变体，例如 `## 1. 接口调用链` 或标题前后有空格。

推荐实现：

- 抽取二级标题时保留原始标题文本。
- 用正则逐项校验顺序，而不是直接字符串相等。
- 标题允许可选序号、点号、顿号和前后空格，但不允许语义替换成其他章节。

中文调用链标题正则示例：

```regex
^##[\s\h]*(?:1[\s\h\.、]*)?接口调用链[\s\h]*$
```

英文调用链标题正则示例：

```regex
^##[\s\h]*(?:1[\s\h\.]*)?Interface Call Chain[\s\h]*$
```

Java 实现示例：

```java
// 开启多行模式，并将 \s 扩展为包含常见的特殊空白符
Pattern pattern = Pattern.compile("^##[\\s\\h]*(?:1[\\s\\h\\.、]*)?接口调用链[\\s\\h]*$", Pattern.MULTILINE);
```

其他二级标题也采用同样模式，例如允许 `## 2. 核心结论`，但不允许 `## 总结` 替代 `## 核心结论`。校验仍需确认 `数据支撑` 表格没有被模型改写，且追问数量仍为 2 个。

## PromptFactory 约束

`PromptFactory` 的系统提示词和 grounded prompt 都要同步新结构。

需要加入强负向约束：

```text
严禁修改以下二级标题的任何文字，严禁添加图标或改变 Markdown 层级，否则系统将无法识别。
```

英文对应：

```text
Do not change any required level-2 heading text, do not add icons, and do not change the Markdown heading level; otherwise the system cannot recognize the response.
```

同时明确 `数据支撑` 与报告末尾汇总的边界：

```text
请将所有客观数字、表格纯粹保留在【数据支撑】中。严禁在【数据支撑】后方或报告末尾创造任何形式的“数据总览”、“总计尾注”或“摘要段落”。
```

英文对应：

```text
Keep all objective numbers and tables only in Data Support. Do not create any data overview, total note, summary block, or trailing recap after Data Support or at the end of the report.
```

Grounded model prompt 必须把动态生成的 `接口调用链` 作为事实上下文传入，要求模型原样保留其事实含义，不得改写数字、范围、经销商编码和调用类别。

## Excel 导入默认值

导入层只在缺少持久身份或最低时间上下文时跳过行。非关键维度缺失时填默认值并保留行。

Campaign 解析规则：

- `campaignId`：仍必填。
- `createdDate`：仍必填。
- `campaignType`：为空时填 `"0"`。
- `dealerCode`：为空时填 `"未分配"`。
- `dealerName`：为空时填 `"未分配"`。
- `productModel`：为空时填 `"未知"`。
- `actualOpportunityCount`：为空时填 `0`。
- `totalNewCustomerTarget`：为空时填 `0`。

当前代码事实校验：

- `Campaign.campaignType` 是 `String` 字段，只有 `nullable = false` 和长度约束，没有枚举字典约束。
- `AnalyticsApiService`、`DataQueryService`、`RuleBasedAnalyticsService` 对 `campaignType` 只做字符串过滤、排序、分组或展示，因此 `"0"` 可被现有路由作为普通类别处理。
- `未分配` 已在现有 campaign/lead 导入逻辑中作为 `dealerCode` 和 `dealerName` 默认值使用；`deriveCity("未分配")` 会得到非空城市值，满足 `Dealer` / `Campaign` 的非空字段约束。

如果后续新增正式字典或枚举，应把这些默认值迁入字典并补充兼容测试。

## 导入日志

`ExcelImportService` 维护一个服务级 `AtomicInteger normalizedRowsCount`，用于统计本次 workbook 导入中发生过非关键字段默认化的行数。由于 service 是 Spring 单例，该计数器必须在每次 `importWorkbook(...)` 开始时重置为 `0`，避免多次导入测试或未来手动导入时累计旧数据。

对被默认化的字段记录可排查日志，不向前端报错。

建议格式：

```text
[Import-Normalization] Row 12: campaignType is blank, defaulting to '0'
[Import-Normalization] Row 12: dealerCode is blank, defaulting to '未分配'
```

单行默认化日志级别建议使用 `debug`。在整个 Workbook 解析结束的 `finally` 块中，无论成功、失败或回退，都无条件输出一条 `info` 汇总日志：

```text
[Import-Normalization] Campaign import completed. Total rows processed: 1000, rows with defaulted non-critical fields: 42.
```

其中 `Total rows processed` 统计 campaign sheet 中实际遍历的非空数据行数量，`rows with defaulted non-critical fields` 统计至少发生过一次默认化的 campaign 行数量。

## 测试计划

正向测试（Rule）：

- 验证 `RuleBasedAnalyticsService` 输出完全包含 7 个新标头。
- 验证标题顺序严格正确：`接口调用链` 在 `核心结论` 前，`改进建议` 在 `追问` 前。
- 验证输出不包含 `数据汇总` / `Data Summary`。

正向测试（Model）：

- 模拟一个合规 LLM 响应，标题可包含轻微序号形式，例如 `## 1. Interface Call Chain`。
- 验证 `ChatService` 通过校验并保留模型响应。

负向测试（Model 降级）：

- 模拟 LLM 响应包含 `数据汇总` / `Data Summary`，验证 `ChatService` 判定非法并回退到 Rule fallback。
- 模拟 LLM 响应缺失 `接口调用链` / `Interface Call Chain`，验证同样回退。

导入测试：

- 构造一行 campaign mock workbook 数据：合法 `campaignId`、合法 `createdDate`，但 `campaignType` 和 `dealerCode` 为空。
- 验证 `ExcelImportService` 解析并保存 `Campaign`，字段符合默认值：`campaignType = "0"`、`dealerCode = "未分配"`、`dealerName = "未分配"`、计数字段默认为 `0`。
- 验证导入归一化计数以单次 workbook 为边界，不因多次运行或多次测试复用 service 实例而累计旧计数。

## 验收标准

- 未配置模型时，规则引擎返回的新报告符合 7 段结构，并先展示动态调用链。
- 配置模型时，Prompt 要求模型保留新结构，并禁止创造任何尾部数据汇总。
- 模型输出轻微标题序号不导致误杀；缺调用链或出现数据汇总必须回退。
- Campaign 缺失非关键字段不会导致整行漏导入，日志保留默认化线索。
- 所有改动均通过聚焦后端测试。
