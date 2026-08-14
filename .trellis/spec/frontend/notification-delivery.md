# Email Notification Delivery UI

> Executable Vue contracts for tenant email readiness, SMTP administration, delivery status, and responsive access.

## Scenario: Email Configuration and Delivery Workspace

### 1. Scope / Trigger

- Trigger: any frontend change to membership email, SMTP settings, report recipients, report delivery status/actions, or the responsive workspace shell that contains these controls.
- This is a frontend/API/security contract. UI gates are for usability; backend authorization and tenant isolation remain authoritative.

### 2. Signatures

- SMTP API module: `src/api/notificationSmtp.js` exports `getSmtpConfig`, `saveSmtpConfig`, `deleteSmtpConfig`, and `testSmtpConfig` through `requestJson()`.
- Report API module: `src/api/reportSubscriptions.js` also exports `listReportDeliveries`, `retryReportDelivery`, and `forceReplayReportDelivery`.
- SMTP component: `components/admin/NotificationSmtpPanel.vue`, shown from `AdminView.vue` only with `USER_MANAGE`.
- Report component/composable: `components/reporting/ReportSubscriptionsView.vue` and `useReportSubscriptions(...)`.
- Recipient shape includes `userId`, identity display fields, and `emailConfigured`; it does not include the address.

### 3. Contracts

- User administration can create/update a tenant membership email with optimistic `version`. The full address remains inside the user administration workspace.
- An email subscription may select only recipients with `emailConfigured === true`. Missing-email recipients remain visible with a localized reason and disabled checkbox.
- SMTP password input is always empty after load/save. `passwordConfigured` controls whether it is required; no plaintext or mask is written back into the field, storage, console, URL, or error state.
- Saving sends `password:null` when the operator leaves an existing secret unchanged. A newly entered password is not trimmed before transmission.
- SMTP test/delete stay disabled until a configuration exists. Delete and `UNKNOWN` force replay require explicit confirmation.
- The delivery list displays recipient name, safe status, `attempt/maxAttempts`, update/retry times, and safe error code only. It never displays recipient email, SMTP metadata, provider exception/detail, report body, or delivery key.
- `REPORT_READ` loads subscription, recipient, and delivery lists. `REPORT_GENERATE` additionally enables subscription mutations, retry for `PERMANENT_FAILURE`, and force replay for `UNKNOWN`.
- All visible strings and status labels exist in both zh and en dictionaries. API calls remain in `src/api/`; components and composables do not call raw `fetch()`.
- The authenticated shell supports phone widths from 320px. At `max-width:1040px`, both normal and `sidebar-collapsed` shells use one `minmax(0, 1fr)` column. At `max-width:720px`, the topbar stacks identity and wrapping tools so title/actions remain readable.
- Browser smoke at 390px must show `documentElement.scrollWidth === clientWidth`, a positive-width `main`, visible report/SMTP forms, and no console errors.

### 4. Validation & Error Matrix

| Condition | Required UI behavior |
| --- | --- |
| Recipient lacks email | Show localized missing-email state; disable selection and creation if none remain |
| SMTP config has existing password | Render an empty optional password input; blank save preserves it |
| HTTP 401 | Delegate to root sign-out flow |
| HTTP 403 | Show localized forbidden/request state; do not reveal hidden admin sections |
| HTTP 400 | Show safe validation feedback without secret/server detail |
| HTTP 409 | Keep current form/list and ask the user to refresh |
| `PERMANENT_FAILURE` | Show normal retry only to `REPORT_GENERATE` users |
| `UNKNOWN` | Show force replay only to `REPORT_GENERATE` users and confirm duplicate risk first |
| `SUCCEEDED` | Label as SMTP accepted; do not claim mailbox delivery or read |
| 320-390px viewport | No root horizontal overflow, zero-width main column, vertical product title, or clipped action toolbar |

### 5. Good/Base/Bad Cases

- Good: saving a membership email immediately changes the recipient checkbox from disabled/missing to selectable without exposing the address in the report view.
- Good: an existing SMTP config reloads with `passwordConfigured=true` and an empty password field; updating display name does not replace the secret.
- Good: an `UNKNOWN` delivery shows one force-replay action whose confirmation warns about duplicate mail.
- Base: no SMTP config renders an editable empty form with test/delete disabled and enabled delivery selected by default.
- Bad: use a role name instead of exact permissions, show a full recipient address in delivery cards, or treat hidden controls as authorization.
- Bad: fill a password input with a mask, trim the user's SMTP password, auto-confirm force replay, or show raw backend/SMTP errors.
- Bad: enforce a 768px body minimum that pushes the mobile `main` into a zero-width grid column.

### 6. Tests Required

- `api/__tests__/notificationSmtp.spec.js` and `reportSubscriptions.spec.js`: exact paths, methods, bodies, acknowledgement payload, and shared-client usage.
- `components/__tests__/NotificationSmtpPanel.spec.js`: load/save redaction, preserve-secret payload, test/delete states, 401 delegation, and confirmation.
- `components/__tests__/AdminView.spec.js`: SMTP tab and membership email controls appear only with `USER_MANAGE`.
- `components/__tests__/ReportSubscriptionsView.spec.js`: `emailConfigured` selection gate, delivery status fields, status-specific actions, and force confirmation.
- `composables/__tests__/useReportSubscriptions.spec.js`: concurrent refresh replacement, delivery retry/force updates, pending state, and 401 handling.
- `src/__tests__/styleTokens.spec.js`: 320px root minimum, collapsed single-column mobile shell, and stacked/wrapping mobile topbar contract.
- Browser smoke: administrator email save, SMTP panel, report recipient readiness, delivery empty/status view, 1440px and 390px screenshots, no horizontal overflow, and zero console errors.
- Final gates: `npm run lint`, `npm test`, and `npm run build`.

### 7. Wrong vs Correct

Wrong:

```js
form.password = config.password ?? "********";
const canReplay = currentUser?.roles?.includes("ADMIN") ?? true;
```

Correct:

```js
form.password = "";
form.passwordConfigured = config.passwordConfigured === true;

const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
const canManage = computed(() => permissions.value.has("REPORT_GENERATE"));
```
