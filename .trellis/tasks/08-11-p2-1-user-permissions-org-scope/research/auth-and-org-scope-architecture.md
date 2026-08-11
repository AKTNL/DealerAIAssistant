# P2-1 authentication and organization-scope architecture research

## Question

How should the current shared access-key/custom bearer-token gate evolve into user, role, organization-scope, revocable-session, and audit capabilities without blocking the later tenant boundary?

## Sources

* Spring Security, `PasswordEncoder`: <https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html>
  * Password verification should use an adaptive one-way password function rather than reversible storage or direct string comparison.
* Spring Security, method security: <https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html>
  * Request rules and service/method authorization can be combined; service authorization remains important when multiple controllers, tools, or background flows call the same use case.
* OWASP Session Management Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html>
  * Session identifiers should be meaningless, unpredictable, protected in transport/storage, expired, rotated where necessary, and invalidated on logout or account/security changes.

Sources were checked on 2026-08-11.

## Comparable patterns

### 1. Framework-managed authentication plus database authorization

Typical Spring applications use Spring Security to create the authenticated principal and centralize endpoint/method authorization. Password hashes use `PasswordEncoder`. Roles/permissions and organization assignments are loaded from durable storage, while service-layer policy objects apply data scope to queries.

Why it exists:

* Authentication failures and authorization failures receive consistent handling.
* Security rules are testable without maintaining an application-specific filter stack for every endpoint.
* The same service policy can protect HTTP controllers, Agent tool callbacks, report generation, and future scheduled jobs.

### 2. Opaque, server-side revocable sessions

The client receives a high-entropy bearer value; the database stores only its digest and session metadata. Every request resolves the user and current account/permission state. Logout, disable, password reset, or administrator revocation invalidates the session immediately.

Why it exists:

* Immediate revocation is straightforward.
* Roles and organization assignments are not copied into long-lived client claims and therefore cannot remain stale.
* Session history supports audit and incident response.

Trade-off: every authenticated request needs a fast session/user lookup. This can be optimized later with carefully invalidated caching, but correctness should come first.

### 3. Short-lived signed access token plus refresh session

The access token is signed and short-lived; a server-side refresh record controls renewal and revocation. To meet immediate permission-change requirements, the application still needs a per-request user/security-version lookup or a denylist/version check.

Why it exists:

* Useful for horizontally scaled APIs and external clients.
* Reduces full session-table lookup if the accepted revocation window is non-zero.

Trade-off: access/refresh rotation, reuse detection, security-version rules, and browser storage policy create more implementation and testing complexity than the current product needs.

## Repository constraints discovered

* `AuthService` compares one configured `app.auth.access-key`; there is no user identity or password hash.
* `SessionTokenService` signs `issuedAt.expiresAt.randomUUID`. The UUID is treated as the token subject, so it identifies a login session rather than a durable user.
* `SessionTokenFilter` protects a hand-maintained subset of endpoints and writes a request attribute. Other `/api/**` routes still use the separate `ApiKeyFilter` path.
* No `spring-boot-starter-security` dependency is present.
* `SessionOwnershipService` stores chat-session ownership only in memory, keyed by the random token subject.
* Business rows carry `importBatchId` and dealer/group/city labels, but no `organizationId` or `tenantId`. Services commonly call `findAll()` and apply active-batch filtering in memory.
* P1 report records already persist `scope_type` and `scope_id`; this vocabulary should converge on the P2 authorization context.
* The Vue app has no router. `App.vue` switches between a shared-key login and the single workspace view. Frontend authorization cannot be the security boundary.

## Organization-scope model options

### A. Fixed role enum plus flat dealer-code assignments

Store a fixed role on each user and assign a set of dealer codes.

Pros: smallest schema and quickest first demo.

Cons: cannot represent group/region inheritance cleanly; role changes require code releases; likely to be replaced during P2.

### B. Configurable RBAC plus organization tree and explicit data-scope grants (recommended)

Use durable users, roles, permissions, user-role assignments, organizations with `parent_id` and `type`, and user/role organization grants with `include_descendants`. Resolve a request to a principal plus permission set and allowed organization/dealer set. Keep `tenant_id` nullable/reserved in identity and organization-owned tables until P2-2 enforces it everywhere.

Pros: matches the roadmap; supports group/region/dealer inheritance; separates functional permission from data scope; provides a stable base for Agent, knowledge, report, and future tenant isolation.

Cons: needs careful recursive-tree validation and query-policy tests; a full management UI should be a later increment.

### C. Attribute/policy engine now

Represent authorization as generic subject/resource/action/environment policies, possibly using an external engine.

Pros: maximum future flexibility.

Cons: operational and conceptual overhead is disproportionate for the current single-repo pilot; difficult to make business-admin friendly; adds another runtime boundary before the product has stable authorization needs.

## Recommended convergence

Use Spring Security with database-backed users and configurable RBAC, but ship it in vertical increments:

1. **P2-1A identity and session foundation**: users, password hashes, account status, roles/permissions, opaque hashed bearer sessions, login/logout/current-user APIs, unified authentication failure handling, seed administrator, and endpoint permission coverage.
2. **P2-1B organization data scope**: organization tree, scope grants, dealer mapping, one authorization-context API, and mandatory service/repository filtering across Dashboard, analytics, Agent tools, knowledge, and reports.
3. **P2-1C administration and audit**: management APIs and minimal UI for users/roles/organizations/grants; audit sensitive changes; token refresh/session management if the pilot requires longer interactive sessions.

This sequencing first replaces the shared-key identity weakness, then adds the data boundary, and only then adds admin UX. It reserves `tenant_id` in P2-owned tables but does not claim cross-tenant safety until P2-2 adds tenant ownership and composite indexes/queries to every protected business record.

## Security-critical acceptance implications

* Authorization must be deny-by-default for protected `/api/**` routes; public endpoints should be enumerated explicitly.
* Role/permission/organization changes must affect the next request; do not embed the authoritative permission set in a long-lived token.
* Store only a digest of opaque bearer/refresh secrets; return the raw value only once.
* Password and session reset, account disable, and logout must revoke relevant sessions.
* Apply organization scope inside shared application/query services so Agent callbacks and report generation cannot bypass controller annotations.
* Audit records must include actor, action, target, outcome, timestamp, and trace/request identifier, but never passwords or raw tokens.
