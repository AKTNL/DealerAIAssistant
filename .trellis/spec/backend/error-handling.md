# Error Handling

> How errors are handled, propagated, and returned to clients in this project.

---

## Overview

The backend does **not** use `@ExceptionHandler` or `@ControllerAdvice` for centralized error handling. Errors are handled locally at each layer: controllers catch service exceptions and convert them to HTTP responses, services catch and log exceptions with graceful degradation, and servlet filters write error responses directly to the `HttpServletResponse`.

---

## Error Response Format

### JSON Error Body (Filters)

When authentication fails in `ApiKeyFilter` or `SessionTokenFilter`, the filter writes a JSON error body directly:

```java
// ApiKeyFilter.java:96-101
private void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("WWW-Authenticate", "Bearer");
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":401,\"message\":\"Invalid API key\"}");
}
```

### ApiResult<T> Record

For structured API responses, `dto/response/ApiResult.java` provides a standard envelope:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(int code, T data, String message) {
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, data, "success");
    }
    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, null, message);
    }
}
```

Success: `{"code":200, "data":{...}, "message":"success"}`
Error: `{"code":401, "data":null, "message":"Invalid API key"}`

### Simple Success/Failure Responses

For operations that only need a boolean indicator:

```java
// dto/response/SimpleSuccessResponse.java
public record SimpleSuccessResponse(boolean success) {}
```

```java
// dto/response/AuthVerifyResponse.java
public static AuthVerifyResponse failure() { ... }
public static AuthVerifyResponse success(String token, Instant expiresAt) { ... }
```

---

## Error Handling by Layer

### Controllers

Controllers use `ResponseEntity` to set HTTP status codes and response bodies. No exceptions are thrown outside the controller -- errors are caught and converted to appropriate status codes.

**Authentication failure** -- return 403 Forbidden:
```java
// ChatController.java:46-48
if (!sessionOwnershipService.claimOrVerify(request.sessionId(), tokenSubject(servletRequest))) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

**Rate limit exceeded** -- return 429 Too Many Requests with `Retry-After` header:
```java
// AuthController.java:44-48
if (authRateLimitService.isLimited(clientKey)) {
    return ResponseEntity.status(429)
            .header("Retry-After", String.valueOf(authRateLimitService.retryAfterSeconds(clientKey)))
            .body(AuthVerifyResponse.failure());
}
```

**Token generation failure** -- catch and return failure response:
```java
// AuthController.java:55-63
try {
    SessionTokenService.IssuedToken issuedToken = sessionTokenService.issueToken();
    authRateLimitService.recordSuccess(clientKey);
    return ResponseEntity.ok(AuthVerifyResponse.success(issuedToken.token(), issuedToken.expiresAt()));
} catch (IllegalStateException exception) {
    log.error("Unable to issue session token after successful access-key verification: {}",
            exception.getMessage());
    return ResponseEntity.ok(AuthVerifyResponse.failure());
}
```

### Services

Services use try-catch for **graceful degradation** -- caught exceptions are logged and the operation proceeds with a fallback rather than propagating the error.

**Data parsing failure** -- log at debug, return null:
```java
// AnalyticsApiService.java:460-468
private LocalDate parseDate(String fieldName, String value) {
    try {
        return hasText(value) ? LocalDate.parse(value.trim()) : null;
    } catch (Exception exception) {
        log.debug("Failed to parse {} from value '{}' as date: {}", fieldName, value,
                exception.getClass().getSimpleName());
        return null;
    }
}
```

**Numeric parsing failure** -- log at debug, return null:
```java
// ExcelImportService.java:764-770
try {
    return (int) Math.round(Double.parseDouble(sanitized));
} catch (NumberFormatException exception) {
    log.debug("Failed to parse {} from value '{}' as integer: {}", fieldName, text,
            exception.getClass().getSimpleName());
    return null;
}
```

**Import failure** -- log at error, fall back to built-in sample data:
```java
// ExcelImportService.java:182-187
} catch (Exception exception) {
    log.error("Workbook import failed. Falling back to built-in sample data.", exception);
    // seed built-in data instead
    log.info("Fallback sample data seeded.");
}
```

**Unsupported operation** -- throw `IllegalArgumentException`:
```java
// DataQueryService.java:66
default -> throw new IllegalArgumentException("Unsupported dataset: " + dataset);
```

### Streaming (ChatService)

When streaming responses, errors are caught and written as SSE error events:

```java
// ChatService.java:194-196
} catch (Exception exception) {
    writeEvent(writer, "error", describeStreamFailure(exception));
}
```

Checked `IOException` in lambda consumers is wrapped as `UncheckedIOException` and unwrapped at the outer catch:
```java
// ChatService.java:151-155
try {
    writeStepEvent(writer, step);
} catch (IOException e) {
    throw new UncheckedIOException(e);
}

// ChatService.java:231-233
} catch (UncheckedIOException exception) {
    throw exception.getCause();  // unwrap back to IOException
}
```

### Filters

Filters handle auth failures by writing HTTP 401 directly and calling `return` to stop the filter chain:

```java
// ApiKeyFilter.java:56-73
@Override
protected void doFilterInternal(...) throws ServletException, IOException {
    String apiKey = request.getHeader("X-API-Key");
    if (matchesConfiguredApiKey(apiKey)) {
        filterChain.doFilter(request, response);
        return;
    }
    log.warn("API key authentication failed: path={}, remoteAddress={}, reason={}",
            request.getServletPath(), request.getRemoteAddr(), failureReason(apiKey));
    writeUnauthorized(response);
}
```

Both filters (`ApiKeyFilter`, `SessionTokenFilter`) provide detailed `failureReason()` methods that return diagnostic strings like `"missing_api_key"`, `"invalid_api_key"`, `"missing_bearer_token"`, `"invalid_or_expired_token"` for logging clarity.

---

## Error Types

### Custom Exception Use

The codebase uses standard Java/Spring exceptions rather than custom exception classes:

| Exception | Usage |
|---|---|
| `IllegalArgumentException` | Unsupported dataset parameter, invalid input |
| `IllegalStateException` | Session token generation failure |
| `UncheckedIOException` | Checked IOException in lambda/stream contexts |
| `NumberFormatException` | Excel cell parse failure (caught locally) |
| `DateTimeParseException` | Date parse failure (caught locally) |
| `IOException` | Stream write failures, filter output errors |

---

## API Error Responses

| Status | Scenario | Body Format |
|---|---|---|
| 200 | Auth verify failure (wrong key) | `AuthVerifyResponse.failure()` |
| 401 | Missing/invalid API key | `{"code":401,"message":"Invalid API key"}` |
| 401 | Missing/invalid session token | `{"code":401,"message":"Login session expired."}` |
| 403 | Session ownership mismatch | Empty body (`ResponseEntity.status(FORBIDDEN).build()`) |
| 429 | Auth rate limit | `AuthVerifyResponse.failure()` with `Retry-After` header |
| SSE `error` | Stream failure | SSE event type `error` with failure description |
