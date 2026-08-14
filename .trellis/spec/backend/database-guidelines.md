# Database Guidelines

> Spring Data JPA patterns and conventions for this project.

---

## Overview

The project uses **Spring Data JPA 3.4.5** with **H2** as the default in-memory database (MySQL compatibility mode) and **PostgreSQL** as the production database. The default demo/test path keeps Hibernate `ddl-auto: update`; the `prod` profile uses Flyway-managed migrations and Hibernate `ddl-auto: validate`.

Dependencies: `spring-boot-starter-data-jpa`, `com.h2database:h2`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, and `org.flywaydb:flyway-database-postgresql`.

Configuration from `backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:agentpoc;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update
  flyway:
    enabled: false
```

Data is seeded on startup by `ExcelImportService` (implements `ApplicationRunner`), which reads an Excel file or falls back to built-in defaults.

Production overrides in `backend/src/main/resources/application-prod.yml` read `APP_DB_URL`, `APP_DB_USERNAME`, and `APP_DB_PASSWORD`, enable Flyway from `classpath:db/migration,classpath:db/postgresql`, and set Hibernate DDL to `validate`.

---

## Entity Patterns

### Table Naming

Tables use **plural lowercase** names with underscores as word separators. Defined via `@Table(name = "...")`.

| Entity Class | Table Name |
|---|---|
| `Dealer` | `dealers` |
| `Opportunity` | `opportunities` |
| `Lead` | `leads` |
| `Task` | `dealer_tasks` |
| `Target` | `dealer_targets` |
| `Campaign` | `campaigns` |

### Primary Key

Every entity uses an auto-generated `Long` surrogate key:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

This is the JPA identity, not the business key. Business identifiers (e.g., `dealerCode`, `opportunityId`, `leadId`) live in separate columns with `nullable = false` and optionally `unique = true`.

### Column Constraints

Every field is annotated with `@Column` specifying `nullable` and `length` (for strings). Example from `entity/Dealer.java`:

```java
@Column(nullable = false, unique = true, length = 64)
private String dealerCode;

@Column(nullable = false, length = 128)
private String dealerName;
```

The standard length conventions observed in the codebase:
- Business IDs: `length = 64`
- Names: `length = 128`
- Campaign names: `length = 256` (longer display names)

### Nullable Fields

Most columns are `nullable = false`. Exceptions are fields whose data source (Excel workbook) may contain blanks:

```java
// entity/Lead.java:44 - createdDate is nullable because Excel rows may have blank dates
@Column
private LocalDate createdDate;
```

When a nullable field is blank in the import, the entity sets a sentinel default in its constructor or the import service skips the row depending on whether the field is critical for analytics.

### Default Values in Constructors

Entities use **two-tier constructor delegation** so that optional fields default to sentinel values like `"未知"` or `0`. Example from `entity/Opportunity.java`:

```java
// Shorter constructor delegates to full constructor with default for purchaseHorizon
public Opportunity(..., String productModel, String stageName, ...) {
    this(..., productModel, "未知", stageName, ...);  // purchaseHorizon defaults to "未知"
}

// Full constructor applies null/blank guard
public Opportunity(..., String purchaseHorizon, ...) {
    this.purchaseHorizon = purchaseHorizon == null || purchaseHorizon.isBlank() ? "未知" : purchaseHorizon;
}
```

### No-Arg Constructor

JPA requires a no-arg constructor. It is declared **`protected`** (not `public`) to prevent accidental use while satisfying Hibernate:

```java
protected Dealer() {
}
```

### Getters Only (No Setters)

Entities are **immutable after construction**. Only getters are exposed; there are no setters. Fields are set via the constructor only. Example from `entity/Dealer.java`:

```java
public Long getId() { return id; }
public String getDealerCode() { return dealerCode; }
// No setDealerCode(), no setId()
```

### No Business Logic in Entities

Entities contain only fields, constructors, and getters. No validation, no calculations, no service references. All business logic belongs in `service/`.

---

## Repository Patterns

### Interface Declaration

Every repository extends `JpaRepository<Entity, Long>` (the `Long` matches the `id` type):

```java
// repository/DealerRepository.java
public interface DealerRepository extends JpaRepository<Dealer, Long> {
    List<Dealer> findByDealerCodeIgnoreCase(String dealerCode);
    List<Dealer> findByCityIgnoreCase(String city);
    List<Dealer> findByDealerGroupNameIgnoreCase(String dealerGroupName);
}
```

Key conventions:
- Return type is always `List<Entity>` (not `Optional`, not `Page`).
- String matching uses `IgnoreCase` suffix for case-insensitive comparison.
- Date range queries use `findByCreatedDateBetween(LocalDate start, LocalDate end)`.
- No custom `@Query` annotations -- everything uses Spring Data **query derivation from method names**.

### Common Query Method Patterns

