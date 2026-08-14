# Email Notification Delivery

> Executable backend contracts for tenant membership email, tenant SMTP configuration, and durable per-recipient report delivery.

## Scenario: Tenant SMTP Report Delivery

### 1. Scope / Trigger

- Trigger: any change to tenant membership email, SMTP settings, notification secrets, report delivery persistence, SMTP result classification, retries, or delivery APIs.
- This is a database, security, network, and cross-layer contract. Migration V10, JPA mappings, authorization, API schemas, and Jakarta Mail behavior must remain aligned.

### 2. Signatures

- Migration: `backend/src/main/resources/db/migration/V10__create_email_delivery_channel.sql`.
- Membership column: nullable `tenant_memberships.email VARCHAR(254)` with unique `(tenant_id, email)`.
- SMTP table: `tenant_smtp_configs`, one row per tenant, optimistic `version`, encrypted `password_ciphertext`, and `secret_version`.
- Outbox table: `report_deliveries`, unique `(report_job_id, channel_key, recipient_user_id)` and unique `delivery_key`.
- SMTP APIs: `GET/PUT/DELETE /api/notification/smtp` and `POST /api/notification/smtp/test`.
- Membership email API: `PUT /api/admin/users/{id}/email` with `{email, version}`.
- Delivery APIs: `GET /api/report-deliveries`, `POST /api/report-deliveries/{id}/retry`, and `POST /api/report-deliveries/{id}/force-replay` with `{acknowledgeDuplicateRisk:true}`.
- Application port: `ReportDeliveryPort.deliver(DeliveryRequest) -> DeliveryResult`.
- Statuses: `READY`, `SENDING`, `RETRY_WAIT`, `SUCCEEDED`, `PERMANENT_FAILURE`, `UNKNOWN`, `CANCELLED`.
- Environment keys: `APP_NOTIFICATION_SECRET_KEY`, `APP_NOTIFICATION_SMTP_ALLOWED_HOSTS`, the three SMTP timeout keys, and `APP_NOTIFICATION_MAX_MESSAGE_BYTES`.

### 3. Contracts

- Membership email belongs to the tenant membership, not the global user. Normalize it to lowercase, validate exactly one mailbox with Jakarta Mail, reject display-name/header syntax and CR/LF, and preserve `NULL` for existing memberships without email.
- Full membership email is visible only through user administration. Report recipient views expose `emailConfigured` and user identity metadata, never the complete address.
- Each tenant has at most one SMTP configuration. Only `STARTTLS:587` with required upgrade or `SMTPS:465` with implicit TLS is supported. The normalized host must exactly match the configured outbound allowlist.
- The SMTP password is write-only and encrypted with the notification-specific AES-256-GCM key plus tenant-bound AAD. GET, API errors, logs, audits, and delivery rows must not expose plaintext or ciphertext.
- A nonblank replacement password is protected exactly as submitted, including leading or trailing whitespace. A blank password on an existing config preserves the current ciphertext; a new config requires a password.
- Dynamic senders always configure finite connection, read, and write timeouts. Do not add a global `spring.mail` account or plaintext SMTP fallback.
- `ReportDeliveryService.materialize(...)` uses `Propagation.MANDATORY`. A successful report job and its per-recipient outbox rows commit together; remote SMTP work occurs only later in `ReportDeliveryRunner`.
- One delivery row and one `text/plain; charset=UTF-8` message are created per recipient. The body includes the complete report Markdown. `X-Report-Delivery-Key` is diagnostic only and is not provider idempotency.
- `SUCCEEDED` means the configured SMTP server accepted responsibility after `DATA`; it does not mean mailbox delivery, display, or read.
- Automatic retry is allowed only for explicit transient outcomes. `UNKNOWN`, including an expired `SENDING` lease, never auto-retries because a duplicate message may result.
- Normal manual retry applies only to `PERMANENT_FAILURE`. Force replay applies only to `UNKNOWN`, requires explicit duplicate-risk acknowledgement, and writes a distinct audit event.
- SMTP administration requires `USER_MANAGE`. Delivery reads require `REPORT_READ`; retry and force replay require `REPORT_GENERATE`. Delivery list/operations are restricted to the current tenant and creator, returning not-found semantics for foreign rows.
- `DeliveryView` contains IDs, channel, status, attempts, retry time, safe error code, timestamps, and version only. It excludes recipient email, SMTP data, provider exceptions, report Markdown, delivery key, lease owner, and provider message ID.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Invalid, multiple-address, control-character, duplicate tenant email | Reject the membership mutation; do not persist or audit the full address |
| SMTP host not allowlisted, invalid mode/port, header injection, missing new password | HTTP 400 with a safe validation message |
| Stale SMTP or membership version | HTTP 409; never overwrite the newer row |
| Missing SMTP config on delete | HTTP 404 |
| Positive completion after SMTP `DATA` | `SUCCEEDED`, no automatic action |
| Explicit SMTP 4xx or connection failure before transaction | `RETRY_WAIT` with bounded 5m/30m/2h backoff plus deterministic jitter |
| SMTP 5xx, explicit invalid recipient, auth failure, TLS failure, invalid config, invalid/oversize message | `PERMANENT_FAILURE`, no automatic retry |
| Generic `SendFailedException` without explicit invalid addresses, socket timeout after send may start, lost final response, unclassified runtime failure | `UNKNOWN`, never automatic retry |
| Expired `SENDING` lease | `UNKNOWN` with `LEASE_EXPIRED_OUTCOME_UNKNOWN` |
| Normal retry of any status except `PERMANENT_FAILURE` | HTTP 409 |
| Force replay without acknowledgement or against a status other than `UNKNOWN` | HTTP 400 or 409 respectively |
| Foreign tenant/creator delivery ID | HTTP 404 without existence disclosure |

