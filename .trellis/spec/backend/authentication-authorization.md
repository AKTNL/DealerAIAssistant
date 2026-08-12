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

---

## Scenario: Real-Time Organization Data Scope

### 1. Scope / Trigger

- Trigger: any change to organization nodes, dealer mappings, user/role organization grants, business-data reads, Chat/SSE, controlled Agent tools, knowledge routing, or report generation/readback.
- This is a security and cross-layer contract because one database-resolved scope must survive HTTP -> service -> Agent/report boundaries without trusting client-supplied organization fields.

### 2. Signatures

- Permissions: `ORGANIZATION_READ`, `ORGANIZATION_MANAGE`, `ORGANIZATION_GRANT_MANAGE`.
- Administration API: `/api/admin/organizations/nodes`, `/dealer-mappings/**`, `/user-grants/{userId}`, `/role-grants/{roleId}`.
- Resolver: `OrganizationAuthorizationService.resolve(AuthPrincipal)` and `resolveCurrent()` -> `OrganizationAuthorizationContext`.
- Scope: `OrganizationDataScope(organizationNodeIds, grantNodeIds, dealerCodes, rootCoverage, unrestricted)`.
- Agent boundary: `AgentRequestScope.authenticated(sessionId, subject, permissions, organizationDataScope)`.
- Business row marker: `DealerScoped.getDealerCode()` on all six active-batch entities.

### 3. Contracts

- The hierarchy is `GROUP -> REGION -> CITY -> DEALER`; only `GROUP` may be a root. Administration rejects cycles and moves across roots.
- Effective scope is the union of current user grants and current role grants. `includeDescendants=false` includes only the grant node; `true` expands only through enabled descendants.
- Every authenticated business request reloads the enabled user, roles, grants, nodes, and dealer mappings from the database. Do not cache grants in access tokens, HTTP sessions, Chat sessions, or Agent callbacks.
- Missing grants, disabled/unknown grant nodes, and scopes with no mapped dealer deny data access. Client `dealerCode`, organization, and report scope fields may narrow results but never add dealer codes.
- Dashboard, raw data, metrics/details, rule analytics, sync/SSE Chat, controlled Agent tools, knowledge routing, and report generation receive the same resolved `OrganizationDataScope`.
- `/api/data-status` requires root coverage. A non-root Dashboard view suppresses global import counts/issues and reports only visible-row totals.
- Organization-scoped reports persist the grant-node anchors as `ReportScope(type=ORGANIZATION, id=<sorted node ids>)`; list/read/export re-check those anchors against the caller's current effective nodes.
- `unrestrictedScope()` exists only for legacy/internal compatibility overloads and tests. Production authenticated controller paths must resolve database scope.

### 4. Validation & Error Matrix

- Missing/wrong SecurityContext principal -> `AccessDeniedException`; never fall back to unrestricted scope.
- Unknown or disabled grant node -> `AccessDeniedException` on the next request.
- Grant without mapped dealer coverage -> `AccessDeniedException` before repository-backed data is returned.
- Forged dealer/org/report scope parameter -> empty/narrower result or normal request validation; never broader data.
- Disabled descendant -> exclude that branch and do not traverse through it.
- Organization cycle or cross-root reparent request -> HTTP 400; no hierarchy mutation.
- Report anchor no longer covered by current effective nodes -> omit from list and deny direct read/export.

### 5. Good/Base/Bad Cases

- Good: a user with a north-region descendant grant and a separate south-dealer grant sees the union and nothing else across HTTP, SSE, and Agent tools.
- Good: replacing a role grant changes the next request without issuing a new token.
- Base: `includeDescendants=false` on a CITY with no direct dealer mapping yields no data access.
- Bad: accept `dealerCode=D999` or `scopeId=GLOBAL_ROOT` as evidence that the caller may read that data.
- Bad: resolve scope once at login and retain it in `AgentRequestScope` for later requests.

### 6. Tests Required

- `OrganizationAuthorizationServiceTest`: descendant expansion, multi-grant union, `includeDescendants=false`, disabled/unknown nodes, SecurityContext extraction, and missing-principal denial.
- `OrganizationAdministrationServiceTest`: hierarchy type rules, cycle rejection, and cross-root rejection.
- Scope enforcement tests: active-batch rows first intersect with allowed dealer codes; forged dealer filters cannot expand access; empty scope denies before repository access.
- HTTP/Chat/Agent/report tests: sync and SSE scopes match, controlled callbacks propagate scope, report anchors are re-checked, and legacy unrestricted overloads remain compatibility-only.
- Startup/migration tests: V5 applies on H2 and Hibernate `validate` accepts every organization mapping.

