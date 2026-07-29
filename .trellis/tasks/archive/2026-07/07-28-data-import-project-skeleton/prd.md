# brainstorm: 数据导入与项目骨架MVP

## Goal

把当前经销商 AI 分析助手 POC 收敛成可实现的项目路线：优先稳定数据口径、样例数据规模、导入链路和持久化策略，再决定是否扩展登录与生产化框架能力。

## What I already know

* 当前项目已有 Vue 3 + Vite 前端、Spring Boot 3.4 + Java 21 后端。
* 前端已有登录页和 `useAuth` 相关测试；后端已有 `POST /api/auth/verify`、session token、API key filter。
* 当前数据库是 H2 内存库，`spring.jpa.hibernate.ddl-auto: update`，没有 Flyway/Liquibase。
* 当前数据启动时由 `ExcelImportService` 从 `backend/src/main/resources/Sample Data.xlsx` 导入。
* 当前导入只处理 workbook/sheet 模式，依赖 Apache POI；还不是独立的 CSV/XLSX 文件导入管道。
* 数据重构文档已把 P0 定义为真实数据源清单、字段映射表、KPI 口径表、缺失值策略；持久化数据库方案是 P1。
* 真实项目接入前不能删除可复现样例和准确率题库。

## Assumptions (temporary)

* 近期目标是把 POC 变成更可靠的 MVP，不是一次性建设完整数据平台。
* 暂时没有真实 CRM/DMS/CDP 接口可接入，所以需要更丰富、更可控的样例数据。
* PostgreSQL 值得做，但应在字段映射、导入幂等和数据质量报告边界明确后落地。
* 登录页已有基本能力，当前更需要补齐权限模型/数据可见范围，而不是先重做 UI。

## Recommended MVP Order

1. 数据口径与样例数据扩展
   * 明确 6 个分析场景所需字段、分子/分母、缺失值、未知分类和跳过规则。
   * 生成更大规模、可复现、带边界场景的样例数据。
   * 保留现有准确率题库，并让新增样例能覆盖更多问题。

2. 导入管道重构
   * 把当前 `ExcelImportService` 拆成“读取文件 -> 标准化 DTO -> 校验/质量报告 -> 持久化”的管道。
   * 先支持 XLSX；CSV 可作为同一管道的第二输入适配器。
   * 定义重复导入策略：覆盖、追加、按 batch/version 切换，或按业务 ID upsert。

3. PostgreSQL 迁移
   * 加 PostgreSQL runtime dependency 和 profile/env 配置。
   * 引入 Flyway 或 Liquibase 记录 schema 版本。
   * 保留 H2/test profile 用于单元测试和快速 demo。
   * 给导入批次、数据源、质量报告留表结构或可追踪字段。

4. 登录与权限增强
   * 当前登录页不作为第一优先级重写。
   * 等数据可见范围明确后，再扩展用户、角色、集团/区域/门店权限过滤。
   * 若只是 demo，保留 access key 登录即可。

## Decision (ADR-lite)

**Context**: 用户在“数据导入优先 / PostgreSQL 优先 / 产品壳优先”中选择了数据导入优先。

**Decision**: 第一阶段先做数据口径、样例数据扩展和导入管道重构；PostgreSQL 迁移作为紧随其后的生产化步骤；登录页暂不重做，只保留后续权限模型扩展入口。

**Consequences**: 这样可以先保证分析结果可信、样例可复现、导入质量可见，避免过早把不稳定字段和 KPI 口径固化到 PostgreSQL schema 或权限模型中。

### Import Version Strategy

**Context**: 重复导入策略会影响导入服务、数据查询、质量报告、PostgreSQL 迁移和回滚能力。

**Decision**: 采用“批次版本化 + active batch”作为目标设计。每次导入生成一个 import batch，数据校验和质量统计通过后再切换为当前活跃批次；查询和分析默认只读取 active batch。

**Consequences**: MVP 可以保留历史批次、支持回滚、追踪数据来源和导入质量；代价是实体或查询层需要携带 batch 过滤能力，导入服务也要维护 batch 状态。

### Sample Data Artifact