| Method Name | Purpose |
|---|---|
| `findByDealerCodeIgnoreCase(String)` | Exact match on a dealer identifier |
| `findByCityIgnoreCase(String)` | Filter by city |
| `findByDealerGroupNameIgnoreCase(String)` | Filter by dealer group |
| `findByProductModelIgnoreCase(String)` | Filter by product/model |
| `findByCampaignTypeIgnoreCase(String)` | Filter by business campaign type |
| `findByStageNameIgnoreCase(String)` | Filter by opportunity/lead stage |
| `findByStatusIgnoreCase(String)` | Filter by task status |
| `findByCreatedDateBetween(LocalDate, LocalDate)` | Date range filter |
| `findByCreatedDateGreaterThanEqual(LocalDate)` | Date lower bound |
| `findByCreatedDateLessThanEqual(LocalDate)` | Date upper bound |
| `findByDealerCodeIgnoreCaseAndCreatedDateBetween(String, LocalDate, LocalDate)` | Compound filter |

### No Custom Implementations

No repository has a custom implementation or fragment. All queries are satisfied by Spring Data method derivation alone.

---

## Transaction Patterns

### Read-Only Services

Services that only query data declare `@Transactional(readOnly = true)` at the class level:

```java
// service/DataQueryService.java:28
@Service
@Transactional(readOnly = true)
public class DataQueryService {
```

### Write Services

Services that modify data use `@Transactional` (default propagation) for write operations:

```java
// service/ExcelImportService.java imports data within @Transactional methods
```

### No Explicit Transaction Boundaries in Controllers

Controllers do not manage transactions. All transaction boundaries are in the service layer.

---

## Migrations

The production profile uses Flyway for schema management. Versioned SQL files live under `backend/src/main/resources/db/migration/` and are applied in order during startup. The initial `V1__create_initial_schema.sql` migration mirrors the current JPA entities, creates the `import_batches` table, and adds indexes for active-batch and batch-scoped business lookups.

The default H2 demo/test profile keeps Flyway disabled and continues to use Hibernate `ddl-auto: update`; tests that need migration coverage invoke Flyway explicitly against an isolated H2 database. Production schema changes must be delivered as a new forward migration. Do not edit an already-applied migration or re-enable Hibernate schema mutation in production.

The following production migration contract is executable and must remain aligned with `application-prod.yml`:

### Scenario: PostgreSQL + Flyway Production Migration

#### 1. Scope / Trigger

- Trigger: The application needs durable production data and auditable schema changes without removing the fast H2 demo/test path.
- This is an infrastructure and database contract because startup, persistence, migration history, and Hibernate validation must agree on the same schema.

#### 2. Signatures

- Profile: `spring.profiles.active=prod`
- Environment keys: `APP_DB_URL`, `APP_DB_USERNAME`, `APP_DB_PASSWORD`
- Migration locations: `classpath:db/migration,classpath:db/postgresql`
- Baseline migration: `V1__create_initial_schema.sql`
- Knowledge migration: `db/postgresql/V2__create_knowledge_vector_store.sql`
- Reporting migration: `db/postgresql/V3__create_report_drafts.sql`
- Identity migration: `V4__create_auth_identity_schema.sql`
- Organization migration: `V5__create_organization_scope_schema.sql`
- Production settings: `spring.flyway.enabled=true`, `spring.flyway.validate-on-migrate=true`, `spring.flyway.clean-disabled=true`, `spring.jpa.hibernate.ddl-auto=validate`

#### 3. Contracts

- The `prod` profile uses the configured PostgreSQL JDBC URL and never silently falls back to H2 or built-in sample data when the database or migration is unavailable.
- Flyway applies pending versioned SQL before Hibernate validates entity mappings.
- The baseline creates `import_batches`, all six batch-scoped business tables, the active-batch lookup index, and non-unique batch/business-key indexes.
- The PostgreSQL-only V2 migration enables `vector`, creates `knowledge_vector_store` with text IDs, JSON metadata, `VECTOR(1536)`, and an HNSW cosine index. It is not applied to the default H2 migration test location.
- The PostgreSQL-only V3 migration creates `report_drafts` with report type, Markdown body, generation timestamp, import batch, scope, model, and prompt-version metadata plus a newest-first timestamp index.
- V4 creates database identity/session/RBAC tables; V5 creates organization nodes, dealer mappings, user grants, and role grants and adds the fixed organization permissions to the built-in administrator.
- Business IDs may repeat across import batches; do not add global unique constraints to `dealer_code`, `opportunity_id`, `lead_id`, `task_id`, or `campaign_id`.
- The current physical naming contract includes `Target.asKTarget -> asktarget`; migration SQL must match the existing Hibernate mapping unless the entity explicitly declares another column name.

#### 4. Validation & Error Matrix

- Missing/invalid `APP_DB_*` credentials -> application startup fails with the datasource error; no H2 fallback.
- Pending valid migration -> Flyway applies it and records the version in `flyway_schema_history`.
- Applied migration file changed -> Flyway validation fails; create a new forward migration instead.
- Migration schema differs from JPA mappings -> Hibernate `validate` fails before the application becomes ready.
- H2 unit/demo startup without `prod` -> Flyway remains disabled and Hibernate `ddl-auto: update` remains available.
- Missing or incompatible V3 schema -> the production `JdbcReportDraftStore` operation fails; never replace it with the `!prod` in-memory adapter.
- Missing or incompatible V5 schema -> Hibernate validation or organization bootstrap fails; never silently substitute a global/unrestricted data scope.

#### 5. Good/Base/Bad Cases