### 7. Wrong vs Correct

Wrong:

```java
OrganizationDataScope scope = OrganizationDataScope.unrestrictedScope();
return dataQueryService.query(dataset, requestFilters, scope);
```

Correct:

```java
OrganizationDataScope scope = organizationAuthorizationService.resolveCurrent().dataScope();
return dataQueryService.query(dataset, requestFilters, scope);
```

---

## Scenario: Safe Administration Readback and Optimistic Mutations

### 1. Scope / Trigger

- Trigger: any change to administration user/role/organization response views, entity mutations, user-session administration, audit reads, or grant readback.
- This is a cross-layer security contract because the browser needs current state without receiving credential material, and concurrent administrators must not silently overwrite each other.

### 2. Signatures

- Safe reads:
  - `GET /api/admin/users/{id}/sessions -> ApiResult<List<SessionView>>`
  - `GET /api/admin/audit-events -> ApiResult<List<AuditEventView>>` (latest 100)
  - `GET /api/admin/organizations/user-grants/{userId}`
  - `GET /api/admin/organizations/role-grants/{roleId}`
- Session mutation: `POST /api/admin/users/{id}/sessions/revoke`.
- Optimistic mutation requests carry nullable `version`: enabled, roles, reset-password, role permissions, and organization node updates.
- User, role, and organization node views return their current JPA `@Version` value.

### 3. Contracts

- `SessionView` may contain only database ID, issue/expiry/rotation/revocation timestamps, fixed revocation reason, and derived `active`; it must never contain token hashes, token values, or `familyKey`.
- `AuditEventView` exposes the persisted fixed metadata fields only. Audit writers never store passwords, tokens, request bodies, or PII details.
- Session reads require `USER_READ`; session revoke requires `USER_MANAGE`; audit reads require `USER_READ`.
- Both user- and role-grant reads require `ORGANIZATION_GRANT_MANAGE`, even though general node/mapping GETs use `ORGANIZATION_READ`; matcher order must put grant paths before the generic organization GET matcher.
- If request `version` is present and differs from the loaded entity version, reject before mutation with HTTP 409.
- Mutations use `saveAndFlush()` so ORM optimistic-lock failures occur inside the controller's 409 mapping boundary.
- Omitting `version` remains compatibility behavior for existing API clients; the administration SPA always sends it.

### 4. Validation & Error Matrix

- Unknown user/role subject -> HTTP 400.
- Missing exact authority -> uniform HTTP 403.
- Expected version differs from current version -> HTTP 409 `The resource changed since it was loaded.`
- ORM optimistic-lock failure after validation -> same HTTP 409 public message.
- Final effective administrator removal/disable -> HTTP 409.
- Revoking a user with no active sessions -> HTTP 200 with a safe session list; operation stays idempotent.

### 5. Good/Base/Bad Cases

- Good: two administrators load version 3; the first update returns version 4, and the second version-3 update receives 409.
- Good: the session response shows active/revoked status while no raw or hashed credential field is serializable.
- Base: a legacy client omits version and preserves prior mutation behavior.
- Bad: expose `accessTokenHash`, `refreshTokenHash`, or `familyKey` so administrators can correlate credentials or session families.
- Bad: put generic organization GET matching before grant GET matching and accidentally authorize grant reads with `ORGANIZATION_READ`.

### 6. Tests Required

- `AuthAdministrationServiceTest`: stale user/role versions reject before persistence.
- `OrganizationAdministrationServiceTest`: stale node version plus existing hierarchy/cycle/root rules.
- `AuthAdministrationQueryServiceTest`: active derivation and record-component assertion excluding credential/hash/family fields.
- `AuthHttpIntegrationTest`: session list/revoke, audit read, exact RBAC, stale version 409, last-admin 409, and absence of password strings in audit details.
- Parse `static/openapi.json` as UTF-8 JSON after signature changes.
- Final gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check` and `mvn.cmd "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
public record SessionView(String familyKey, String accessTokenHash, String refreshTokenHash) {}
userRepository.save(user); // stale flush may escape the controller mapping
```

Correct:

```java
public record SessionView(Long id, Instant issuedAt, Instant refreshExpiresAt,
                          Instant revokedAt, String revocationReason, boolean active) {}
requireVersion(user.getVersion(), request.version());
userRepository.saveAndFlush(user);
```
