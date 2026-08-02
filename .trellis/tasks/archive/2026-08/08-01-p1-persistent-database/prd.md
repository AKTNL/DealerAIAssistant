# P1-1 持久化数据库和迁移

## Goal

把当前依赖 H2 内存库和 Hibernate `ddl-auto: update` 的后端底座升级为可持久化、可迁移、可按环境隔离的数据库方案，同时保留 H2 测试与本地 demo 能力。导入批次、业务数据和 active batch 语义必须在应用重启后保持一致，为后续真实数据接入和报告/Agent 能力提供稳定基础。

## What I already know

* 项目是 Spring Boot 3.4.5、Java 21、Spring Data JPA。
* 默认配置位于 `backend/src/main/resources/application.yml`，当前使用 H2 内存数据库和 `spring.jpa.hibernate.ddl-auto: update`。
* `application-prod.yml` 当前只关闭 H2 Console 和 Excel fallback，没有生产数据库连接配置。
* 业务实体包括 `Dealer`、`Target`、`Opportunity`、`Lead`、`Task`、`Campaign` 和 `ImportBatch`，均已使用 JPA 注解。
* 所有业务数据已带 `importBatchId`，`ImportBatchService` 已提供 active batch 读取和过滤能力；这是本任务需要保留并验证的现有契约。
* `ExcelImportService` 在应用启动时导入 workbook；若数据库已有业务数据，会跳过启动导入并将已有数据视为 active 数据。
* 文档路线图明确 P1 的验收包括：本地可用持久化数据库启动、schema 由迁移脚本创建、相同业务 ID 可在不同 import batch 共存、用户可见查询默认读取当前 active batch。
* 前一阶段已明确 PostgreSQL 迁移不能破坏 H2/test profile、可复现样例和准确率回归题库。

## Assumptions (temporary)

* 推荐生产数据库采用 PostgreSQL；MySQL 不在本任务同时支持，避免维护两套方言和迁移脚本。
* 推荐采用 Flyway；迁移脚本使用 SQL，版本由仓库管理，应用启动时自动执行。
* H2 继续用于单元测试、准确率回归和无外部依赖的本地 demo；PostgreSQL profile 用于持久化验收和生产形态验证。
* 本任务只建设数据库与迁移底座，不实现真实 CRM/DMS 接入、上传 UI/API、完整权限系统或业务模块重构。

## Open Questions

* 无。用户已确认按 PostgreSQL + Flyway 方案推进。

## Requirements (evolving)

* 增加 PostgreSQL 运行时依赖和可配置的 JDBC、用户名、密码、schema 参数。
* 增加 Flyway 依赖和首个基线迁移，覆盖当前实体表、必要索引、约束和 `import_batches` 表。
* 迁移脚本必须允许同一业务 ID 在不同 `import_batch_id` 下共存；业务 ID 不得继续使用跨批次唯一约束。
* 生产 profile 必须关闭 Hibernate 自动更新，交由 Flyway 管理 schema。
* H2 profile/test 配置继续可用，测试可显式使用 `create-drop` 或等价隔离策略，不要求本地测试依赖 PostgreSQL。
* 应用启动时必须能连接 PostgreSQL、执行迁移并完成 workbook 导入；重启后保留数据，不重复导入已有业务数据。
* active batch 查询、数据状态和现有分析/查询行为必须保持兼容；历史批次可共存，但用户可见查询只读 active batch。
* 文档必须说明启动方式、必需环境变量、profile 选择、迁移回滚/前向修复边界和本地 H2 fallback。

## Acceptance Criteria (evolving)

* [x] PostgreSQL profile 使用环境变量配置连接信息，且生产配置将 Hibernate 切换为 `validate`。
* [x] 首次启动由 Flyway 创建完整 schema，H2 上的 Flyway migration 可通过 Hibernate schema validation。
* [ ] 在配置有效 PostgreSQL 凭据后验证应用重启保留数据、`import_batches` 和 active batch 状态；当前机器 PostgreSQL 服务需要认证凭据，代码路径和手工步骤已具备。
* [x] 同一业务 ID 可在两个不同 import batch 中共存，迁移不会因全局唯一约束失败。
* [x] 所有用户可见查询、Dashboard、分析和数据状态继续通过现有 active batch 语义读取数据。
* [x] H2 测试与现有准确率回归仍然通过。
* [x] 迁移、profile 配置和运行说明有自动化验证或明确的手工验收步骤。

