# Deployment and Operations

> Executable contracts for production configuration, container deployment, release evidence, and full-database recovery.

## Scenario: Production Deployment And Full-Database Recovery

### 1. Scope / Trigger

- Trigger: any change to `prod` configuration, Docker/Compose assets, deployment tooling, backup/restore behavior, readiness-driven traffic, release CI, or operational evidence.
- This is an infrastructure, database, security, and cross-layer contract. Spring configuration, Flyway, PostgreSQL/PGvector, authentication, tenant state, business smoke, secret mounts, and CI must fail or succeed as one release path.

### 2. Signatures

- Production profile: `SPRING_PROFILES_ACTIVE=prod`.
- Production guard: `config.ProductionConfigurationValidator`, active only under `prod`.
- Image: repository-root `Dockerfile`; runtime port `8081`; runtime UID/GID `10001:10001`.
- Compose:
  - `ops/deployment/compose.production.yml`
  - `ops/deployment/compose.bootstrap.yml`, used only for the first empty-user-database startup
  - `ops/deployment/compose.release-gate.yml`, used only by isolated CI
- Operations CLI: `python ops/deployment/operations.py <command>`:
  - `preflight --expected-migration <version> [--archive <dump> --manifest <json>] --evidence <new-json>`
  - `backup --output <new-dump> --application-version <safe-id> --evidence <new-json>`
  - `restore --archive <dump> --manifest <json> --target-database <name> --confirm-target <same-name> --expected-migration <version> --evidence <new-json>`
  - `smoke --base-url <https-origin> [--tenant-key <key>] [--allow-writes] --evidence <new-json>`
  - `bootstrap-password --base-url <https-origin> --evidence <new-json>`
- Database environment: `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE`, plus `PGPASSWORD` or `PGPASSFILE`.
- Credential environment: `SMOKE_USERNAME`, `SMOKE_PASSWORD`, `BOOTSTRAP_USERNAME`, `BOOTSTRAP_CURRENT_PASSWORD`, and `BOOTSTRAP_NEW_PASSWORD`.
- Traffic probes: `/livez` for process restart decisions; `/readyz` for traffic eligibility.
- Recovery targets: daily full plus pre-release backup, `RPO <= 24h`, `RTO <= 4h`, monthly empty-database restore rehearsal.

### 3. Contracts

- Production startup fails closed unless it uses PostgreSQL, Flyway validate-on-migrate with clean disabled, Hibernate `validate`, secure refresh cookies, required empty-database bootstrap policy, disabled sample fallback, PGvector, OpenAI embeddings, 1536 dimensions matching V2, two independent 32-byte Base64 AES keys, HTTPS non-local CORS origins, and enabled report jobs.
- Validation errors name unsafe property keys only. They never include configured passwords, tokens, API keys, AES keys, or partial secret values.
- The image builds the Vue bundle and Spring Boot jar in separate Node/Maven stages. The final image contains only the JRE/application artifacts and runs as non-root. Compose adds a read-only application filesystem, writable `/tmp`, dropped capabilities, health-based dependency order, and loopback-only host exposure.
- Production secrets enter through config-tree files. Real secrets and production `.env` files are ignored and never copied into the image. The bootstrap secret is removed after forced password replacement and the application is recreated without the bootstrap overlay.
- `preflight`, backup manifests, restore evidence, and smoke evidence may contain safe tool versions, migration/application versions, SHA-256, durations, resource IDs, and aggregate table counts. They must not contain connection strings, credentials, request/response bodies, prompts, model output, or business rows.
- A backup uses PostgreSQL custom format, `--no-owner`, and `--no-privileges`, then must pass `pg_restore --list`. Its manifest is exclusive-create, records the archive filename/size/SHA-256, and captures counts for every Flyway-managed application table.
- Application and scheduler writes are quiesced before a backup whose manifest will be used for exact restore-count verification. Without quiescence or a shared exported snapshot, pre-dump counts can race the dump snapshot and are not valid recovery evidence.
- Restore requires a new, explicitly confirmed database with zero non-system tables. It verifies archive listability, manifest schema/name/size/hash, runs `pg_restore --exit-on-error --single-transaction`, validates migration/tenant/active-batch state, and compares every restored application-table count with the manifest.
- The database is one recovery unit: auth, tenant/organization, all active/inactive import batches and business rows, PGvector knowledge, reports/subscriptions/jobs/deliveries/collaboration, audit, model config, and model usage/cost tables are restored together.
- Smoke first verifies liveness/readiness, then login, exact selected tenant, identity, Dashboard, data status, organization, report draft/subscription/job/delivery reads, model usage, and an SSE knowledge request. Writes are opt-in and limited to a report draft in an isolated acceptance tenant.
- Release order is backup -> forward Flyway migration -> no-traffic new image -> readiness/authenticated smoke -> progressive traffic. A schema-compatible rollback changes only the application image and retains the new schema. Never run Flyway clean/down or modify an applied migration.
- CI uses `embedding_stub.py` only as a deterministic OpenAI-compatible 1536-dimension provider. It is not a production embedding service and must never be exposed as one.
- `APP_MODEL_SECRET_KEY` and `APP_NOTIFICATION_SECRET_KEY` cannot be replaced in place while version-1 ciphertext exists. Key rotation first requires a forward release with old/new-key dual read and verified re-encryption; direct replacement makes stored tenant credentials unreadable.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Unsafe/missing production property | Application startup fails before readiness; message contains property names only |
| Embedding dimensions differ from migrated `VECTOR(1536)` | Production validation fails; add a forward migration and reindex before changing dimensions |
| Required PostgreSQL command or non-interactive credential missing | Operations command exits non-zero and writes failed evidence |
| Missing table, failed migration, no enabled tenant/membership, no active batch, or multiple active batches per tenant | Preflight/restore exits non-zero |
| Archive missing/empty/unlistable, or manifest name/size/hash mismatch | Backup inspection/restore exits non-zero before database mutation |
| Restore confirmation differs from target, or target has a user table | Restore exits non-zero before `pg_restore` |
| Any restored application-table count differs from the quiesced backup manifest | Restore exits non-zero; restored database does not receive traffic |
| HTTP base URL is plaintext and not explicit loopback | Smoke/bootstrap-password exits non-zero |
| Login token missing, forced password still active, selected tenant differs, endpoint envelope fails, or SSE lacks `done` | Smoke exits non-zero without recording response bodies |
| Docker daemon/PostgreSQL clients unavailable locally | Static/tests may pass, but image/startup/backup/restore must be recorded as not executed |
| Old image is incompatible with the migrated schema | Application rollback is forbidden; use a forward fix or approved new-database restore |

