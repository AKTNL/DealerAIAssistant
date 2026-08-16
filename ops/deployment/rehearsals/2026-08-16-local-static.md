# P2-4D Local Rehearsal Record - 2026-08-16

## Scope

Repository-level verification on the Windows development workstation. This record separates executed checks from procedures that require a working Docker daemon and PostgreSQL client tools.

## Executed

| Check | Result | Evidence |
| --- | --- | --- |
| Production configuration validator tests | Passed | 5 focused JUnit tests |
| Full backend regression | Passed | 524 JUnit tests |
| Backend PMD | Passed | `mvn.cmd "-Dfrontend.skip=true" pmd:check` |
| Frontend lint | Passed | ESLint |
| Frontend regression | Passed | 51 files / 265 tests |
| Frontend production build | Passed | Vite production bundle; existing large-chunk warnings only |
| Deployment operations unit tests | Passed | 15 Python tests |
| Python syntax compilation | Passed | `operations.py` and `embedding_stub.py` |
| Production + bootstrap + release-gate Compose merge | Passed | `docker compose ... config -q` |
| YAML parsing | Passed | CI, release gate, and Compose files |
| Performance tool syntax | Passed | `node --check ops/performance/baseline.mjs` |

No evidence output contained passwords, tokens, API keys, prompts, response bodies, or business rows.

## Not Executed Locally

| Procedure | Status | Reason / next execution point |
| --- | --- | --- |
| Build the production image | Not executed | Docker CLI is installed but the local daemon is not running |
| Start PostgreSQL/PGvector and `prod` application | Not executed | Docker daemon unavailable |
| Replace bootstrap password and restart without bootstrap secret | Not executed | Requires the container stack |
| Authenticated production smoke | Not executed | No local production stack |
| Create and inspect a real custom-format backup | Not executed | Host `pg_dump`/`pg_restore` are unavailable |
| Restore into a new empty database | Not executed | Docker daemon and PostgreSQL clients unavailable |
| Application-only rollback rehearsal | Not executed | Requires two immutable application images and a running target environment |
| Measure RPO/RTO | Not executed | A real backup/restore interval was not exercised |
| GitHub Actions semantic lint | Not executed | `actionlint` was not installed and the Go module proxy was unreachable; YAML parsing passed and the workflow run remains authoritative |

The executable environment rehearsal is defined in `.github/workflows/release-gate.yml`. Its first successful run must be linked from the release record before claiming image startup, backup/restore, post-restore smoke, rollback compatibility, or the `RTO <= 4h` target as verified for an environment.

## Follow-up

1. Run the release gate on an isolated GitHub environment and retain its JSON artifacts.
2. Rehearse application-only rollback with two schema-compatible image versions in staging.
3. Repeat the empty-database restore monthly and record start/end timestamps, backup age, image digest, migration version, and smoke result.
