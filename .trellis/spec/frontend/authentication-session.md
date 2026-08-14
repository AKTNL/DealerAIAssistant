# Authentication Session

> Executable Vue contracts for login, refresh recovery, forced password change, and permission-aware rendering.

## Scenario: Browser Session and Permission UI

### 1. Scope / Trigger

- Trigger: any frontend change to authentication APIs, request retry, session storage, `useAuth`, root-view routing, or permission-gated UI.

### 2. Signatures

- API module: `src/api/auth.js` exports `loginUser`, `refreshSession`, `getCurrentUser`, `changePassword`, and `logoutUser`.
- Storage module: `src/api/sessionToken.js` stores `{accessToken,accessExpiresAt,user}` under `STORAGE_KEYS.auth` in `sessionStorage`.
- Root state: `useAuth({dictionary})` exposes `initialized`, `currentUser`, `authVerified`, `mustChangePassword`, credential refs, and lifecycle methods.
- Views: `LoginView`, `PasswordChangeView`, and `ChatView` are selected by `App.vue` without a router.

### 3. Contracts

- JavaScript can read only the short-lived access token. The refresh token remains in the HttpOnly Cookie and every request uses `credentials: "include"`.
- `request()` attaches the current Bearer token, refreshes once after a protected 401, and retries the original request once. `skipAuthRefresh` is an internal option and must never be forwarded to `fetch()`.
- `refreshSession()` is module-level single-flight: concurrent callers await the same promise.
- Page initialization calls `me` when an access token is valid, otherwise refreshes from the Cookie. Any recovery failure clears token and identity state.
- `mustChangePassword=true` renders only `PasswordChangeView`; successful change clears local identity because the backend revoked every session.
- `ChatView` derives gates from `currentUser.permissions`. Missing user/permissions default to an empty set, never an allowlist.
- API calls stay in `src/api/`; components/composables never call raw `fetch()` or read the refresh Cookie.

### 4. Validation & Error Matrix

- Malformed/expired stored access session -> ignore/clear it and attempt Cookie recovery.
- Login failure -> generic localized error; password value cleared.
- First protected 401 -> one refresh; refresh failure -> clear local state and return to login.
- Valid session with missing permission -> hide/disable that UI entry; backend 403 remains authoritative.
- Password-change failure -> remain on the forced-change view with localized error.
- Logout request failure -> still clear browser access/identity state.

### 5. Good/Base/Bad Cases

- Good: several simultaneous 401 responses await one refresh, then retry with the rotated access token.
- Good: a `CHAT_USE`-only user sees Chat but not Dashboard or model settings.
- Base: a user with no UI permissions still sees the authenticated shell and logout action.
- Bad: store the refresh token in Web Storage, create a second refresh promise per request, or default missing permissions to administrator-like access.

### 6. Tests Required

- `api/__tests__/auth.spec.js`: login/password/logout request contracts and refresh single-flight.
- `api/__tests__/client.spec.js` and `sessionToken.spec.js`: Bearer injection, retry/error behavior, storage normalization, and expiry.
- `composables/__tests__/useAuth.spec.js`: login, Cookie restoration, forced password change, and cleanup.
- `views/__tests__/LoginView.spec.js`, `PasswordChangeView.spec.js`, and `ChatView.spec.js`: form states and explicit permission allow/deny rendering.
- Final gates: `npm.cmd run lint`, `npm.cmd test`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:

```js
localStorage.setItem("refreshToken", response.refreshToken);
const canDashboard = currentUser?.permissions?.includes("DASHBOARD_READ") ?? true;
```

Correct:

```js
const permissions = computed(() => new Set(props.currentUser?.permissions ?? []));
const canDashboard = computed(() => permissions.value.has("DASHBOARD_READ"));
return fetch(url, { ...options, credentials: "include" });
```

---

## Scenario: Permission and Organization Administration Workspace

### 1. Scope / Trigger

- Trigger: any browser change to administration navigation, user/role mutations, organization nodes, dealer mappings, grants, session revocation, audit display, or temporary-password handling.
- This is a frontend/API/security contract because UI gates improve usability but every administration request still relies on backend permission checks and current database state.

### 2. Signatures

- Workspace: `components/admin/AdminView.vue`, selected from `ChatView.vue` when the user has `USER_READ`, `ROLE_READ`, or `ORGANIZATION_READ`.
- State: `useAdministration({currentUser,dictionary,onAuthExpired,onIdentityRevoked})`.
- API module: `api/administration.js` through `requestJson()` only.
- Shared permission catalog: `constants/permissionCatalog.js`.
- Temporary-password generator: `utils/temporaryPassword.js -> createTemporaryPassword()` using `crypto.getRandomValues()`.