### 5. Good/Base/Bad Cases

- Good: the release gate builds one immutable image, starts PGvector and the `prod` app with one-time bootstrap, replaces the password, recreates the app without bootstrap credentials, smokes it, quiesces writes, backs up, restores into an empty database, starts the same image against restored data, and reruns smoke.
- Good: a tampered dump or a restore missing one report/model-usage row fails checksum/count validation and never becomes ready for traffic.
- Base: a developer without Docker can run PMD, full backend/frontend tests, Python operations tests, and Compose `config -q`; the rehearsal record explicitly leaves container and restore checks unexecuted.
- Bad: run `pg_restore --clean` against the current production database, edit V12 after release, or call a documentation-only backup successful without listing/restoring it.
- Bad: leave the application writing while capturing pre-dump counts, then treat a legitimate snapshot count difference as data corruption.
- Bad: upload the `.dump` database archive to a general CI artifact store or include login/HTTP bodies in JSON evidence.

### 6. Tests Required

- `ProductionConfigurationValidatorTest`: valid baseline; aggregate unsafe-property failure without secret echo; HTTPS/non-local CORS; independent AES keys; exact migrated embedding dimension.
- `AgentPocApplicationStartupTest`: default external-service-free startup; `prod` datasource/Flyway/embedding settings; V1-V12 and PostgreSQL PGvector schema contracts.
- `ops/deployment/tests/test_operations.py`: missing credential, invalid version/database name, missing tables, active-batch requirement, manifest tamper, exact restore confirmation, empty-target guard, restore-count mismatch, safe evidence, tenant-selected authenticated smoke, and write opt-in.
- `ops/deployment/tests/test_embedding_stub.py`: one deterministic 1536-value vector per input and invalid-input rejection without input echo.
- Fast CI: backend PMD/full tests, frontend lint/test/build, Python deployment tests, and merged Compose `config -q`.
- Release gate: tenant isolation regressions, production image build/start, bootstrap removal, authenticated pre-backup smoke, quiesced custom backup/list/hash, empty-database restore/count validation, restored production startup, and post-restore smoke.
- Environment rehearsal evidence must state whether image build, real PostgreSQL backup/restore, application rollback, and RPO/RTO measurement actually ran.

### 7. Wrong vs Correct

Wrong:

```bash
# Destructive and not snapshot-safe.
pg_dump agentpoc > backup.sql
pg_restore --clean --dbname agentpoc backup.dump
```

Correct:

```bash
# Drain traffic and quiesce application/runner writes first.
python ops/deployment/operations.py backup \
  --output backups/release.dump \
  --application-version 2026.08.16-1 \
  --evidence artifacts/backup.json

python ops/deployment/operations.py restore \
  --archive backups/release.dump \
  --manifest backups/release.dump.manifest.json \
  --target-database agentpoc_restore_20260816 \
  --confirm-target agentpoc_restore_20260816 \
  --expected-migration 12 \
  --evidence artifacts/restore.json
```

Wrong:

```yaml
environment:
  APP_DB_PASSWORD: production-password
ports:
  - "5432:5432"
```

Correct:

```yaml
environment:
  SPRING_CONFIG_IMPORT: configtree:/run/secrets/
secrets:
  - source: database_password
    target: spring.datasource.password
ports:
  - "127.0.0.1:8081:8081"
```
