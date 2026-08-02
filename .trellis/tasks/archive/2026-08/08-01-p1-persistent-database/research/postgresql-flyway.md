# PostgreSQL + Flyway 研究记录

## Scope

比较本项目把 H2/`ddl-auto: update` 升级为生产持久化方案时的数据库和 schema migration 选择。

## Comparable patterns

### Spring Boot + Flyway

* Spring Boot detects Flyway on the classpath and runs versioned migrations during application startup.
* SQL migrations live under `classpath:db/migration` and use ordered names such as `V1__baseline.sql`.
* Production should use `ddl-auto: validate` (or `none`) so Hibernate checks the mapped schema without mutating it.
* Migration history is stored in Flyway's schema history table, making applied versions explicit and repeatable across environments.
* Destructive changes should be delivered as forward migrations; rollback is usually a new corrective migration rather than an automatic down migration.

### PostgreSQL as the production database

* PostgreSQL provides durable storage, transactions, indexes, and a mature JDBC/JPA integration.
* PostgreSQL-specific SQL can be kept isolated in the migration directory; the application can still use standard JPA repositories for normal access.
* A separate H2 test profile remains useful for fast tests, but PostgreSQL integration tests should verify migration SQL and production constraints because H2 compatibility mode cannot prove every PostgreSQL behavior.

### Liquibase alternative

* Liquibase can express migrations as XML/YAML/JSON or SQL and offers explicit rollback blocks.
* It adds more metadata and format choices than this repository currently needs; the project already uses straightforward JPA entities and has no migration history to preserve.

## Mapping to this repository

* `backend/pom.xml` currently has Spring Data JPA and H2 but no migration tool or PostgreSQL driver.
* `backend/src/main/resources/application.yml` is the H2 demo path; `application-prod.yml` is the natural place for production overrides.
* Entities already define the initial relational model and all imported business entities carry `importBatchId`.
* The active-batch contract requires historical rows to remain queryable by batch, so business identifiers such as `dealerCode`, `opportunityId`, `leadId`, `taskId`, and `campaignId` must not be globally unique in the baseline schema.
* A baseline migration should create indexes for `import_batch_id` and the active-batch lookup fields. `import_batches` needs an index supporting newest active batch resolution.
* Tests currently start isolated H2 databases through explicit Spring properties, so the default test path should not be forced onto PostgreSQL.

## Recommendation

Use PostgreSQL + Flyway for the production profile, with H2 retained for demo and fast tests. Use a single SQL baseline migration derived from the current entity mappings, then add PostgreSQL-specific integration coverage for migration startup and persistence. Prefer forward-only corrective migrations over relying on automatic rollback.

## Risks and mitigations

* H2 may accept SQL that PostgreSQL rejects -> run a PostgreSQL profile smoke test when a PostgreSQL service is available.
* A baseline that accidentally preserves global unique constraints would prevent duplicate business IDs across import batches -> review every business key and add batch-scoped indexes instead.
* `ddl-auto: validate` can fail if the baseline misses a nullable/length/type detail -> run the application against the migrated schema before declaring the task complete.
* Automatic startup migration can block application startup on an unavailable database -> document required environment variables and keep H2 as the default local path.