**Context**: 样例数据既要验证导入链路，也要便于业务同学检查字段、口径和异常样本。

**Decision**: 第一阶段继续生成 XLSX workbook，保持现有 sheet 结构和字段命名，优先复用当前 `ExcelImportService` 的 workbook 入口。CSV 文件集暂不作为第一阶段交付物。

**Consequences**: XLSX 更贴近当前 POC 和业务验收方式，改动范围较小；CSV 适配器可以等导入管道抽象稳定后再补。

### Sample Data Scale

**Context**: 数据规模需要足够验证导入、分析口径和边界情况，但第一阶段不应把重点变成百万行压力测试。

**Decision**: 采用中型 MVP 规模：约 50-100 家经销商、12 个月业务数据、总量 10 万级行。

**Consequences**: 该规模可以验证导入耗时、JPA 查询、分析聚合和数据质量报告，同时仍适合本地开发、测试和业务验收。

### Data Quality Mix

**Context**: 样例数据既要支持正常业务分析，也要验证导入质量报告、缺失值策略和边界处理。

**Decision**: 采用“干净数据 + 受控边界数据”。大部分数据保持业务一致性，少量数据包含缺失值、未知分类、未分配分类、真实零值和应被跳过的异常行。

**Consequences**: 分析体验仍以正常业务结果为主，同时可以验证质量报告和口径边界；生成脚本应让边界/异常比例可配置或至少集中定义。

### Import Entry

**Context**: 当前后端已支持启动时从 `APP_EXCEL_PATH` / classpath workbook 导入。用户也希望后续增加上传文件并直接分析上传数据的能力。

**Decision**: 第一阶段继续采用启动时配置路径导入，作为稳定的本地/demo 底座；上传文件分析作为同一数据导入体系上的后续功能纳入设计，但不阻塞第一阶段导入底座。

**Consequences**: 可以先复用现有启动导入路径和测试，再把上传功能做成“创建 import batch -> 校验质量 -> 设为分析数据源”的扩展入口。

### Uploaded File Scope

**Context**: 后续产品可能需要按经销商、区域或集团限制数据可见范围，用户上传的文件也不应默认替换全局数据。

**Decision**: 上传文件分析按当前用户/会话作用域设计。上传 XLSX 后生成用户/会话可见的 import batch，聊天分析默认使用该用户当前选择或最新上传的数据批次；未来可扩展到经销商/区域/集团权限过滤。

**Consequences**: 该方向更贴近正式产品和数据权限要求，但比全局 active batch 更复杂，需要后续在认证、batch 归属、查询过滤和数据可见范围上补设计。

### Upload Implementation Phase

**Context**: 上传分析会同时影响前端上传体验、后端文件接口、用户/会话数据隔离、batch 归属和查询过滤。若放进第一阶段，会稀释数据导入底座建设。

**Decision**: 第一阶段仅预留上传分析所需的数据作用域和 batch 设计，不实现上传文件分析 UI/API。上传 XLSX 并直接分析作为第二阶段功能。

**Consequences**: 第一阶段范围更聚焦，可以先稳定样例生成、启动导入、质量报告和 active batch；第二阶段实现上传时，沿用同一 import batch 和数据权限模型。

## Open Questions

* None. 用户已确认第一阶段按当前 MVP 范围进入实现。

## Requirements (evolving)

* MVP 应先保证数据可信、可追溯、可复现。
* 第一阶段采用数据导入优先路线：先完成字段口径、样例生成、导入质量和导入管道，再推进 PostgreSQL 和登录权限增强。
* 重复导入采用批次版本化与 active batch 策略；查询和分析默认只读取 active batch。
* 第一阶段样例数据以 XLSX workbook 交付，保持当前必需 sheet 结构：`AE Target Data`、`Opportunity`、`Lead`、`Task`、`Campaign`。
* 第一阶段样例数据采用中型 MVP 规模，目标为约 50-100 家经销商、12 个月、总量 10 万级行。
* 样例数据生成采用正常业务数据为主、受控边界数据为辅的策略，需要覆盖缺失值、真实零值、未知分类、未分配分类和异常/跳过行。
* 第一阶段导入入口继续使用启动时配置路径导入。
* 上传 XLSX 并直接分析上传数据是后续功能方向，应复用同一 import batch、质量报告和 active data source 体系。
* 上传文件分析按当前用户/会话作用域设计，后续可扩展到经销商、区域、集团级数据权限。
* 第一阶段仅预留上传分析架构，不实现上传文件分析 UI/API；上传分析进入第二阶段。
* 导入失败、回退、导入质量问题必须对后端和前端可见。
* PostgreSQL 迁移不能破坏现有本地 demo 和测试体验。
* 登录增强应围绕数据权限设计，而不是单纯做页面。