- Good: A new PostgreSQL database starts with `V1`, imports one active batch, and retains the rows after restart.
- Good: V3 persists and round-trips the exact report batch, scope, model, prompt version, and timestamp used to generate a draft.
- Base: H2 tests run without a PostgreSQL server; the migration is explicitly exercised against isolated H2 and Hibernate validation passes.
- Bad: Setting `ddl-auto: update` in `prod` hides missing migration columns and makes schema drift unauditable.
- Bad: Creating report history only in memory under `prod` makes drafts disappear on restart and hides a failed V3 deployment.
- Bad: Naming the column `as_k_target` without changing the entity causes `ddl-auto: validate` to reject the schema because Hibernate expects `asktarget`.

#### 6. Tests Required

- Profile configuration test: assert PostgreSQL driver, `ddl-auto=validate`, Flyway enabled, and `clean-disabled=true`.
- Migration test: apply `V1` to isolated H2 and assert all required tables exist.
- Mapping test: start a context with Flyway enabled and `ddl-auto=validate` against the migrated schema.
- Reporting migration test: execute V3 in PostgreSQL-compatible H2, insert a complete draft record, and assert its batch/scope/model/prompt metadata round-trips.
- Organization migration test: apply through V5, assert all four organization tables/constraints exist, then start Hibernate with `ddl-auto=validate`.
- Batch coexistence test: insert the same business ID under two batch IDs and assert both rows are accepted.
- Deployment smoke test: with valid PostgreSQL credentials, assert schema version, first import counts, and unchanged counts after restart.

#### 7. Wrong vs Correct

Wrong:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

Correct:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

---

## Scenario: Organization Hierarchy and Grant Persistence

### 1. Scope / Trigger

- Trigger: organization hierarchy, dealer mapping, organization-grant, or permission-catalog persistence changes.
- This is a database/startup/security contract because Flyway, JPA mappings, bootstrap data, and real-time authorization queries must agree.

### 2. Signatures

- Migration: `backend/src/main/resources/db/migration/V5__create_organization_scope_schema.sql`.
- Tables: `organization_nodes`, `organization_dealer_mappings`, `organization_user_grants`, `organization_role_grants`.
- Fixed root key: `GLOBAL_ROOT`; node types: `GROUP`, `REGION`, `CITY`, `DEALER`.
- Unique keys: node `node_key`, mapping `dealer_code`, user `(user_id, organization_node_id)`, role `(role_id, organization_node_id)`.
- Foreign keys target `organization_nodes`, `auth_users`, and `auth_roles`.

### 3. Contracts

- V5 is a forward migration; never edit V4 or an applied V5. All timestamps use `TIMESTAMP WITH TIME ZONE`, IDs use identity `BIGINT`, and `OrganizationNodeEntity.version` is non-null for optimistic locking.
- V5 inserts only the stable `GLOBAL_ROOT` and fixed administrator permission rows. `OrganizationBootstrap` idempotently builds active-data `REGION -> CITY -> DEALER` nodes, dealer mappings, and the administrator root descendant grant after startup imports are ready.
- A dealer code has one organization mapping across the current schema. Business rows remain active-batch scoped; the mapping is an authorization lookup, not ownership of the imported row.
- Bootstrap uses stable keys and validates existing node type/parent drift. It preserves an administrator's explicit dealer remapping instead of overwriting a non-empty mapping on restart.
- Grant repositories read user and role rows on every authorization request; do not denormalize effective dealer codes into grant tables.

### 4. Validation & Error Matrix

- Duplicate node key/dealer mapping/grant -> database unique-constraint failure or service-level HTTP 400.
- Missing user, role, or node reference -> foreign-key failure; administration validates before write.
- JPA column/index/enum drift from V5 -> Hibernate `ddl-auto=validate` startup failure.
- Bootstrap key with a different type or parent -> startup failure; do not mutate the hierarchy silently.
- V5 absent in production -> organization repositories/bootstrap fail; no H2 or unrestricted fallback.

### 5. Good/Base/Bad Cases

- Good: a fresh database applies V1-V5, imports dealers, bootstraps one stable hierarchy, and a restart creates no duplicate nodes/mappings/grants.
- Base: an isolated H2 migration database accepts V5 and Hibernate validates the same entity mappings used by PostgreSQL.
- Bad: add `dealer_code` grant columns to every business table or copy effective dealer sets into access tokens.
- Bad: edit V5 after deployment to change a constraint; add V6 instead.

### 6. Tests Required

- `AgentPocApplicationStartupTest`: V5 SQL/table/constraint assertions plus Flyway-to-Hibernate validation.
- Bootstrap integration: fixed root, active dealer mappings, administrator descendant grant, and idempotent restart behavior.
- Authorization repository tests: user/role grant union and current database state on each request.
- Deployment smoke: confirm `flyway_schema_history` reaches V5 and no duplicate organization rows appear after restart.

### 7. Wrong vs Correct

Wrong:

```sql
ALTER TABLE organization_user_grants ADD COLUMN effective_dealer_codes TEXT;
```

Correct:

```sql
CONSTRAINT uq_organization_user_grants
    UNIQUE (user_id, organization_node_id)
```

Resolve descendants and dealer mappings at request time so grant changes take effect immediately.

---

