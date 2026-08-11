# Authentication and Authorization

> Executable contracts for database-backed identity, RBAC, opaque sessions, and business authorization.

## Scenario: Revocable Identity and RBAC Boundary

### 1. Scope / Trigger

- Trigger: any change to login, refresh, logout, password lifecycle, users/roles, permissions, protected HTTP/SSE routes, or Agent tools.
- The canonical implementation lives under `com.brand.agentpoc.auth`; new identity code must not return to the legacy root `controller`, `service`, or `config` packages.

### 2. Signatures

- Public session endpoints:
  - `POST /api/auth/login` with `{username,password}`.
  - `POST /api/auth/refresh` with the `agentpoc_refresh` HttpOnly Cookie.
  - `POST /api/auth/logout` with the refresh Cookie, or an optional valid Bearer session for clients without cookies.
- Authenticated endpoints: `GET /api/auth/me`, `POST /api/auth/password`, `POST /api/auth/logout-all`.
- Administration: `/api/admin/users/**` and `/api/admin/roles/**`.
- Database tables: `auth_users`, `auth_roles`, `auth_role_permissions`, `auth_user_roles`, `auth_sessions`, `auth_audit_events` from `V4__create_auth_identity_schema.sql`.
- Configuration: `APP_AUTH_ACCESS_TOKEN_TTL`, `APP_AUTH_REFRESH_TOKEN_TTL`, `APP_AUTH_COOKIE_SECURE`, `APP_AUTH_COOKIE_SAME_SITE`, `APP_AUTH_BOOTSTRAP_*`, and `APP_CORS_ALLOWED_ORIGINS`.

### 3. Contracts

- Passwords use Spring Security's delegating `PasswordEncoder`; raw passwords and raw access/refresh tokens are never stored, audited, or logged.
- Access and refresh values are 32-byte cryptographically random opaque tokens; `auth_sessions` stores only SHA-256 digests.
- Defaults are a 30-minute access TTL and a 7-day refresh TTL. Refresh rotates both tokens. Reusing a rotated refresh token revokes its entire family.
- Refresh and Cookie-based logout require an exact trusted `Origin`. `SameSite=None` is invalid unless Cookie `Secure` is enabled.
- Every Bearer request resolves the session, enabled user, roles, and permissions from the database. Tokens never carry an authorization snapshot.
- Permission keys are the `PermissionKey` enum. `ADMIN`, `ANALYST`, and `VIEWER` are the fixed built-in matrices; built-in role permissions cannot be edited.
- Temporary-password principals have no business authorities. They may only call `me`, `password`, and logout routes.
- `AgentRequestScope.authenticated(sessionId, stableUserId, permissions)` always receives explicit permissions. `ChatService` has no overload that omits the scope; every tool checks its own `AgentToolName.requiredPermission()`.
- Refresh rows and last-administrator mutations use database locks so concurrent requests cannot double-rotate a token or remove every effective administrator.

### 4. Validation & Error Matrix

- Missing/invalid/expired Bearer token -> uniform JSON HTTP 401.
- Valid identity without the endpoint/tool permission -> uniform JSON HTTP 403.
- Invalid login, unknown user, disabled user, or bad password -> the same HTTP 401 message; rate-limited login -> 429 with `Retry-After`.
- Refresh replay -> HTTP 401, refresh Cookie cleared, whole session family revoked, audit event written.
- Untrusted refresh/logout Origin -> HTTP 403 and refresh Cookie cleared.
- Password change -> revoke all sessions and require login with the new password.
- Disable, password reset, role assignment, or role-permission change -> revoke affected users' sessions.
- Unknown/empty roles or permissions, duplicate normalized username/role key, or built-in role edit -> HTTP 400.
- Removing or disabling the final effective administrator -> HTTP 409.

### 5. Good/Base/Bad Cases

- Good: a role change is visible on the next request because authorization is reloaded from database state.
- Good: two uses of one refresh token serialize; one rotates and the other is treated as replay, revoking the family.
- Base: logout is idempotent even when the Cookie is missing or already invalid.
- Bad: store a JWT permission claim, raw token, or long-lived browser credential and treat it as current authorization.
- Bad: grant `DATA_READ`, `KNOWLEDGE_QUERY`, or `REPORT_GENERATE` merely because the user has `CHAT_USE`.

### 6. Tests Required

- `AuthBootstrapTest` and startup tests: role matrices, empty-database bootstrap, idempotence, H2 Flyway V4, and Hibernate validation.
- `AuthHttpIntegrationTest`: temporary-password restriction, password invalidation, management API, last-admin protection, logout/logout-all, Origin rejection, refresh rotation/replay, and raw-secret absence.
- Security tests: public routes plus uniform 401/403 behavior.
- Agent/Chat tests: explicit scope, per-tool permission allow/deny, and denial before SSE starts.
- Final gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check` and `mvn.cmd "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
AgentRequestScope scope = AgentRequestScope.authenticated(sessionId, userId);
if (scope.hasPermission(PermissionKey.CHAT_USE)) {
    return analyticsService.queryAllData();
}
```

Correct:

```java
AgentRequestScope scope = AgentRequestScope.authenticated(sessionId, userId, principal.permissions());
if (!scope.hasPermission(AgentToolName.QUERY_METRIC.requiredPermission())) {
    throw new AccessDeniedException("Data analysis is not allowed.");
}
```
