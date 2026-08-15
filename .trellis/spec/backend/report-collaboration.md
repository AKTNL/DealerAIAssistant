# Report Collaboration Workflow

> Executable backend contracts for scoped report status, assignees, immutable comments, history, concurrency, and notifications.

## Scenario: Tenant-Scoped Report Collaboration

### 1. Scope / Trigger

- Trigger: any backend change to report collaboration reads, status transitions, assignee selection, comments, history, permissions, or collaboration notifications.
- This is a cross-layer and infrastructure contract because HTTP APIs, report authorization, optimistic locking, three database tables, audit records, and the P2 delivery port participate in one workflow.

### 2. Signatures

- Permission: `PermissionKey.REPORT_COLLABORATE`; built-in `ADMIN` and `ANALYST` roles receive it, while reads require `REPORT_READ`.
- Read APIs:
  - `GET /api/report-collaborations?status&assigneeUserId&organizationId&generatedFrom&generatedTo`
  - `GET /api/report-collaborations/{reportId}`
  - `GET /api/report-collaborations/{reportId}/assignees`
- Mutation APIs:
  - `PATCH /api/report-collaborations/{reportId}/status` with `{status,version}`
  - `PATCH /api/report-collaborations/{reportId}/assignee` with `{assigneeUserId,version}`; nullable assignee clears ownership
  - `POST /api/report-collaborations/{reportId}/comments` with `{body,version}`
- Application entry points: `ReportCollaborationService`, `ReportCollaborationInitializer`, and `ReportCollaborationNotificationService`.
- Storage: `report_collaborations`, append-only `report_collaboration_events`, and durable `report_collaboration_notifications` from `V11__create_report_collaboration_workflow.sql`.
- Delivery boundary: `ReportDeliveryPort.deliver(DeliveryRequest)`; collaboration code never calls SMTP infrastructure directly.

### 3. Contracts

- `ReportDraft` remains immutable. `ReportService.generate(...)` saves the draft and initializes a separate collaboration record in the same application transaction.
- Every list/detail/mutation resolves the current `OrganizationDataScope`; tenant mismatch and reports outside the actor's organization coverage fail closed.
- Status transitions are forward-only: `OPEN -> IN_PROGRESS|CLOSED`, `IN_PROGRESS -> RESOLVED|CLOSED`; `RESOLVED` and `CLOSED` are terminal.
- Assignees must be active members of the same tenant, retain `REPORT_READ`, and have organization coverage for every node in the report scope. Historical identity snapshots remain visible after disablement.
- Comments are trimmed, limited to 2,000 characters, single-level, immutable events. There are no replies, mentions, edits, or deletes.
- Each mutation requires the loaded non-negative JPA `version`. A stale version returns HTTP 409 with `data.currentVersion`; an illegal transition or terminal mutation returns HTTP 409 without pretending it is a stale-version conflict.
- Status, assignee, comment, and creation events retain actor snapshot, timestamp, trace ID, event type, and bounded previous/current summaries. Timeline order is `created_at ASC, id ASC`.
- A notification outbox row is materialized only for the current assignee and never when the assignee is also the actor. Delivery happens after commit through the scheduled runner.
- Before sending, revalidate tenant enablement, current assignee, report presence, recipient membership, `REPORT_READ`, organization scope, and email. Revoked eligibility cancels the outbox row before calling `ReportDeliveryPort`.
- Unknown provider outcomes and expired sending leases are not blindly replayed; they become `UNKNOWN` following the report delivery safety contract.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Missing `REPORT_READ` on GET | HTTP 403; no report metadata or assignee directory |
| Missing `REPORT_COLLABORATE` on mutation | HTTP 403; no state/event/outbox write |
| Cross-tenant or out-of-scope report | Return not found/denied without exposing the report |
| Assignee is disabled, lacks report read, or lacks scope | Reject assignment with HTTP 403 |
| Version differs from stored version | HTTP 409 with `data.currentVersion`; do not mutate |
| Backward transition or terminal mutation | HTTP 409; no event or notification |
| Blank or over-2,000-character comment | HTTP 400; no event |
| Actor is current assignee | Commit collaboration event but create no self-notification |
| Assignee eligibility revoked before delivery | Mark notification `CANCELLED`; do not call provider |

### 5. Good/Base/Bad Cases

- Good: two analysts load version 2; one advances the report and the other receives HTTP 409 with current version 3 instead of overwriting it.
- Good: an analyst assigns an in-scope viewer, then a later comment creates one durable email outbox row for that viewer.
- Base: a `REPORT_READ`-only user can list reports, read markdown, and inspect the complete immutable timeline.
- Bad: trust a report/assignee tenant ID from the request, update a terminal report, send to every collaborator, or call an SMTP sender from `ReportCollaborationService`.

### 6. Tests Required

- `ReportCollaborationStatusTest`: allowed transitions, backward rejection, and terminal states.
- `ReportCollaborationEntityTest`: activity/versioned state invariants and terminal mutation rejection.
- `ReportCollaborationServiceTest`: stale-version current value, scope rejection, disabled assignee, read-only permission, comments, filters, event fields, and notification materialization.
- `ReportCollaborationNotificationServiceTest`: P2 delivery port use and provider avoidance after membership/scope revocation.
- `ReportCollaborationControllerTest`: request validation, envelope shape, filters, version payloads, and HTTP 409 `currentVersion`.
- `AgentPocApplicationStartupTest`: V10-to-V11 role backfill, workflow/event/outbox persistence, OpenAPI JSON pointers, and Flyway-to-Hibernate validation.
- Final backend gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check` and `mvn.cmd "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
collaboration.setStatus(request.status());
smtpSender.send(collaboration.getAssigneeUserId(), comment);
```

Correct:

```java
ReportCollaborationEntity collaboration = requireVersion(
        ensureCollaboration(draft), request.version());
collaboration.changeStatus(ReportCollaborationStatus.parse(request.status()), clock.instant());
ReportCollaborationEventEntity event = recordEvent(actor, collaboration, /* bounded audit fields */);
notificationService.materialize(collaboration, event, actor.userId(), clock.instant());
```
