# SMTP Delivery Contract

## Sources

* Spring Boot email reference: <https://docs.spring.io/spring-boot/reference/io/email.html>
* SMTP protocol: RFC 5321, <https://www.rfc-editor.org/rfc/rfc5321>
* Jakarta Mail address validation: <https://jakarta.ee/specifications/mail/2.1/apidocs/jakarta.mail/jakarta/mail/internet/internetaddress>
* Reviewed: 2026-08-14

## Repository Fit

* Spring Boot 3.4.5 manages the compatible Jakarta Mail implementation through `spring-boot-starter-mail`; no provider-specific SDK is needed.
* The existing report job produces a tenant-scoped `ReportDraft` with title, language, Markdown, and generation time. That is sufficient for a useful `text/plain; charset=UTF-8` email without adding an HTML/Markdown renderer.
* `auth_users` is a globally shared identity and can belong to multiple tenants. The email route therefore belongs on `tenant_memberships`; the tenant administration API and UI already own membership mutation and are the correct place to maintain an optional normalized email without cross-tenant changes.
* The existing tenant model configuration demonstrates the required secret behavior: encrypted credentials, tenant-bound AAD, write-only password updates, and a redacted configuration view. SMTP needs notification-specific types and AAD rather than reusing model configuration entities.

## SMTP Acceptance Semantics

RFC 5321 states that a server issuing the positive completion reply after the end of `DATA` takes responsibility for delivering the message or reporting a later failure. It does not prove that the final mailbox received or displayed the message.

P2-3C therefore defines `SUCCEEDED` as **accepted by the configured SMTP server**. Final delivery, bounce processing, spam-folder placement, and read receipts are not claimed.

RFC 5321 also explicitly discusses duplicate messages caused by timeouts around the final end-of-data reply. SMTP has no standard application idempotency key, so an ambiguous timeout after message transmission must not be automatically replayed.

## Spring Mail Runtime Constraints

Spring Boot documents that the default mail timeouts can be infinite. Every dynamically constructed tenant sender must set finite values for:

* `mail.smtp.connectiontimeout`
* `mail.smtp.timeout`
* `mail.smtp.writetimeout`

The MVP supports authenticated TLS only:

* `STARTTLS` for the normal submission flow (commonly port 587), with TLS required rather than opportunistic.
* Implicit TLS/SMTPS (commonly port 465).
* Plaintext SMTP is rejected.

The application-level egress allowlist must approve the configured SMTP host. Redirects are not relevant to SMTP, but DNS/host validation and explicit ports are required to avoid arbitrary internal network access.

## Address Contract

* Parse and validate addresses with `InternetAddress` and `validate()` rather than a handwritten regular expression.
* Store one canonical lowercase address per tenant membership; the database unique constraint prevents two memberships in the same tenant from sharing it while permitting multiple `NULL` values for migrated memberships.
* Never accept display-name/header syntax in the stored user email field. It must resolve to exactly one mailbox address with no CR/LF characters.
* Tenant report-recipient APIs expose `emailConfigured` to ordinary report users. The full address remains in the tenant user-administration view.
* One SMTP message and one durable delivery row are created per recipient. This avoids partial-recipient ambiguity and gives each recipient independent retry/audit state.

## Message Contract

* Subject: fixed product prefix plus the bounded report title.
* Body: UTF-8 `text/plain` containing report title, type, generation time, and the complete report Markdown.
* Set a stable application delivery key in an `X-Report-Delivery-Key` header for diagnostics only. SMTP servers do not guarantee deduplication from that header.
* Reject control characters in all address/header fields and enforce a conservative serialized message size limit. Do not silently truncate a generated report.
* Do not include credentials, internal trace details, or unprotected report links.

## Result And Retry Matrix

| Observation | Local result | Automatic action |
| --- | --- | --- |
| Positive completion after end of `DATA` | `SUCCEEDED` / SMTP accepted | None |
| Explicit SMTP 4xx temporary failure before acceptance | `RETRY_WAIT` | Bounded retry with backoff and jitter |
| Connection establishment fails before an SMTP transaction begins | `RETRY_WAIT` | Bounded retry |
| SMTP 5xx permanent rejection, invalid recipient, authentication failure, TLS/certificate failure, invalid configuration, message preparation failure | `PERMANENT_FAILURE` | No automatic retry |
| Socket timeout/reset after transaction begins, lost final `DATA` response, worker crash in `SENDING`, or exception whose send stage cannot be proven | `UNKNOWN` | Never auto-retry because the server may already have accepted the message |

An ordinary manual retry may replay only explicit failures. Replaying `UNKNOWN` requires a separate audited force action that acknowledges duplicate-mail risk.

## MVP Configuration

Each tenant owns one enabled SMTP configuration:

* host and port
* security mode: `STARTTLS` or `SMTPS`
* username and write-only password
* from address and optional from display name
* enabled/configured metadata

Configuration APIs must never return the password. The SMTP password is protected with tenant-bound AES-GCM. A test action sends a clearly labeled message to the acting administrator's configured user email and returns only a safe status code/message.
