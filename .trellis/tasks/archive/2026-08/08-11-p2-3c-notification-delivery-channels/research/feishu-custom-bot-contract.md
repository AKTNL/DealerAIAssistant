# Feishu Custom Bot Webhook Contract

## Source

* Official guide: <https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot.md>
* Reviewed: 2026-08-14
* Scope: group custom bots (`/open-apis/bot/v2/hook/{id}`), not Feishu app bots.

## Provider Boundary

* A custom bot can send only to the group in which it was configured. One webhook therefore represents one group target.
* Requests use HTTPS `POST`, `Content-Type: application/json`, and a webhook shaped like `https://open.feishu.cn/open-apis/bot/v2/hook/{id}`.
* The provider limit is 100 requests/minute and 5 requests/second per tenant/bot. Feishu warns that bursts around the hour and half-hour may return rate-limit code `11232`.
* The complete UTF-8 request body must not exceed 20 KB.
* Custom bots cannot retrieve users, respond to messages, or provide application-bot callback semantics. Individual `open_id` mapping requires an application bot and is outside P2-3C.

## Signing

Signing is required for this integration even though Feishu also offers keyword and IP-list security controls.

1. Produce a Unix timestamp in seconds. Feishu accepts timestamps no more than 3600 seconds from its current time.
2. Build the HMAC key as `timestamp + "\n" + secret` using UTF-8.
3. Compute HMAC-SHA256 over the empty byte array.
4. Base64-encode the digest.
5. Include `timestamp` as a JSON string and the Base64 value as `sign` in every request.

The webhook and signing secret are credentials. Persist only tenant-bound encrypted values and never emit them in logs, audit details, database delivery rows, or API responses.

## Minimal Message Contract

P2-3C sends only `msg_type: "text"`. The text contains a fixed report prefix, title, report type, generation time, and a bounded excerpt of the report Markdown. The adapter must validate the serialized UTF-8 body against an internal limit below 20 KB and truncate the excerpt with an explicit marker when necessary.

No report link is included. A usable link would require a public base URL plus an authenticated browser route, which is a larger product and deployment change than the selected webhook MVP.

## Response Contract

Provider success is determined from the modern `code` field, not the legacy `StatusCode`/`StatusMessage` fields. A success response is:

```json
{"code":0,"data":{},"msg":"success"}
```

Custom-bot success does not return a provider message ID, so the application field remains nullable.

Documented rejection codes:

| Code | Meaning | Classification |
| --- | --- | --- |
| `0` | Accepted successfully | `SUCCEEDED` |
| `11232` | Provider rate limit | Retryable with bounded backoff and jitter |
| `9499` | Malformed/oversized request | Permanent payload defect |
| `19021` | Signature mismatch or timestamp outside one hour | Permanent configuration/clock defect |
| `19022` | Source IP not allowed | Permanent configuration defect |
| `19024` | Required keyword absent | Permanent configuration/template defect |

## Retry And Unknown-Outcome Matrix

Feishu custom-bot requests do not accept an application idempotency key. The local delivery key prevents duplicate workers, but it cannot deduplicate two requests that both reach Feishu. Retry policy must therefore distinguish an explicit rejection from an ambiguous outcome.

| Observation | Local result | Automatic action |
| --- | --- | --- |
| HTTP success and JSON `code == 0` | `SUCCEEDED` | None |
| HTTP 429 or explicit `code == 11232` | `RETRY_WAIT` / rate limited | Retry with bounded backoff and jitter; honor a valid `Retry-After` when present |
| A connection failure proven to occur before request transmission | `RETRY_WAIT` / provider unavailable | Bounded retry |
| Read timeout, connection reset after dispatch, worker crash while `SENDING`, HTTP 5xx without a documented rejection, empty/unparseable response | `UNKNOWN` | Never auto-retry; surface for audited manual resolution because the group message may already exist |
| HTTP 4xx other than 429, or documented `9499`, `19021`, `19022`, `19024` | `PERMANENT_FAILURE` | No automatic retry |
| Any unknown nonzero provider code in a parseable response | `PERMANENT_FAILURE` / provider rejected | No automatic retry until explicitly classified in code |
| Redirect response | `PERMANENT_FAILURE` / invalid target | Do not follow redirects |

An operator may explicitly replay an `UNKNOWN` delivery only through an audited force action that acknowledges duplicate-message risk. Ordinary retry APIs must reject `UNKNOWN`.

## Repository Mapping

* Create an application-level provider-neutral delivery port and result type under `reporting/application`.
* Add a durable delivery entity/outbox linked to the successful report generation job and draft. Enforce one row per job/channel/target.
* Reuse the tenant-bound AES-GCM secret-provider pattern, but use notification-specific types and AAD rather than storing Feishu credentials as model configuration.
* Keep subscription recipient user IDs as execution eligibility and audit metadata. They are not Feishu addressing data in this MVP.
* Use the JDK/Spring HTTP facilities already available through `spring-boot-starter-web`; no Feishu SDK is required.