### 3. Contracts

- Read and mutation controls are gated separately. A read-only administrator sees only resource sections authorized by its exact permission set and never sees mutation forms/buttons for missing manage authorities.
- User, role, and organization update requests send the loaded `version`; HTTP 409 renders the localized refresh/conflict state.
- Create/reset password generates the temporary password in the browser, submits it once, and stores it only in `oneTimePassword` memory until the modal is dismissed.
- Temporary passwords never enter Web Storage, console output, URL/query state, telemetry, or error detail.
- User disable, role assignment, password reset, session revocation, role permission updates, organization updates, and grant replacement require explicit confirmation and expose pending state.
- An administration mutation that revokes the current user's sessions calls `onIdentityRevoked` and returns the SPA to login.
- Lists render loading, empty, error, forbidden, validation, and conflict states; raw backend validation detail is shown only for HTTP 400.

### 4. Validation & Error Matrix

- Missing access session / HTTP 401 -> call the root sign-out flow.
- Missing exact permission / HTTP 403 -> localized forbidden state; do not retry as another administration resource.
- Invalid form or hierarchy / HTTP 400 -> localized validation error plus server validation detail.
- Stale entity version or final-administrator protection / HTTP 409 -> localized conflict message and require refresh.
- Secure random API unavailable -> abort password generation; never fall back to `Math.random()`.
- Page reload after a successful create/reset -> the one-time password is absent.

### 5. Good/Base/Bad Cases

- Good: a `USER_READ`-only user can inspect users and audit metadata but cannot create, disable, reset, assign, or revoke.
- Good: resetting the signed-in administrator's password shows the password once and then signs the browser out because the backend revoked all sessions.
- Base: an empty organization or audit list renders an explicit empty state rather than a blank panel.
- Bad: persist the temporary password in local/session storage so the modal can survive reload.
- Bad: treat hidden buttons as authorization or default missing permissions to allow.

### 6. Tests Required

- `api/__tests__/administration.spec.js`: exact paths, methods, grant reads, audit reads, and mutation version payloads.
- `composables/__tests__/useAdministration.spec.js`: permission-scoped loading, in-memory password lifecycle, 401 delegation, and 409 classification.
- `components/__tests__/AdminView.spec.js`: read-only controls, loading/empty/error/conflict states, and one-time password dialog.
- `views/__tests__/ChatView.spec.js`: administration tab visibility and default workspace for administration-only identities.
- Browser smoke: administrator login, management tab, create-user modal, modal dismissal, reload absence, and zero console errors.
- Final gates: `npm.cmd run lint`, `npm.cmd test`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:

```js
localStorage.setItem("newUserPassword", password);
const canAdmin = currentUser?.roles?.includes("ADMIN");
```

Correct:

```js
const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
const canManageUsers = computed(() => permissions.value.has("USER_MANAGE"));
oneTimePassword.value = { label: user.displayName, password };
```

## Scenario: Tenant Selection and Server-Side Model Credentials

### 1. Scope / Trigger
- Trigger: any frontend change to tenant switching, `/api/auth/me`, model settings, chat payloads, or auth cleanup.

### 2. Signatures
- Tenant storage: `getSelectedTenantKey()/setSelectedTenantKey()/clearSelectedTenantKey()` uses `sessionStorage` only.
- API client: every request adds `X-Tenant-Key` when selected; the value is never treated as authorization.
- Auth response: `user.tenants[]` and nullable `user.currentTenant`; roles/permissions are empty until a required multi-membership selection is made.
- Model API: `GET/PUT/DELETE /api/model-config`, `POST /api/model-config/test`.
- Chat API: `streamChat({sessionId,message})`; no model credentials in the request body.

### 3. Contracts
- Tenant selection clears on logout/auth recovery failure and is sent only as a normalized lowercase key.
- Switching tenant refreshes `/api/auth/me` and recreates tenant-local Chat state; stale tenant messages must not remain visible.
- Browser model settings contain metadata and an `apiKeyConfigured` flag only. Legacy stored credentials are deleted during read and never rewritten.
- Existing API keys are represented by a fixed mask; blank save preserves the server-side key. API responses never expose plaintext or ciphertext.