## Acceptance Criteria (evolving)

* [ ] 有一份明确的样例数据生成方案和字段口径表。
* [ ] 大样例 XLSX workbook 能被当前或重构后的导入链路导入。
* [ ] 大样例数据达到中型 MVP 规模，并能在本地开发环境可接受时间内导入。
* [ ] 导入质量报告能体现受控边界数据的规范化、跳过和问题统计。
* [ ] 上传文件分析的范围和数据生命周期有明确设计决策。
* [ ] 第一阶段不包含上传 UI/API，但 batch/scope 设计不会阻碍第二阶段用户/会话级上传分析。
* [ ] 重复导入/数据版本策略有明确决策，并体现在导入服务设计中。
* [ ] 导入质量报告能统计每个 sheet/文件的处理、导入、规范化、跳过和问题原因。
* [ ] PostgreSQL profile 可启动，schema 由迁移工具管理。
* [ ] H2/test profile 仍可运行现有测试。
* [ ] 登录/权限是否进入 MVP 有明确决策。

## Definition of Done (team quality bar)

* Tests added/updated (unit/integration where appropriate)
* Lint / typecheck / CI green
* Docs/notes updated if behavior changes
* Rollout/rollback considered if risky

## Out of Scope (explicit)

* 本阶段不直接建设完整数据平台。
* 本阶段不默认接入真实 CRM/DMS。
* 在字段口径未确认前，不重写所有实体。
* 不优先重做登录页视觉体验。
* 第一阶段不实现上传文件分析 UI/API。
* 第一阶段不直接实现完整用户、经销商、区域、集团级权限系统。

## Technical Approach

第一阶段实现数据导入底座：

* 新增或整理可复现样例数据生成脚本，输出中型 MVP 规模 XLSX workbook。
* 保持现有 workbook sheet 结构，继续支持启动时配置路径导入。
* 引入 import batch 概念，支持 active batch 目标设计，并为后续用户/会话作用域上传分析预留归属字段或扩展点。
* 扩展导入质量报告，覆盖受控边界数据的规范化、跳过和问题统计。
* 暂不实现 PostgreSQL、上传 UI/API、完整权限系统，但设计不阻碍后续接入。

## Implementation Plan (small PRs)

* PR1: 样例数据生成与字段口径文档
  * 生成中型 MVP XLSX workbook。
  * 明确数据分布、边界比例、字段映射和 KPI 口径。
  * 保留现有小样例和准确率题库。

* PR2: 导入 batch 与质量报告底座
  * 将导入流程整理为可追踪的 import batch。
  * 查询/分析默认读取 active batch。
  * 质量报告暴露导入来源、批次、处理/导入/跳过/问题统计。

* PR3: 回归验证与后续上传预留
  * 更新导入、分析、数据状态相关测试。
  * 验证中型 workbook 可导入，现有 H2/test 流程不破。
  * 在设计/代码中预留后续用户/会话级上传分析扩展点。

## Technical Notes

* Existing backend config: `backend/src/main/resources/application.yml`
* Existing import service: `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java`
* Existing data plan: `docs/03-数据重构需求.md`
* Existing workbook samples:
  * `mockservice/SampleData/Sample Data - 星曜汽车.xlsx`
  * `backend/src/main/resources/Sample Data.xlsx`
* MVP workbook generator:
  * `backend/src/main/java/com/brand/agentpoc/service/importing/SampleWorkbookGenerator.java`
  * default output: `mockservice/SampleData/Sample Data - Xingyao MVP.xlsx`