## Scenario: Bundled Knowledge Retrieval And PGvector Production Store

### 1. Scope / Trigger

- Trigger: dealer knowledge questions need citable KPI definitions, SOPs, policies, and product/campaign rules without treating structured operating data as vector-store facts.
- This is a backend module, resource-ingestion, Spring AI, database migration, startup, and Agent contract because the same citation metadata and failure semantics must survive source -> chunk -> index -> tool -> chat.

### 2. Signatures

- Framework-neutral ports/use cases:
  - `KnowledgeDocumentSource.load(): List<KnowledgeDocument>`
  - `KnowledgeIndex.replaceAll(List<KnowledgeChunk>): void`
  - `KnowledgeIndex.search(KnowledgeQuery): KnowledgeSearchResult`
  - `KnowledgeService.retrieve(String query, Integer topK): KnowledgeSearchResult`
- Query limits: `DEFAULT_TOP_K=4`, `MAX_TOP_K=8`, `MAX_QUERY_LENGTH=500`.
- Catalog: `classpath:/knowledge/catalog.json` with `documentId`, `title`, `type`, `version`, `source`, and controlled `classpath:/knowledge/*.md` resource.
- Store selection/config:
  - `APP_KNOWLEDGE_VECTOR_STORE` -> `memory` by default, `pgvector` by default in `prod`.
  - `APP_KNOWLEDGE_SCHEMA_NAME` -> `public`.
  - `APP_KNOWLEDGE_TABLE_NAME` -> `knowledge_vector_store`.
  - `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS` -> `1536`.
  - `APP_KNOWLEDGE_SIMILARITY_THRESHOLD` -> `0.45`.
- Production database: `knowledge_vector_store(id TEXT PRIMARY KEY, content TEXT, metadata JSON, embedding VECTOR(1536))` plus HNSW cosine index.

### 3. Contracts

- `knowledge.domain` and `knowledge.application` contain no Spring, Spring AI, JDBC, resource-loader, or model-SDK types; Spring bean wiring and vector adapters live under `knowledge.infrastructure`.
- Only repository-reviewed bundled Markdown is ingested. `documentId`/version syntax, duplicate IDs, controlled resource path, exact citation source mapping, non-empty content, and known `KnowledgeType` are validated before indexing.
- Chunking is heading-first and deterministic. A chunk ID includes document ID, version, normalized section key, and section-local chunk index so edits in an earlier section do not churn later-section IDs.
- Every hit contains `documentId`, `source`, `version`, `section`, `chunkId`, `excerpt`, and score. Empty hits mean explicit `noMatch=true`; they are not an invitation to use model general knowledge.
- The default memory adapter uses deterministic local lexical retrieval and requires no embedding service or PostgreSQL. The `prod` PGvector adapter filters `catalog == 'bundled'`, replaces bundled chunks on startup, and never silently falls back to memory.
- PGvector uses Spring AI 1.0 because the existing model and controlled `@Tool` runtime already use it. A future LangChain4j implementation may replace/add only an infrastructure adapter; it must preserve these domain/application contracts.
- Structured KPI values, rankings, details, scopes, and active-batch facts remain owned by structured services. RAG can explain a definition or policy but cannot overwrite those facts.
- Embedding model output dimension, `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS`, and the Flyway `VECTOR(...)` dimension must match. Dimension changes require a new forward migration and full bundled reindex; never edit applied V2.

### 4. Validation & Error Matrix

- Missing/duplicate/unsafe `documentId`, invalid version, missing resource, source/resource mismatch, empty catalog, or empty Markdown -> startup fails before the application becomes ready.
- Blank query, query over 500 characters, or Top-K outside `1..8` -> `IllegalArgumentException` before index access.
- Index not initialized/available -> `IllegalStateException`; knowledge-only chat attempts deterministic fallback and otherwise emits the fixed unavailable response.
- No lexical/semantic hit -> return empty hits with `noMatch=true`; do not fabricate a policy/SOP/definition.
- `prod` without `EmbeddingModel`, PostgreSQL `vector`, V2 schema, valid identifiers/dimensions/threshold, or database connectivity -> startup fails; do not create/use an undeclared memory store.
- Embedding dimension differs from the migrated vector column -> PGvector validation/add fails; add a forward migration and reindex.

### 5. Good/Base/Bad Cases

- Good: local H2 startup loads four catalog documents into the deterministic memory index and answers a KPI-definition query with stable source/version/section/chunk citations.
- Good: `prod` applies V2, embeds the bundled catalog, deletes/replaces only `catalog='bundled'`, and retrieves Top-K results above the configured threshold.
- Base: a valid but unrelated business question returns `noMatch=true` and the user-facing composer explicitly says no citable document was found.
- Bad: store current opportunity rows or active-batch KPI values in RAG and let the model select them as facts.
- Bad: configure `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS=3072` while V2 remains `VECTOR(1536)`.
- Bad: use Spring AI types in `KnowledgeService`, making a future adapter replacement leak across Agent and application code.

### 6. Tests Required