## Definition of Done (team quality bar)

* 添加/更新数据库配置、迁移脚本、测试和文档。
* 后端 PMD、测试和必要的 PostgreSQL 验收命令通过。
* 不改变现有 API 的业务语义；如有配置或启动行为变化，文档同步更新。
* 明确失败时的处理、迁移前向修复策略和 H2 回退路径。

## Out of Scope (explicit)

* 不接入真实 CRM、DMS、CDP 或数据仓库。
* 不实现文件上传、用户/会话级 batch 归属和完整权限过滤。
* 不同时支持 PostgreSQL 和 MySQL 的生产迁移脚本。
* 不在本任务重写所有实体或进行完整模块化拆分。
* 不把 H2 从测试和本地 demo 中移除。

## Technical Approach

采用 PostgreSQL + Flyway 的生产路径，使用 profile/env 覆盖数据源配置；保留默认 H2 作为开发/测试路径。首个 Flyway migration 以当前 JPA 实体和数据库约定为基线，显式创建表、字段、索引和批次相关约束。后续 schema 修改只新增版本迁移，不再依赖生产环境的 Hibernate 自动更新。应用层继续通过现有 repository 和 `ImportBatchService` 读取 active batch。

## Decision (ADR-lite)

**Context**: H2 内存库无法在应用重启后保留导入批次和业务数据，`ddl-auto: update` 也无法审计 schema 变更；P1 需要进入真实试点形态，同时不能破坏现有测试和 demo。

**Decision**: 生产形态采用 PostgreSQL + Flyway；H2 作为 test/demo 保留；生产 profile 将 Hibernate DDL 设置为 `validate`，由 Flyway 在启动时创建和升级 schema。

**Consequences**: 获得持久化、可审计、可回放的 schema 版本和明确的环境隔离；代价是本地生产形态验收需要 PostgreSQL 实例，且后续 schema 变化必须维护迁移脚本。

## Expansion Sweep

### Future evolution

* 迁移目录和表结构为后续数据质量报告、数据源登记、报告历史和用户/会话级 batch 归属预留扩展空间。
* 生产连接配置保持环境变量化，避免把部署方式绑定到本地 Docker 或个人机器。

### Related scenarios

* 启动导入、数据状态、Dashboard、聊天分析和准确率回归必须继续共享 active batch 语义。
* H2 测试路径与 PostgreSQL 生产路径的 schema 变更应保持同一实体契约；新增字段先改迁移，再改实体/服务。

### Failure and edge cases

* 数据库不可用或迁移失败时，生产 profile 应快速失败并给出可诊断日志，不应悄悄回退到 H2 或内置样例。
* 重复启动不得重复导入已有批次；同一业务 ID 跨批次存在时不能因唯一约束冲突而启动失败。
* 迁移失败后的修复采用新的前向 migration，保留 Flyway 历史，不直接修改已应用的版本文件。

## Research References

* [`research/postgresql-flyway.md`](research/postgresql-flyway.md) — PostgreSQL、Flyway 与 Liquibase 的方案比较及本项目映射。

## Technical Notes

* Route reference: `docs/07-生产化升级路线图.md` 的 P1「持久化数据库和迁移」。
* Data model reference: `docs/03-数据重构需求.md` 的存储与迁移、当前实体和 active batch 约定。
* Existing database spec: `.trellis/spec/backend/database-guidelines.md`。
* Existing entities: `backend/src/main/java/com/brand/agentpoc/entity/`。
* Existing import flow: `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java`。
* Existing active-batch flow: `backend/src/main/java/com/brand/agentpoc/service/ImportBatchService.java`。
* Hibernate naming note: `Target.asKTarget` maps to `asktarget` under the current physical naming strategy; the migration and validation test intentionally preserve that existing contract.
