# Model Usage and Cost Governance

> Executable Vue contracts for the tenant model-usage dashboard, price history, and budget administration.

## Scenario: Model Usage Administration Workspace

### 1. Scope / Trigger

- Trigger: any browser change to model-usage navigation, date filters, usage aggregation, recent events, price versions, or budget policy controls.
- This is a frontend/API/security contract. UI gates improve usability, while backend RBAC and tenant resolution remain authoritative.

### 2. Signatures

- API module: `src/api/modelUsage.js` uses the shared `requestJson()` client for summary, events, prices, and budget endpoints.
- Composable: `useModelUsage({currentUser,dictionary,onAuthExpired})` exposes read/manage gates, load state, data refs, feedback, `load`, `addPrice`, and `saveBudget`.
- Workspace: `components/admin/ModelUsagePanel.vue`, mounted by `AdminView.vue` for `MODEL_USAGE_READ`.
- Permissions: `MODEL_USAGE_READ` shows the panel; `MODEL_USAGE_MANAGE` additionally shows price and budget mutation forms.

### 3. Contracts

- The browser sends ISO `from` and exclusive `to` instants derived from local date inputs; tenant ID is never sent.
- Initial and refresh loads request summary, events, prices, and budget together. A failed load renders an explicit error/retry state and never fabricates an empty successful result.
- Summary renders calls, known input/output tokens, estimated cost grouped by currency, failures, unknown-token calls, high-cost scenario/model tables, anomalies, and recent events.
- Unknown tokens and unknown cost remain visibly unknown; the UI never substitutes zero or claims provider-invoice accuracy.
- Read and manage permissions are checked separately from `currentUser.permissions`, defaulting missing permissions to deny. Hidden controls are not authorization.
- Price versions are append-only in the UI. A successful create prepends the returned version and clears the form; existing history has no edit/delete control.
- Budget edits send the loaded optimistic `version`. Hard-limit, fail-open, reservation, threshold, currency, and monthly limit values reflect the saved backend response.
- All text exists in both `zh` and `en` dictionaries. Components use the shared API layer and do not persist usage, prices, policies, or tenant identifiers in Web Storage.

### 4. Validation & Error Matrix

- Missing `MODEL_USAGE_READ` -> no usage panel and no usage API request.
- Missing `MODEL_USAGE_MANAGE` -> read-only dashboard and price history; no mutation forms.
- HTTP 401 -> invoke `onAuthExpired`; HTTP 403 -> localized forbidden state; HTTP 400 -> localized validation plus safe server detail; HTTP 409 -> localized conflict and retain loaded policy.
- Empty/invalid date range -> browser constraints or backend 400; never silently widen the requested range.
- Unknown token/cost field -> render the localized unknown label.
- Mutation pending -> disable submit commands to prevent duplicate price versions or policy writes.

### 5. Good/Base/Bad Cases

- Good: a read-only administrator changes the date range and reconciles totals, anomalies, and events without seeing mutation controls.
- Good: a manager saves a budget, receives a new version, and the summary budget state updates from the response.
- Base: no prices or events renders explicit empty states while aggregate counts remain usable.
- Bad: default missing permissions to allow, send a tenant ID, use raw `fetch()`, persist governance responses, or display unknown cost as `0 USD`.

### 6. Tests Required

- `api/__tests__/modelUsage.spec.js`: exact paths, encoded range parameters, methods, and request bodies through the shared client.
- `composables/__tests__/useModelUsage.spec.js`: permission gates, combined load, mutation state, 401 delegation, 400 detail, and 409 preservation.
- `components/__tests__/ModelUsagePanel.spec.js`: loading/error/empty/unknown states, range refresh, read-only controls, price append, and budget version.
- `components/__tests__/AdminView.spec.js` and `views/__tests__/ChatView.spec.js`: permission catalog, navigation visibility, and workspace mounting.
- Final frontend gates: `npm.cmd run lint`, `npm.cmd test -- --run`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:

```js
const canManage = currentUser?.roles?.includes("ADMIN") ?? true;
await fetch(`/api/admin/model-usage/summary?tenantId=${tenantId}`);
```

Correct:

```js
const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
const canManage = computed(() => permissions.value.has("MODEL_USAGE_MANAGE"));
await getModelUsageSummary({ from, to }); // shared client; tenant comes from AuthPrincipal
```
