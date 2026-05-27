# Conservative Security Hardening Design

## Context

This design hardens the current Agent POC without changing its product shape. The requirements documents define the system as a POC with simple access-key login, `sessionStorage` login state, SSE chat, browser-local model settings, H2 sample data, and no full user system or production-grade security governance.

The current implementation matches that POC model, but `/api/chat/**` and `/api/model-config/**` are publicly reachable from the backend whitelist. The goal is to close that backend gap while staying aligned with the documented POC constraints.

Relevant requirement alignment:

- `docs/01-功能清单.md`: keep F01 access-key verification, F02 session-level login state, F06 API key filtering, F73/F75 browser-configurable model settings.
- `docs/02-上下游.md`: keep simple password as the POC substitute for SSO/LDAP.
- `docs/03-数据流向.md`: keep browser -> backend -> OpenAI-compatible model flow and the existing SSE event contract.
- `docs/04-业务架构.md`: do not add persistent chat records, real system integration, or a production account system.
- `docs/05-技术架构.md`: keep custom lightweight filters instead of introducing Spring Security for this phase.

## Goals

- Require backend-verifiable authentication for chat and model-connection test APIs.
- Keep the existing access-key login experience.
- Keep session-scoped browser login state so closing the browser clears the client login state.
- Keep existing data APIs protected by `X-API-Key`.
- Reduce model `baseUrl` abuse risk by validating allowed hosts and rejecting local/private network targets.
- Preserve current SSE protocol, fallback analytics behavior, local model settings, and UI layout.

## Non-Goals

- No username/password account system.
- No roles, permissions, JWT refresh-token flow, SSO, LDAP, or OAuth.
- No persistent server-side chat history.
- No server-side model API key vault.
- No database schema changes.
- No Spring Security migration in this phase.

## Recommended Approach

Use a lightweight signed session token. This is the smallest change that gives the backend an enforceable login boundary while preserving the POC access-key flow.

Alternatives considered:

- Keep the current frontend-only login state. This is lowest effort, but it leaves chat and model-test endpoints publicly callable.
- Add a full user/JWT/Spring Security stack. This is stronger, but conflicts with the POC scope and adds unnecessary moving parts.

## Backend Design

### Session Token

Add `SessionTokenService` to create and validate an HMAC-signed token. The token is stateless and contains:

- issued-at timestamp
- expires-at timestamp
- random nonce or token id

The signature secret comes from configuration. If not supplied, the app may derive a demo secret from the access key for local use, but logs should warn that production-like deployments must provide an explicit secret.

Default TTL: 8 hours.

Configuration:

- `app.auth.session-secret`
- `app.auth.session-ttl`

### Auth API

Keep `POST /api/auth/verify`.

Successful response:

```json
{
  "success": true,
  "sessionToken": "...",
  "expiresAt": "2026-05-21T16:00:00Z"
}
```

Failed response may remain HTTP 200 with `success: false` to preserve the current UI flow, unless validation itself fails. This avoids unnecessary frontend behavior churn.

### Request Authentication

Add a lightweight filter for session-protected endpoints:

- `/api/chat/**`
- `/api/model-config/**`

These endpoints require:

```http
Authorization: Bearer <sessionToken>
```

The important change is aggregate protection, not forcing every endpoint through the same credential type. The existing `ApiKeyFilter` can continue to skip `/api/chat/**` and `/api/model-config/**` to avoid requiring browser clients to know `X-API-Key`, but those paths must be covered by the new session-token filter. The combined behavior should be:

| Path group | API key filter | Session-token filter |
| --- | --- | --- |
| static resources, `/api/auth/**`, `/actuator/health` | skipped | skipped |
| `/api/chat/**`, `/api/model-config/**` | skipped | required |
| `/api/v1/data/**`, analytics data APIs | required | skipped |
| H2 console | keep existing local POC behavior | skipped |

Data APIs keep the current `X-API-Key` protection. This phase does not merge access-token auth with API-key auth.

### Model Base URL Guard

Extend `ModelConfigService` validation before constructing `OpenAiApi`.

Rules:

- scheme must be `http` or `https`
- host must be present
- reject localhost names
- reject loopback, private, link-local, multicast, and unspecified IP addresses
- if `app.model.allowed-hosts` is non-empty, host must match it exactly or through a documented wildcard suffix rule such as `*.example.com`
- preserve path handling for `/v1` and non-`/v1` compatible endpoints

Configuration:

- `app.model.allowed-hosts`
- `app.model.allow-private-hosts` defaults to `false`, intended only for local demos

Connection-test failures from URL policy return a normal `ModelConfigTestResponse(false, "...")`.

Chat failures caused by model URL policy continue through the SSE `error` event path.

## Frontend Design

### Auth State

Update `useAuth` so session state stores:

- `sessionToken`
- `expiresAt`

The login state is true only when a token exists and is not locally expired. The token remains in `sessionStorage`, keeping the documented session-level behavior.

On sign-out:

- clear token state
- clear auth storage
- clear current chat session id as the app already does

### API Client

Add a small token reader shared by API modules. Calls to chat, clear-session, and model connection test include:

```http
Authorization: Bearer <sessionToken>
```

If a response is `401`, map it to a localized "login expired" style message and move the app back to the login flow where practical.

### Model Settings

Keep browser-local model settings in `localStorage`. This matches the existing requirement and the prior model-settings design. This change does not attempt to secure provider API keys beyond documenting that this remains demo-oriented.

## Error Handling

Backend `401` response body:

```json
{
  "code": 401,
  "message": "Login session expired."
}
```

Frontend handling:

- Login failure continues to show the current login error.
- Missing/expired token during normal REST calls shows a friendly auth-expired message.
- Missing/expired token during SSE request results in a failed fetch and the same friendly auth-expired message.
- Model URL policy rejection is shown as an invalid model configuration or connection failure, not as an internal server error.

## Testing

Backend tests:

- `SessionTokenServiceTest`: creates valid token, rejects tampered token, rejects expired token.
- Auth controller test: successful access key returns token and expiry; failed access key does not.
- Filter test: chat/model endpoints reject missing token, reject bad token, accept valid token.
- Existing API-key tests: data APIs still require `X-API-Key`.
- Model config tests: reject localhost/private IP/disallowed host; accept allowed host; preserve existing invalid URL behavior.
- Chat stream test: URL policy failure emits SSE `error`.

Frontend tests:

- `useAuth` stores token/expiry in `sessionStorage`.
- Expired token is treated as logged out.
- Chat and model config APIs attach `Authorization`.
- 401 responses map to localized login-expired copy.
- Existing streaming, model settings, and login tests remain green.

Verification commands:

```powershell
cd backend
mvn "-Dfrontend.skip=true" test

cd ..\frontend
npm.cmd run test
npm.cmd run build
```

## Acceptance Criteria

- Users still log in with the documented access key flow.
- Closing the browser still clears the client-side login state.
- `/api/chat/**` and `/api/model-config/**` cannot be called without a valid signed session token.
- `/api/v1/data/**` and analytics data APIs retain `X-API-Key` behavior.
- Existing SSE event protocol stays unchanged.
- Model connection test and chat reject unsafe or disallowed model base URLs.
- No account system, database migration, persistent chat history, or Spring Security dependency is introduced.
