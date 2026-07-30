# 数据导入与模拟业务数据 MVP 实施说明

## 第一阶段范围

第一阶段聚焦数据导入底座，不实现上传文件分析 UI/API，也不切换 PostgreSQL：

* 生成中型 MVP 规模 XLSX 模拟业务数据。
* 保持现有 5 个业务 sheet：`AE Target Data`、`Opportunity`、`Lead`、`Task`、`Campaign`。
* 继续通过 `APP_EXCEL_PATH` 或 classpath workbook 在后端启动时导入。
* 引入 import batch / active batch 概念。
* 查询、指标 API 和规则分析默认只读取 active batch。
* 导入质量报告展示 active batch 元数据和 sheet 质量统计。

## 模拟业务数据生成

生成器位于：

```text
backend/src/main/java/com/brand/agentpoc/service/importing/SampleWorkbookGenerator.java
```

默认输出路径：

```text
mockservice/SampleData/Sample Data - Xingyao MVP.xlsx
```

从 `backend/` 目录运行：

```powershell
mvn "-Dfrontend.skip=true" "-DskipTests" compile exec:java "-Dexec.mainClass=com.brand.agentpoc.service.importing.SampleWorkbookGenerator"
```

也可以显式传入输出路径：

```powershell
mvn "-Dfrontend.skip=true" "-DskipTests" compile exec:java "-Dexec.mainClass=com.brand.agentpoc.service.importing.SampleWorkbookGenerator" "-Dexec.args=../mockservice/SampleData/Sample Data - Xingyao MVP.xlsx"
```

生成器使用固定随机种子和固定 12 个月窗口，确保每次生成结果可复现。规模约为：

| Sheet | 目标行数 |
| --- | ---: |
| `AE Target Data` | 3,840 |
| `Opportunity` | 30,000 |
| `Lead` | 25,000 |
| `Task` | 60,000 |
| `Campaign` | 2,400 |

当前数据以正常业务数据为主，并包含少量受控边界数据：

* 可选日期缺失。
* 目标分母缺失。
* 未知车型、未知来源、未分配经销商。
* 少量缺失 ID 或非法概率，用于验证跳过行和质量报告。

## MVP 增强方向

后续应把生成器从“随机样例数据”升级为“带业务故事的模拟数据”。模拟数据需要支撑 Dashboard、分析回答、人工验收和题库回归，而不仅是填充数据库。

建议内置的业务故事：

| 故事 | 数据表现 | 验收问题 |
| --- | --- | --- |
| 高目标低达成门店 | 目标高、赢单不足、达成率排名靠后 | 哪些经销商目标达成率最低？ |
| 线索多但转化低 | 某些来源线索量高但转化率低 | 哪个线索来源转化率最差？ |
| 跟进积压影响赢单 | 待处理/逾期任务集中在低达成门店 | 哪些门店跟进任务积压严重？ |
| 活动投入高产出低 | 活动目标高但实际商机和赢单偏低 | 哪些活动投入高但产出低？ |
| 数据质量边界 | 缺失目标、未知来源、异常概率、空日期 | 当前数据质量有什么问题？ |

增强后的生成器应同时维护一份场景说明，列出：

* 生成参数和随机种子。
* 每类业务故事涉及的区域、门店、来源或活动。
* 推荐验收问题。
* 预期命中的核心指标和结论。
* 受控异常数据的原因和数量。

## Active Batch 行为

启动导入成功后，后端会创建一个新的 global import batch，并把该批次标记为当前 active batch。业务实体保存 `importBatchId`，查询和分析服务会过滤到 active batch。

`GET /api/data-status` 会返回最新导入状态，并额外包含 batch 元数据：

```json
{
  "batch": {
    "id": "startup-20260729050000-abc12345",
    "active": true,
    "scopeType": "GLOBAL",
    "scopeId": null,
    "activatedAt": "2026-07-29T05:00:00Z"
  }
}
```

## 第二阶段预留

上传文件分析暂不在第一阶段实现。后续实现时建议沿用同一条链路：

```text
用户上传 XLSX -> 创建用户/会话作用域 import batch -> 校验质量 -> 聊天分析读取该用户当前 batch
```

这样后续可以自然扩展到经销商、区域、集团级数据权限过滤。
