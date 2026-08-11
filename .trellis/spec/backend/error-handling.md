# Error Handling

> How errors are propagated and returned by the current backend.

## Standard API Envelope

Structured JSON endpoints use `ApiResult<T>`:

```java
public record ApiResult<T>(int code, T data, String message) {
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, data, "success");
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, null, message);
    }
}
```

Controllers may return an empty 403 for ownership mismatches in legacy business endpoints, but new JSON endpoints should return the standard envelope.

## Authentication and Authorization Errors

Spring Security owns missing/invalid identity and missing-authority responses. `JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler` delegate to `SecurityErrorWriter`, which writes stable JSON and stops the filter chain.

```java
authenticationEntryPoint.commence(request, response, authenticationException); // 401
accessDeniedHandler.handle(request, response, accessDeniedException);           // 403
```

Rules:

- Do not expose whether a login failed because the user is missing, disabled, or has a bad password.
- Login rate limits return 429 plus `Retry-After` and the same generic login message.
- Refresh replay and invalid refresh credentials return the same public 401; replay also revokes the family and records an audit event.
- Administration validation returns 400. The final-administrator invariant returns 409.
- See [Authentication and Authorization](./authentication-authorization.md) for the full matrix.

## Controllers

Controllers convert expected application validation failures locally:

```java
try {
    return ResponseEntity.ok(ApiResult.success(operation.run()));
} catch (IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
} catch (IllegalStateException exception) {
    return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
}
```

Do not catch unexpected runtime failures only to return success. Let infrastructure failures fail the request and preserve transaction rollback.

## Services

- Reject caller/input contract failures with `IllegalArgumentException`.
- Reject authorization with Spring `AccessDeniedException`, not an ordinary fallback.
- Use graceful degradation only where the product contract explicitly defines it, such as optional model generation falling back to deterministic analytics or demo workbook import falling back when enabled.
- Parsing helpers may log at DEBUG and return `null` when row-level data cleansing explicitly allows the field to be absent.

```java
try {
    return hasText(value) ? LocalDate.parse(value.trim()) : null;
} catch (DateTimeParseException exception) {
    log.debug("Failed to parse {} as date: {}", fieldName, exception.getClass().getSimpleName());
    return null;
}
```

## Streaming

Authorization must complete before an SSE response begins. Once streaming starts, application/model errors use the existing SSE `error` event contract; transport `IOException` is rethrown after cleanup.

```java
chatService.authorizeRequest(request, scope);
StreamingResponseBody body = output -> chatService.streamChat(request, output, scope);
```

Never begin a 200 SSE response and then discover that the principal lacks data, knowledge, or report permission.

## Status Matrix

| Status | Scenario |
|---|---|
| 400 | Bean validation, unknown role/permission, invalid report/model/data input |
| 401 | Missing/invalid/expired Bearer identity, invalid login/refresh credentials |
| 403 | Valid identity without authority, untrusted refresh/logout Origin, ownership mismatch |
| 409 | Attempt to remove the final effective administrator |
| 429 | Login rate limit, with `Retry-After` |
| SSE `error` | Failure after a streaming response safely began |

## Tests

- Security handler tests assert exact 401/403 JSON fields and content type.
- HTTP integration tests assert login, refresh, logout, management, and authorization status behavior.
- Controller tests cover Bean Validation plus mapped 400/404/409 paths.
- Chat tests assert authorization failure occurs before SSE output.
