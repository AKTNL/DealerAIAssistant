# P2-4D Deployment And Recovery Approach

## Question

How should this repository turn its existing production profile, Flyway migrations, health probes, tenant isolation, reporting, delivery, cost, and observability capabilities into a repeatable deployment and recovery process without choosing a cloud vendor or splitting the modular monolith?

## Existing Constraints

* The deployable is one Spring Boot application containing the built Vue frontend.
* Production uses PostgreSQL with PGvector and forward-only Flyway migrations. Hibernate validates rather than creates the schema.
* `/livez` and `/readyz` already separate process health from traffic eligibility. Readiness includes database, migration, and knowledge checks.
* Production secrets include database credentials, model configuration encryption, notification encryption, and one-time administrator bootstrap credentials.
* Tenant isolation, reports, subscriptions, deliveries, audit history, and model usage are all PostgreSQL-backed and must be restored as one consistent data set.
* The repository currently has no container image, production compose template, deployment preflight, backup/restore automation, release smoke tool, or full release CI gate.
* The local Docker CLI is installed, but its daemon is not currently running. PostgreSQL client tools are not installed on the host, so a real local restore rehearsal cannot be claimed until the daemon or an external PostgreSQL environment is available.

## External References

* PostgreSQL `pg_dump`: <https://www.postgresql.org/docs/current/app-pgdump.html> - custom archives support flexible, selective, and parallel restore and are internally consistent for one database.
* PostgreSQL `pg_restore`: <https://www.postgresql.org/docs/current/app-pgrestore.html> - `--list` can inspect an archive before restore; restore should target a clean, controlled database and surface SQL errors.
* PostgreSQL continuous archiving: <https://www.postgresql.org/docs/current/continuous-archiving.html> - point-in-time recovery needs base backups plus WAL archiving and is an infrastructure capability beyond a simple repository script.
* Docker Compose startup order: <https://docs.docker.com/compose/how-tos/startup-order/> - dependency startup alone is insufficient; health-based dependency conditions are needed before the application starts.
* Flyway validation: <https://documentation.red-gate.com/flyway/reference/commands/validate> - validation detects checksum, naming, and applied/resolved migration drift before release.

All references were reachable on 2026-08-16.

## Options Considered

### A. Vendor-neutral container and repository-owned release tools (recommended)

Build one non-root application image, provide a production-like Compose topology with PGvector, and add cross-platform release tools that validate configuration, schema, backup evidence, readiness, and authenticated business paths. Keep traffic shifting and secret storage as documented integration contracts for the target environment.

Advantages:

* Reproducible locally and in CI without committing to a cloud provider.
* Matches the current modular monolith and existing probe contracts.
* Release checks and evidence formats remain useful when Compose is later replaced by Kubernetes or a managed platform.

Costs:

* Actual TLS, load-balancer traffic weights, durable object storage, scheduled backup retention, and secret vault integration remain deployment-environment responsibilities.
* A repository rehearsal proves the procedure, not a production infrastructure SLA.

### B. Documentation and shell snippets only

Document environment variables, `pg_dump`, Flyway, smoke, and rollback commands without executable tooling.

Advantages: minimal implementation.

Costs: configuration drift and untested operator steps remain likely; it does not satisfy the requirement that an unprepared operator can deploy and recover without reading source code.

### C. Kubernetes and managed PostgreSQL/PITR deployment

Add Helm/Kubernetes resources and provider-specific backup, secret, and traffic-shifting integrations.

Advantages: can express a stricter RPO/RTO and real canary automation.

Costs: forces infrastructure decisions that the project has explicitly deferred, substantially expands the task, and would be mostly unverifiable in this repository.

## Recommendation

Use option A. Implement repository-owned checks as small, composable commands with machine-readable evidence. Use PostgreSQL custom-format full backups before every release and on a schedule selected by the RPO target. Treat PGvector content, report drafts, subscriptions/jobs/deliveries, authentication/tenant/organization state, audit history, and model usage as one database recovery unit.

Use expand/contract migrations and application-only rollback inside a declared compatibility window. Never run Flyway clean or delete applied migrations. A migration that makes old code incompatible must be declared irreversible for application rollback and requires a forward corrective migration or database restore under an approved incident plan.

The smoke sequence should progress from non-mutating safety checks to authenticated reads and finally explicitly enabled writes:

1. liveness and readiness;
2. login, current identity, and selected tenant;
3. Dashboard and data status;
4. deterministic SSE/knowledge query;
5. report draft creation and report subscription listing;
6. model usage/health visibility when the smoke principal has those permissions.

Secrets must only be passed through environment/secret mounts, never command arguments, committed `.env` files, evidence JSON, or logs. Bootstrap credentials are one-time inputs and must be removed after forced password change.

## Verification Strategy

* Unit-test production configuration failure cases and release-tool parsing/secret redaction.
* Keep the existing fast PR gate: PMD, backend tests, frontend lint/test/build.
* Add a full release gate with PostgreSQL/PGvector, Flyway validation, production-profile startup, readiness, authenticated smoke, backup archive inspection, restore into a clean database, and post-restore smoke.
* Record a repository rehearsal separately from an actual environment rehearsal. Do not mark a real deployment, traffic shift, restore SLA, or secret rotation as completed without environment evidence.

