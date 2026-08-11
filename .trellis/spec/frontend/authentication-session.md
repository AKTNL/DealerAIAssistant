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
