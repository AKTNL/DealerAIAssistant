# Database Guidelines

> Spring Data JPA patterns and conventions for this project.

---

## Overview

The project uses **Spring Data JPA 3.4.5** with **H2** as the in-memory database (MySQL compatibility mode). Schema is managed automatically by Hibernate via `ddl-auto: update`. No migration tooling (Flyway/Liquibase) is used.

Dependencies: `spring-boot-starter-data-jpa`, `com.h2database:h2`.

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
```

Data is seeded on startup by `ExcelImportService` (implements `ApplicationRunner`), which reads an Excel file or falls back to built-in defaults.

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

This project does **not** use Flyway, Liquibase, or any migration tool. Schema is managed by Hibernate's `ddl-auto: update` setting, which auto-creates and updates tables based on entity annotations at startup.

For production use, a migration tool would need to be introduced.

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