### 4. Validation & Error Matrix
- Multiple memberships without selection -> show tenant choices, no business workspace permissions.
- Unknown/disabled tenant header -> backend 403; clear the selection only when the server confirms it is invalid.
- Model config 401 -> sign out; 403 -> show forbidden state; 400 -> show validation feedback without raw secrets.
- Chat request inspection -> body contains only `sessionId` and `message`; any legacy model fields are a regression.

### 5. Good/Base/Bad Cases
- Good: switching from tenant A to B changes `/api/auth/me` and rebuilds Chat state before B data is rendered.
- Base: a single-membership user has an automatic current tenant and no selector is required.
- Bad: persist tenant choice in localStorage, trust a tenant key as authorization, or send a masked/API key value in chat.

### 6. Tests Required
- API client test: selected tenant header and auth cleanup behavior.
- Auth/composable tests: tenant choices, current tenant, and selection refresh.
- Model config/chat tests: no storage of credentials, no key in request bodies, fixed mask handling.
- Final gates: `npm.cmd run lint`, `npm.cmd test -- --run`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:
```js
localStorage.setItem("modelApiKey", apiKey);
streamChat({ sessionId, message, apiKey });
```

Correct:
```js
setSelectedTenantKey(tenant.key); // sessionStorage selection intent only
streamChat({ sessionId, message }); // server resolves encrypted tenant config
```

## Scenario: Report Subscription Workspace

### 1. Scope / Trigger

- Trigger: the authenticated SPA exposes the tenant-scoped report subscription list, controlled schedule form, and enable/disable/delete actions.
- This is a cross-layer permission and API contract; hidden controls are usability only and backend RBAC remains authoritative.

### 2. Signatures

- API module: `src/api/reportSubscriptions.js` uses `requestJson()` for the six subscription endpoints.
- Composable: `useReportSubscriptions({ currentUser, dictionary, onAuthExpired })` returns loading, error, pending, subscription, recipient, and mutation methods.
- Workspace: `components/reporting/ReportSubscriptionsView.vue`, selected from `ChatView.vue` when `REPORT_READ` is present.

### 3. Contracts

- `REPORT_READ` users can inspect their own list and eligible recipient directory; `REPORT_GENERATE` additionally reveals create/edit/enable/disable/delete controls.
- All user-facing text comes from both `zh` and `en` dictionaries. API calls never use raw `fetch()` or browser storage.
- The form sends only controlled `DAILY`/`WEEKLY`/`MONTHLY` fields, a normalized channel key, selected tenant user IDs, and the optimistic `version` on edits.
- Editing filters recipients that are no longer eligible instead of silently submitting hidden stale IDs. Enablement is a separate action; edit mode does not display a non-functional enable checkbox.
- Loading, empty, forbidden, validation, conflict, ineligible, and 401 sign-out states are explicit. A 409 leaves the current list unchanged and asks the user to refresh.

### 4. Validation & Error Matrix

- Missing `REPORT_READ` -> no subscription tab and no subscription API call from the workspace.
- HTTP 401 -> call the root sign-out callback; HTTP 403 -> localized forbidden message; HTTP 400 -> localized/server validation message; HTTP 409 -> localized conflict state.
- Empty recipients disable submit; topic is required only for topic reports; monthly day input is constrained to `1..28`.
- Refresh failure keeps the page state explicit and never fabricates an empty successful list.

### 5. Good/Base/Bad Cases

- Good: a report-read-only user sees cards but no form or mutation actions.
- Good: a subscription with a revoked recipient can be edited to remove or replace that recipient.
- Base: an ineligible subscription remains visible with its backend reason and can be refreshed after authorization changes.
- Bad: default missing permissions to allow, persist tenant/recipient data in Web Storage, or show an edit toggle that the backend ignores.

### 6. Tests Required

- `api/__tests__/reportSubscriptions.spec.js`: exact paths, methods, request bodies, and shared client usage.
- `composables/__tests__/useReportSubscriptions.spec.js`: loading, list replacement, conflict preservation, and 401 delegation.
- `components/__tests__/ReportSubscriptionsView.spec.js`: controlled form, read-only gate, revoked-recipient edit state, and explicit status states.
- `views/__tests__/ChatView.spec.js`: tab visibility and default workspace for report-read-only users.
- Final frontend gates: `npm.cmd run lint`, `npm.cmd test -- --run`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:

```js
const canManage = currentUser?.permissions?.includes("REPORT_GENERATE") ?? true;
localStorage.setItem("subscription", JSON.stringify(form));
```

Correct:

```js
const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
const canManage = computed(() => permissions.value.has("REPORT_GENERATE"));
return requestJson("/api/report-subscriptions", { method: "POST", body: JSON.stringify(input) });
```
