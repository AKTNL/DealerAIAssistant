# Logging Guidelines

> How logging is done in this project.

---

## Overview

The project uses **SLF4J** as the logging facade. Logging is configured in `application.yml` with `com.brand.agentpoc: INFO`. All loggers are obtained via `LoggerFactory.getLogger()` -- the project does **not** use Lombok's `@Slf4j` annotation.

---

## Logger Declaration

Two naming conventions coexist in the codebase:

### `log` (lowercase, most common)

Used in `ExcelImportService`, `DataQueryService`, `AnalyticsApiService`, `ModelConfigService`, `ApiKeyFilter`, `SessionTokenFilter`:

```java
// ExcelImportService.java:51
private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);
```

### `LOGGER` (uppercase)

Used in `RuleBasedAnalyticsService`:

```java
// RuleBasedAnalyticsService.java:47
private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedAnalyticsService.class);
```

### `log` instance field in AuthController

`AuthController` uses an instance field (not `static`):

```java
// AuthController.java:22
private static final Logger log = LoggerFactory.getLogger(AuthController.class);
```

**Standard for new code**: Use `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`.

---

## Log Levels

### DEBUG

Used for **data-processing details** that are useful during development but too noisy for production:

- Skipping rows during import due to missing required values:
  ```java
  // ExcelImportService.java:292
  log.debug("Skipping opportunity row {} due to missing required values.", rowIndex + 1);
  ```

- Defaulting blank fields to sentinel values:
  ```java
  // ExcelImportService.java:376
  log.debug("[Import-Normalization] Row {}: campaignName is blank, defaulting to campaignId", rowIndex + 1);
  ```

- Parse failures that are handled gracefully:
  ```java
  // ExcelImportService.java:767-768
  log.debug("Failed to parse {} from value '{}' as integer: {}", fieldName, text,
          exception.getClass().getSimpleName());
  ```

  ```java
  // AnalyticsApiService.java:464-465
  log.debug("Failed to parse {} from value '{}' as date: {}", fieldName, value,
          exception.getClass().getSimpleName());
  ```

### INFO

Used for **operational milestones** and system state transitions:

- Application startup and initialization:
  ```java
  // ExcelImportService.java:101
  log.info("Sample data already initialized, skipping startup import.");
  ```

- Import progress and results:
  ```java
  // ExcelImportService.java:120-122
  log.info("Seeded {} dealers and {} campaign rows from the workbook.",
          dealerRepository.count(), campaignRepository.count());
  ```

- Fallback data seeding:
  ```java
  // ExcelImportService.java:117
  log.info("Fallback sample data seeded.");
  ```

- Workbook import attempt:
  ```java
  // ExcelImportService.java:159
  log.info("Attempting workbook import from {}", resource);
  ```

### WARN

Used for **recoverable problems** and security events:

- Authentication failures:
  ```java
  // ApiKeyFilter.java:68-70
  log.warn("API key authentication failed: path={}, remoteAddress={}, reason={}",
          request.getServletPath(), request.getRemoteAddr(), failureReason(apiKey));
  ```

  ```java
  // SessionTokenFilter.java:63-65
  log.warn("Session token authentication failed: path={}, remoteAddress={}, reason={}",
          request.getServletPath(), request.getRemoteAddr(),
          failureReason(authorization, token));
  ```

- Missing resources with fallback:
  ```java
  // ExcelImportService.java:111
  log.warn("Excel resource not found at {}. Seeding built-in sample data instead.",
          appProperties.getExcel().getPath());
  ```

- Import produced no usable data:
  ```java
  // ExcelImportService.java:167
  log.warn("Workbook import produced no usable rows. Falling back to built-in sample data.");
  ```

### ERROR

Used for **unexpected failures** that trigger fallback behavior:

- Import crash with fallback:
  ```java
  // ExcelImportService.java:184
  log.error("Workbook import failed. Falling back to built-in sample data.", exception);
  ```

- Token generation failure after successful auth:
  ```java
  // AuthController.java:60-61
  log.error("Unable to issue session token after successful access-key verification: {}",
          exception.getMessage());
  ```

**Important**: When logging an error, always include the exception object as the last argument so the full stack trace is captured.

---

## Structured Logging

### Key=Value Format

Authentication and security logs use `key=value` pairs for machine-parseable logging:

```java
log.warn("API key authentication failed: path={}, remoteAddress={}, reason={}",
        request.getServletPath(), request.getRemoteAddr(), failureReason(apiKey));
```

This produces output like:
```
API key authentication failed: path=/api/v1/data/dealers, remoteAddress=203.0.113.10, reason=invalid_api_key
```

### Context Tags

Data-processing debug logs use `[Context]` prefix tags to identify the subsystem:

```java
log.debug("[Import-Normalization] Row {}: campaignName is blank, defaulting to campaignId", rowIndex + 1);
log.debug("[Import-Normalization] Row {}: eventType is blank, defaulting to '0'", rowIndex + 1);
```

### Exception Logging

When logging exceptions, use `{}` as a placeholder for the exception message, and pass the exception as the last argument:

```java
// Simple message-only (no stack trace needed):
log.error("Unable to issue session token after successful access-key verification: {}",
        exception.getMessage());

// Full exception with stack trace:
log.error("Workbook import failed. Falling back to built-in sample data.", exception);
```

---

## What to Log

| Event | Level | Example |
|---|---|---|
| Application startup/init | INFO | `"Sample data already initialized, skipping startup import."` |
| Data import progress | INFO | `"Seeded {} dealers and {} campaign rows from the workbook."` |
| Fallback/graceful degradation | INFO / WARN | `"Excel resource not found at {}. Seeding built-in sample data instead."` |
| Auth failures | WARN | `"API key authentication failed: path={}, remoteAddress={}, reason={}"` |
| Unexpected errors with fallback | ERROR | `"Workbook import failed. Falling back to built-in sample data."` |
| Row-level data skipping | DEBUG | `"Skipping opportunity row {} due to missing required values."` |
| Field defaulting | DEBUG | `"[Import-Normalization] Row {}: campaignName is blank, defaulting to campaignId"` |
| Parse failures (graceful) | DEBUG | `"Failed to parse date from value '{}' as date: {}"` |

---

## What NOT to Log

- **API keys, session tokens, or access secrets** -- never log these in any form (full or partial)
- **Personally identifiable information (PII)** from user messages or data
- **Full HTTP request/response bodies** unless strictly for development and behind a flag
- **Stack traces at INFO or above** -- stack traces should only appear at ERROR level and should not leak sensitive data

---

## Log Configuration

From `application.yml`:

```yaml
logging:
  level:
    com.brand.agentpoc: INFO
```

To enable debug logging for import troubleshooting without restarting, change this to `DEBUG` or override with the environment variable `LOGGING_LEVEL_COM_BRAND_AGENTPOC=DEBUG`.
