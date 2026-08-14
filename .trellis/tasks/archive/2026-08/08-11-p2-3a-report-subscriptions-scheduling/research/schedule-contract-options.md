# P2-3A schedule contract options

## Question

Should the first subscription release accept arbitrary cron expressions, or expose a controlled calendar schedule?

## Comparable patterns

### Microsoft Graph recurring events

Microsoft Graph separates a structured recurrence `pattern` (daily, weekly, absolute monthly, and related variants) from its `range`, and applies a recurrence time zone. Users edit calendar concepts rather than raw scheduler syntax.

Source: <https://learn.microsoft.com/en-us/graph/outlook-schedule-recurring-events>

### Amazon EventBridge Scheduler

EventBridge Scheduler supports distinct one-time, rate, and cron schedule types. Cron schedules can carry an IANA time zone, but exposing them transfers expression validation, minimum-frequency enforcement, and calendar edge cases to the product surface.

Source: <https://docs.aws.amazon.com/scheduler/latest/UserGuide/schedule-types.html>

### RFC 5545 recurrence rules

`RRULE` is a portable calendar recurrence model with broad expressiveness. It is useful for calendar interoperability, but its combinations are larger than this MVP needs and would substantially expand validation and deterministic test coverage.

Source: <https://datatracker.ietf.org/doc/html/rfc5545>

## Repository constraints

* The backend is a Java 21 / Spring Boot 3.4 modular monolith and currently has no Quartz or other cron dependency.
* `ReportType` already exposes daily, weekly, monthly, and topic report types. A matching controlled recurrence keeps the first UI and API vocabulary small.
* `ReportService` is the existing generation fact source and already applies tenant plus organization scope when listing and generating drafts.
* `AuthPrincipal`, `OrganizationAuthorizationService`, and `AuthAuditService` already provide the selected tenant, live organization scope resolution, and tenant-scoped audit primitives that subscriptions should reuse.
* P2-3A defines schedules only. Leasing, retry, catch-up execution, and delivery belong to P2-3B/P2-3C, so the persisted contract should not depend on a scheduler engine yet.

## Feasible approaches

### A. Controlled calendar presets (recommended)

Persist an explicit schedule kind plus calendar fields: local time and IANA zone for daily; weekday for weekly; day-of-month for monthly. Calculate `nextRunAt` with `java.time` and keep the domain independent of a job engine.

Pros:

* Small, auditable validation surface and a simple first UI.
* DST behavior can be explicitly specified and deterministically tested.
* No new scheduling dependency before P2-3B.
* Naturally enforces a minimum interval of one day.

Cons:

* Cannot express multiple runs per day or complex business calendars.
* A later cron/RRULE option requires an additive schedule kind.

### B. Restricted cron

Accept a limited cron grammar, require an IANA time zone, reject sub-daily schedules, and normalize expressions before duplicate detection.

Pros:

* More flexible without committing to full Quartz execution.
* Familiar to technical administrators.

Cons:

* Validation, canonicalization, DST semantics, and UI guidance are materially more complex.
* Product users must understand scheduler syntax.
* It creates an engine-shaped contract before P2-3B chooses execution infrastructure.

### C. RFC 5545 RRULE

Persist an RRULE plus time-zone metadata.

Pros:

* Strong calendar interoperability and future expressiveness.

Cons:

* Too broad for the current daily/weekly/monthly requirement.
* Highest validation and test cost.

## Recommendation

Choose approach A for P2-3A. Model the schedule as an extensible discriminator so a restricted cron or RRULE variant can be added later without changing existing subscription records.