- `ClasspathKnowledgeCatalogLoaderTest`: four bundled types plus duplicate/invalid IDs, invalid version, empty content, and citation source drift.
- `KnowledgeDocumentChunkerTest`: heading boundaries, deterministic length splitting, section-local stable IDs, and no-content rejection.
- `KnowledgeServiceTest` / `InMemoryKnowledgeIndexTest`: query and Top-K limits, unavailable index, stable ranking, citation fields, and explicit no-match.
- `PgVectorKnowledgeIndexTest`: bundled delete filter, metadata mapping, Top-K, threshold, catalog filter, score, and pre-bootstrap rejection.
- `KnowledgeVectorStoreConfigTest`: text-ID/cosine/HNSW build contract, unsafe identifiers/dimensions/threshold rejection, and missing `EmbeddingModel` startup failure.
- `AgentPocApplicationStartupTest`: default external-service-free startup, prod store/Flyway locations, and V2 extension/table/index contract.
- Full gate: `mvn "-Dfrontend.skip=true" pmd:check` then `mvn "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong -- couple the application layer to one vector framework and hide production failure:

```java
@Service
class KnowledgeService {
    private final PgVectorStore store;
}
// If PGvector fails, silently create an in-memory store.
```

Correct -- keep the use case behind a port and select adapters explicitly by profile:

```java
public class KnowledgeService {
    private final KnowledgeIndex knowledgeIndex;
}

@ConditionalOnProperty(name = "app.knowledge.vector-store", havingValue = "pgvector")
class PgVectorKnowledgeIndex implements KnowledgeIndex {
}
```

---

## Scenario: Import Batch Active Data Scope

### 1. Scope / Trigger
- Trigger: Startup workbook imports can be repeated, and future upload analysis needs user/session/dealer-scoped data without mixing rows from older imports.
- This is a backend database, import, query, analytics, and status contract because entity rows, import metadata, and every user-visible data surface must agree on the active data batch.

### 2. Signatures
- Marker contract: `BatchScoped.getImportBatchId(): String`
- Legacy batch constant: `BatchScoped.LEGACY_BATCH_ID = "legacy-default"`
- Batch entity/table: `ImportBatch -> import_batches`
  - `batchKey: String`
  - `source: String`
  - `scopeType: String`
  - `scopeId: String?`
  - `active: Boolean`
  - `fallbackActive: Boolean`
  - `createdAt: Instant`
  - `activatedAt: Instant?`
  - `message: String`
- Batch service:
  - `ImportBatchService.newBatchId(String prefix): String`
  - `ImportBatchService.activateGlobalBatch(String batchId, String source, boolean fallbackActive, String message): ImportBatch`
  - `ImportBatchService.activeBatchId(): String`
  - `ImportBatchService.activeStatusBatch(): ImportDataStatus.Batch`
  - `ImportBatchService.filterActive(List<T extends BatchScoped> rows): List<T>`
- Status response field: `ImportDataStatus.batch: Batch?`

### 3. Contracts
- Every imported business entity (`Dealer`, `Opportunity`, `Campaign`, `Lead`, `Task`, `Target`) implements `BatchScoped` and persists `importBatchId`.
- Existing constructors must delegate to `BatchScoped.LEGACY_BATCH_ID` so unit tests and old manually created rows remain visible when no imported batch exists.
- New startup imports create a fresh global batch id before parsing, persist every parsed entity with that id, then activate the batch after successful persistence.
- Query services, analytics services, and known-dealer checks must filter repository results through `ImportBatchService.filterActive(...)` before mapping, counting, or deciding scope.
- `ImportBatchService.activeBatchId()` falls back to `legacy-default` when no active batch exists.
- Active-batch resolution uses the newest active import batch ordered by `activatedAt desc, id desc`. During the H2/ddl-auto MVP, older active rows may remain in `import_batches`; consumers must use `activeBatchId()` rather than reading `active=true` directly.
- Business identifiers such as `dealerCode`, `opportunityId`, `campaignId`, `leadId`, and `taskId` are not globally unique. The PostgreSQL baseline uses non-unique batch-scoped indexes so the same source identifier can coexist in different import batches; add composite uniqueness only when the source contract proves it safe.

### 4. Validation & Error Matrix
- Configured workbook import succeeds -> persist rows with the generated startup batch id, activate that batch, publish `source="configured-workbook"` and `fallbackActive=false`.
- Configured workbook is missing or invalid with fallback enabled -> seed built-in rows with a generated fallback batch id, activate that batch, publish `source="built-in-sample"` and `fallbackActive=true`.
- Configured workbook is missing or invalid with fallback disabled -> publish `source="import-failed"` using the current active/legacy status batch, throw startup failure, and do not activate a new batch.
- Repository returns rows from multiple batches -> service response includes only rows whose `importBatchId` equals `ImportBatchService.activeBatchId()`.
- No `ImportBatch` row exists -> legacy rows whose `importBatchId` is `legacy-default` remain visible.
- Direct repository counts across all rows -> invalid for user-visible status or analytics because they mix batches.

### 5. Good/Base/Bad Cases
- Good: Import workbook A, then workbook B. Queries and analytics show workbook B rows because B is the newest active batch, while workbook A remains stored for future rollback/audit work.
- Base: Unit tests that construct entities without an explicit batch still pass because those entities default to `legacy-default`.
- Good: `GET /api/data-status` includes `batch.id`, `batch.scopeType`, `batch.scopeId`, and `batch.activatedAt` alongside sheet quality counts.
- Bad: A dealer-scoped analytics query calls `dealerRepository.findAll()` and counts rows directly; old batch rows leak into the answer.
- Bad: Restoring `unique=true` on business ids prevents two import batches with the same source business id from coexisting.

### 6. Tests Required
- Query regression: `DataQueryService` returns only rows from the active import batch.
- Analytics regression: `RuleBasedAnalyticsService` and `AnalyticsApiService` aggregate only active-batch rows when repositories contain multiple batches.
- Chat regression: known-dealer checks ignore dealers from inactive batches.
- Import regression: startup/fallback imports assign a non-blank batch id to every persisted entity and publish status batch metadata.
- Compatibility regression: constructors without an explicit batch id still produce `legacy-default` rows.

### 7. Wrong vs Correct

Wrong:
```java
List<Opportunity> rows = opportunityRepository.findAll();
long won = rows.stream()
        .filter(row -> "Won".equalsIgnoreCase(row.getStageName()))
        .count();
