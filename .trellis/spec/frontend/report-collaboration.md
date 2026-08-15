# Report Collaboration Workflow

> Executable Vue contracts for the report queue, workflow controls, immutable history, and optimistic conflicts.

## Scenario: Report Collaboration Workspace

### 1. Scope / Trigger

- Trigger: any frontend change to the report collaboration tab, queue filters, report detail, status/assignee controls, comments, timeline, permissions, or conflict handling.
- This is a frontend/API/security contract. UI gates improve usability; backend permission and tenant/organization checks remain authoritative.

### 2. Signatures

- API module: `src/api/reportCollaboration.js` uses `requestJson()` for all six `/api/report-collaborations` endpoints.
- Composable: `useReportCollaboration({currentUser,dictionary,onAuthExpired})` owns list/detail/filter/loading/error/pending/conflict state.
- Workspace: `components/reporting/ReportCollaborationView.vue`, selected from `ChatView.vue` with `REPORT_READ`.
- Permission catalog: `constants/permissionCatalog.js` includes `REPORT_COLLABORATE`.
- Mutation payloads always carry `selected.report.version`; comments also carry a trimmed body limited by the 2,000-character control.

### 3. Contracts

- A `REPORT_READ`-only identity defaults to the collaboration report queue, can read report markdown and history, and sees no status, assignee, or comment controls. The existing report subscription tab remains separate.
- `REPORT_COLLABORATE` reveals mutation controls only while the report is non-terminal. `RESOLVED` and `CLOSED` show an explicit terminal notice and no mutation form.
- The list supports status, current assignee, organization, and inclusive generated-date filters. Query construction stays in the API module; components never call raw `fetch()`.
- Selecting or refreshing a report loads detail and report-specific eligible assignees together. Malformed list/detail payloads normalize to empty/null safe states.
- A successful mutation replaces both selected detail and the matching queue summary so displayed status, assignee, and version remain synchronized.
- A 409 containing `data.currentVersion` is an optimistic concurrency conflict and shows a localized reload action. A 409 without that field is a state-machine/terminal conflict, not a version conflict.
- Reloading after conflict discards stale server state but never automatically resubmits a comment or mutation.
- Comments render as ordered, immutable timeline entries. The UI provides no reply, mention, edit, or delete affordance.
- Every visible label, status, event, empty/loading/error/conflict message, and permission label exists in both `messages.zh` and `messages.en`.
- The master/detail workspace switches to one column below 820px and remains usable without root horizontal overflow at phone widths.

### 4. Validation & Error Matrix

| Condition | Required UI behavior |
| --- | --- |
| Missing `REPORT_READ` | Hide collaboration tab and make no collaboration API call |
| `REPORT_READ` without collaborate permission | Render full read-only detail and history |
| Terminal report | Hide all mutation controls and show terminal notice |
| HTTP 401 | Call root sign-out flow |
| HTTP 403 | Show localized forbidden feedback |
| HTTP 400 | Show localized safe validation feedback, not raw backend text |
| 409 with `data.currentVersion` | Preserve current UI, expose latest version, require explicit reload |
| 409 without current version | Show localized workflow-state conflict without a false version action |
| Empty or malformed list/detail | Render explicit empty/error state, never a blank successful panel |

### 5. Good/Base/Bad Cases

- Good: after an assignee change returns version 4, both the queue item and open detail show the new assignee/version.
- Good: a stale comment at version 3 receives current version 4, retains the user's page context, and requires reload before another explicit submit.
- Base: a viewer opens directly on the report queue and can inspect markdown and the creation/status/comment timeline.
- Bad: infer edit permission from a role name, expose controls on a closed report, persist a draft comment in Web Storage, or treat every HTTP 409 as a stale-version conflict.

### 6. Tests Required

- `api/__tests__/reportCollaboration.spec.js`: exact paths, query encoding, methods, versioned bodies, and shared-client use.
- `composables/__tests__/useReportCollaboration.spec.js`: list/detail synchronization, permission gate, current-version extraction, state-conflict distinction, and 401 delegation.
- `components/__tests__/ReportCollaborationView.spec.js`: markdown/history rendering, comment submit, read-only mode, terminal mode, and explicit states.
- `views/__tests__/ChatView.spec.js`: tab visibility and collaboration default for report-read-only identities while subscriptions remain available.
- Final frontend gates: `npm.cmd run lint`, `npm.cmd test -- --run`, and `npm.cmd run build`.

### 7. Wrong vs Correct

Wrong:

```js
const canEdit = currentUser.value.roles.includes("ANALYST");
if (error.status === 409) conflictVersion.value = selected.value.report.version;
```

Correct:

```js
const canCollaborate = computed(() =>
  new Set(currentUser.value?.permissions ?? []).has("REPORT_COLLABORATE"));
const currentVersion = readConflictVersion(error.body);
if (error.status === 409 && currentVersion !== null) {
  conflictVersion.value = currentVersion;
}
```