Only stable uppercase error codes may be stored. Never persist raw Jakarta Mail exceptions, SMTP banners/replies, credentials, recipient addresses, or message bodies.

### 5. Good/Base/Bad Cases

- Good: report-job success creates one `READY` row for every still-eligible email recipient in the same transaction, and repeated materialization produces no duplicate key.
- Good: an SMTP 421 result waits and retries, while an ambiguous timeout becomes `UNKNOWN` and waits for explicit operator action.
- Good: updating unrelated SMTP metadata with an empty password preserves the existing secret; replacing with `" secret "` preserves both spaces inside the encrypted value.
- Base: a migrated membership without email remains valid but cannot be selected for an email subscription until an administrator configures it.
- Bad: send SMTP inside the report-job transaction, place multiple recipients in one message, or treat the diagnostic header as provider deduplication.
- Bad: map every `SendFailedException` to permanent recipient rejection; without explicit invalid addresses the outcome is ambiguous.
- Bad: automatically replay `UNKNOWN`, expose complete recipient email in delivery JSON, or put report Markdown into outbox metadata.

### 6. Tests Required

- `IdentityInputPolicyTest` and auth administration tests: email normalization, Jakarta Mail validation, header rejection, tenant uniqueness, optimistic version, and safe audit detail.
- `TenantSmtpConfigRegistryTest`: host allowlist, exact TLS/port pairs, secret preserve/replace, password whitespace preservation, redacted view, active-tenant isolation, and delete conflict.
- `AesGcmNotificationSecretProviderTest`: independent key, tenant-bound AAD, tamper/wrong-tenant rejection, and round trip.
- `SmtpReportDeliveryAdapterTest`: one recipient, UTF-8 body/header, serialized size, finite timeouts, 4xx/5xx/auth/TLS/connect/timeout/generic-send classification, and no raw error leakage.
- `ReportDeliveryEntityTest` and `ReportDeliveryServiceTest`: legal transitions, max attempts, same-transaction materialization, idempotency, eligibility recheck, lease recovery, safe views, retry, force acknowledgement, tenant/creator isolation, and audits.
- Controller and real security-filter tests: `USER_MANAGE`, `REPORT_READ`, `REPORT_GENERATE`, 400/404/409 mappings, and redacted response bodies.
- Migration/startup tests: apply V10, validate Hibernate mappings, and parse `openapi.json` as UTF-8 JSON.
- Final gates: `mvn "-Dfrontend.skip=true" pmd:check` and `mvn "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
mailSender.send(message); // inside report-job completion transaction
delivery.markRetry("SMTP_TIMEOUT", now.plusSeconds(30), now);
```

Correct:

```java
@Transactional(propagation = Propagation.MANDATORY)
public List<DeliveryView> materialize(ReportGenerationJobEntity job, ReportDraft draft, Instant now) {
    return createPerRecipientOutboxRows(job, draft, now);
}

// A timeout may follow server acceptance, so it cannot be replayed automatically.
return DeliveryResult.unknown("SMTP_TIMEOUT_UNKNOWN");
```