```

Correct:
```java
List<Opportunity> rows = importBatchService.filterActive(opportunityRepository.findAll());
long won = rows.stream()
        .filter(row -> "Won".equalsIgnoreCase(row.getStageName()))
        .count();
```

Wrong:
```java
@Column(nullable = false, unique = true, length = 64)
private String opportunityId;
```

Correct:
```java
@Column(nullable = false, length = 64)
private String opportunityId;

@Column(nullable = false, length = 64)
private String importBatchId;
```

---

## Scenario: Report Subscription Schedule Persistence

### 1. Scope / Trigger

- Trigger: P2-3A adds durable tenant-scoped recurring report definitions and recipient membership rows.
- This is a Flyway/JPA contract because schema types, element collections, unique definitions, and Hibernate validation must remain aligned.

### 2. Signatures

- Migration: `backend/src/main/resources/db/migration/V8__create_report_subscriptions.sql`.
- Tables: `report_subscriptions` and `report_subscription_recipients`.
- Key columns: `tenant_id`, `creator_user_id`, `scope_type`, `scope_id`, `schedule_kind`, `local_time`, `time_zone`, `next_run_at`, `version`, and `deleted_at`.
- Unique key: `(tenant_id, creator_user_id, active_configuration_key)` for non-deleted definitions; recipient key `(subscription_id, recipient_user_id)`.

### 3. Contracts

- IDs are identity `BIGINT`; timestamps and `next_run_at` use `TIMESTAMP WITH TIME ZONE`; local schedule time uses SQL `TIME` and IANA zone text.
- Creator and recipient foreign keys point to `auth_users`; creator ownership is additionally checked against the current tenant membership in the service.
- `ReportSubscriptionEntity` uses a protected no-arg constructor, an eager `@ElementCollection` for recipient IDs, and JPA `@Version` for optimistic updates.
- Soft delete sets `enabled=false`, clears `next_run_at` and `active_configuration_key`, and retains the row for audit/history.
- The H2 migration test location applies V8 after V6 and Hibernate `ddl-auto=validate` must accept the same mapping used by production PostgreSQL.

### 4. Validation & Error Matrix

- Missing V8 or mismatched column/index/collection mapping -> Flyway/Hibernate startup or validation failure; never fall back to an in-memory subscription store.
- Duplicate active configuration or duplicate recipient pair -> service HTTP 409 or database constraint failure mapped to 409.
- Missing referenced tenant/user -> foreign-key failure; service validates active membership before persistence.
- Editing an applied migration -> reject; add V9 for future schema changes.

### 5. Good/Base/Bad Cases

- Good: a fresh H2 migration reaches V8, inserts one subscription and recipient, and round-trips the time zone and schedule kind.
- Base: disabled rows remain queryable by owner but have no due-run timestamp.
- Bad: add global uniqueness to subscription definitions, persist external channel credentials, or edit V8 after deployment.

### 6. Tests Required

- `AgentPocApplicationStartupTest`: apply V8, insert a complete subscription and recipient, assert round-trip fields, and run Flyway-to-Hibernate validation.
- `ReportSubscriptionServiceTest`: verify repository writes use `saveAndFlush()` and optimistic version/duplicate failures are mapped.
- Full backend migration and test gates must pass after every schema change.

### 7. Wrong vs Correct

Wrong:

```sql
ALTER TABLE report_subscriptions ADD COLUMN recipient_emails TEXT;
```

Correct:

```sql
CREATE TABLE report_subscription_recipients (
    subscription_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    CONSTRAINT uq_report_subscription_recipients
        UNIQUE (subscription_id, recipient_user_id)
);
```

---

## Common Mistakes

### Scenario: Lead Import With Blank CreatedDate

#### 1. Scope / Trigger
- Trigger: Lead rows in the sample Excel can have blank `CreatedDate`, but the rows are still valid for source, status, dealer, model, and conversion analytics.
- This is a backend import and database contract because it controls what data enters H2/JPA and what downstream analytics can count.

#### 2. Signatures
- Entity: `Lead.createdDate: LocalDate?`
- Import parser: `ExcelImportService.parseLeadSheet(...)`
- Query surfaces: `DataQueryService.query("leads", filters)` and `AnalyticsApiService` lead list/details.

#### 3. Contracts
- `Lead.createdDate` may be `null`.
- `Lead.leadId`, `Lead.stageName`, and `Lead.converted` are still required for import.
- Blank `CreatedDate` must not cause a Lead row to be skipped.
- API/detail serialization should return `createdDate: null` for blank dates.

#### 4. Validation & Error Matrix
- Missing `leadId` -> skip row.
- Missing `stageName` -> skip row.
- Missing/invalid `IsConverted` -> skip row.
- Missing `CreatedDate` -> import row with `createdDate = null`.
- Date range filter + `createdDate = null` -> exclude that row from the date-filtered result.
- No date filter + `createdDate = null` -> include that row.

#### 5. Good/Base/Bad Cases
- Good: A Lead with `Id`, `Status`, `IsConverted`, and blank `CreatedDate` is saved and counted in source/status/conversion aggregates.
- Base: A Lead with a valid `CreatedDate` behaves normally in unfiltered and date-filtered queries.
- Bad: Treating blank `CreatedDate` as a required-value failure silently drops otherwise valid Leads and makes H2 counts lower than the Excel question-bank baseline.

#### 6. Tests Required
- Import regression: blank Lead `CreatedDate` is persisted with `createdDate == null`.
- Query regression: unfiltered lead queries include null-date Leads.
- Query regression: date-filtered lead queries exclude null-date Leads without throwing.
- API regression: Lead detail mapping formats null dates as `null`, not `NullPointerException`.

#### 7. Wrong vs Correct

Wrong:
```java
if (hasBlank(leadId, stageName) || createdDate == null || converted == null) {
    continue;
}
```

Correct:
```java
if (hasBlank(leadId, stageName) || converted == null) {
    continue;
}
```

### Scenario: Workbook Analytics Fields And Campaign Dealer Matching

#### 1. Scope / Trigger
- Trigger: Accuracy-test questions depend on deterministic aggregations over imported workbook fields, not on generated prose or inferred data.
- This is a backend import, database, and analytics contract because missing imported fields or over-strict dealer matching makes valid workbook questions return wrong counts.

#### 2. Signatures
- Entities:
  - `Opportunity.purchaseHorizon: String?`
  - `Task.subject: String?`
  - `Campaign.campaignName/eventType/campaignType/targetOpportunityAmount/actualOpportunityCount/targetOrderAmount/wonOpportunityCount/leadCount/totalNewCustomerTarget`
- Import parser: `ExcelImportService.parseOpportunitySheet(...)`, `parseTaskSheet(...)`, `parseCampaignSheet(...)`
- Query surfaces: `DataQueryService.query("opportunities" | "tasks" | "campaigns", filters)`
- Analytics routing/matching: `RuleBasedAnalyticsService.detectTopic(...)`, `matchesScope(...)`, campaign direct-answer branches.

#### 3. Contracts
- Raw workbook fields used by accuracy-test aggregations must be persisted when present:
  - Opportunity `Purchase_Horizon__c` -> `Opportunity.purchaseHorizon`
  - Task `Subject` -> `Task.subject`
  - Campaign `Name`, raw `Type`, business `CampaignType`, opportunity/order/won/lead counters -> `Campaign`
- Campaign raw `Type` and business `CampaignType` are separate concepts:
  - raw `Type` -> `Campaign.eventType`
  - business campaign type -> `Campaign.campaignType`
- Campaign workbook `dealerCode` may be an external CRM/Salesforce-style id while `Dealer.dealerCode` is the local dealer code.
- When a specific dealer scope has been resolved from master data, analytics may match campaign rows by exact `dealerName` even if campaign `dealerCode` or blank campaign `dealerGroupName` does not match the master dealer row.
- Multi-entity count questions that ask "how many" across two or more entity types route to `DATA_OVERVIEW` before single-domain campaign/task/lead routing.

#### 4. Validation & Error Matrix
- Missing optional workbook analytics field -> persist `null` or project default, but do not skip an otherwise valid row.
- Missing required row identity -> skip according to the existing sheet-specific import rules.
- Campaign `dealerCode` differs from master dealer code but `dealerName` matches exactly -> include row in dealer-scoped campaign analytics.
- Campaign `dealerName` and `dealerCode` both fail the resolved dealer identity -> exclude row from dealer-scoped analytics.
- Question asks a non-business topic with no business keywords -> out-of-scope reply.
- Question asks `购买周期` / `购车周期` -> analytics request, not out-of-scope.

#### 5. Good/Base/Bad Cases
- Good: `经销商C(深圳南山)活动效果怎么样？` matches campaign rows with `dealerCode=001XYA...`, `dealerName=经销商C(深圳南山)`, and blank group; it returns campaign count and opportunity/order totals.
- Base: Campaign rows whose dealer code, dealer name, city, and group all match continue to behave normally.
- Bad: Requiring campaign `dealerCode == Dealer.dealerCode` and campaign group equality drops valid campaign rows and returns `0 matching rows`.

#### 6. Tests Required
- Import regression: new workbook fields are persisted on Opportunity, Task, and Campaign entities.
- Query regression: `DataQueryService` maps the new fields into response item maps.
- Routing regression: a multi-entity "how many opportunities/leads/tasks/campaigns" question maps to `DATA_OVERVIEW`.
- Business-boundary regression: a purchase-cycle question calls analytics instead of the out-of-scope path.
- Campaign scope regression: dealer-scoped campaign analytics matches by exact dealer name when campaign code/group formats differ.

#### 7. Wrong vs Correct

Wrong:
```java
return matchesField(campaign.getDealerCode(), scope.dealerCode())
        && matchesField(campaign.getDealerGroupName(), scope.dealerGroupName());
```

Correct:
```java
return matchesDealerIdentity(campaign.getDealerCode(), campaign.getDealerName(), scope)
        && matchesCity(campaign.getCity(), scope.city());
```

### Scenario: Field-Aware Excel Import And Comparable-Rate Cohorts

#### 1. Scope / Trigger
- Trigger: Excel source fields can be blank without invalidating the rest of the row, and target/campaign rates can become misleading when all observed actuals are divided by only the available targets.
- This is a database, import, API, and analytics contract because nullability must survive source -> entity -> query/DTO -> calculation -> user-visible response.

#### 2. Signatures
- Configuration:
  - `app.excel.path` / `APP_EXCEL_PATH`
  - `app.excel.fallback-enabled` / `APP_EXCEL_FALLBACK_ENABLED`
- Nullable entity fields:
  - `Opportunity.expectedCloseDate: LocalDate?`
  - `Target.asKTarget: Integer?`
  - Campaign target/actual counters: `Integer?`
- Authenticated status API: `GET /api/data-status -> ApiResult<ImportDataStatus>`
- Metric response fields:
  - `TargetMetrics.totalOpportunityWon` and `comparableOpportunityWon`
  - `CampaignMetrics.totalActualOpportunities`, `totalTarget`, `comparableActualOpportunities`, and `comparableTarget`

#### 3. Contracts
- Validate the five required sheets before persistence: `AE Target Data`, `Opportunity`, `Lead`, `Task`, and `Campaign`.
- Missing required business identifiers or required observed facts -> skip the row and increment a deterministic reason.
- Missing optional dates or metric-specific target denominators -> retain the row with `null`; never invent a date or replace the denominator with zero.
- Optional categories normalize to the project's explicit unknown/unassigned bucket and increment `normalizedFields`.
- Observed totals include every valid observed row.
- A rate numerator includes only rows whose paired denominator is available; expose the comparable numerator/denominator when they differ from observed totals.
- Demo fallback publishes source `built-in-sample` with `fallbackActive=true`. Strict/production failure publishes `import-failed` and throws instead of seeding sample data.

#### 4. Validation & Error Matrix
- Missing configured workbook + fallback enabled -> seed built-in sample, publish fallback status, show frontend warning.
- Missing configured workbook + fallback disabled -> throw startup failure; do not persist sample data.
- Missing required sheet -> same mode-aware failure behavior as an unavailable workbook.
- Blank `Opportunity.expectedCloseDate` -> import the opportunity; exclude it only from expected-close-date analysis.
- Blank `Target.asKTarget` -> import observed create/win counts; exclude that row from target-achievement rate cohorts.
- Negative numeric value or invalid probability/date ordering -> skip the row with a reason count.
- Textual null marker -> normalize to Java `null`, not numeric zero or literal category text.

#### 5. Good/Base/Bad Cases
- Good: A target row with `asKTarget=null` and `opportunityWonCount=10` contributes 10 to observed wins but contributes neither numerator nor denominator to achievement rate.
- Base: A complete row contributes its observed value and its paired numerator/denominator to the rate cohort.
- Good: An opportunity with no expected-close date still links its tasks to the dealer and participates in created-date funnel analysis.
- Bad: `sum(all wins) / sum(non-null targets)` overstates achievement and presents a mathematically unauditable response.
- Bad: Restoring `createdDate.plusDays(30)` or defaulting a missing target to `0` fabricates source facts.

#### 6. Tests Required
- `ExcelImportServiceTest`: retain nullable optional fields, reject required fields, assert import-quality reason counts, and cover strict/demo fallback.
- `DataQueryServiceTest`: serialize unavailable dates/targets as `null` and preserve observed counts.
- `AnalyticsApiServiceTest`: assert observed totals and comparable numerators separately.
- `RuleBasedAnalyticsServiceTest`: assert the response contains the observed total, comparable fraction, and corrected rate.
- `AccuracyWorkbookRegressionTest`: assert counts `112 / 5088 / 6198 / 1898 / 57582 / 715` and corrected workbook answer points.

#### 7. Wrong vs Correct

Wrong:
```java
int target = rows.stream().mapToInt(row -> defaultZero(row.getAsKTarget())).sum();
int won = rows.stream().mapToInt(Target::getOpportunityWonCount).sum();
double rate = percentage(won, target);
```

Correct:
```java
List<Target> comparable = rows.stream()
        .filter(row -> row.getAsKTarget() != null)
        .toList();
int observedWon = rows.stream().mapToInt(Target::getOpportunityWonCount).sum();
int comparableWon = comparable.stream().mapToInt(Target::getOpportunityWonCount).sum();
int comparableTarget = comparable.stream().mapToInt(Target::getAsKTarget).sum();
double rate = percentage(comparableWon, comparableTarget);
```
