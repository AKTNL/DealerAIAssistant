# Add CI Checks

## Goal

Add a minimal GitHub Actions CI workflow so backend and frontend regressions are caught automatically on pushes to `main` and pull requests.

## What I Already Know

* The repository currently has no `.github/workflows/` directory.
* Backend is a Spring Boot Maven project under `backend/`.
* Backend requires Java 21.
* Existing backend verification command is `mvn -Dfrontend.skip=true test` from `backend/`.
* Frontend is a Vite/Vue/Vitest project under `frontend/`.
* Frontend uses `package-lock.json`, so CI should install dependencies with `npm ci`.
* README lists Node.js 24+ as the frontend environment requirement.

## Requirements

* Add a GitHub Actions workflow at `.github/workflows/ci.yml`.
* Trigger CI on pushes to `main` and on pull requests.
* Run a backend job on Ubuntu with Java 21 using the Maven test command:
  * `mvn -B -Dfrontend.skip=true test`
* Run a frontend job on Ubuntu with Node.js 24 using:
  * `npm ci --no-audit --no-fund`
  * `npm test`
  * `npm run build`
* Use dependency caching where supported by official setup actions.
* Keep workflow permissions minimal.
* Do not introduce deployment, publishing, secrets, or release automation in this task.

## Acceptance Criteria

* [x] `.github/workflows/ci.yml` exists and is valid YAML.
* [x] Backend CI config uses Java 21 and runs Maven tests from `backend/`.
* [x] Frontend CI config uses Node.js 24, `frontend/package-lock.json`, Vitest, and Vite build.
* [x] Workflow is scoped to push to `main` and pull requests.
* [x] Local verification commands pass or any environment-specific limitation is documented.

## Definition of Done

* CI workflow committed.
* Local backend tests pass with `mvn "-Dfrontend.skip=true" test`.
* Local frontend tests/build pass, or any failure is diagnosed and fixed if caused by this task.
* Task is archived and session journal is recorded.

## Technical Approach

Create one workflow with two independent jobs:

* `backend`: `actions/checkout`, `actions/setup-java` with Temurin Java 21 and Maven cache, then Maven tests from `backend/`.
* `frontend`: `actions/checkout`, `actions/setup-node` with Node 24 and npm cache keyed by `frontend/package-lock.json`, then install, test, and build from `frontend/`.

## Decision

**Context**: The project has separate backend and frontend toolchains. Maven can build the frontend during package/install, but regular backend regression already uses `frontend.skip=true` for speed and isolation.

**Decision**: Keep CI split into backend and frontend jobs instead of making Maven drive the whole stack.

**Consequences**: CI failures identify the affected layer more clearly. The backend job remains fast and deterministic; the frontend job still validates the static build separately.

## Out of Scope

* Deployment or artifact upload.
* Code coverage reports.
* Browser/e2e tests.
* Branch protection setup in GitHub UI.
* Automatic publishing or releases.

## Research References

* [`research/official-github-actions.md`](research/official-github-actions.md) - official setup/check-out action capabilities and selected versions.

## Technical Notes

* Official action references checked:
  * `actions/setup-java` supports setting up requested Java versions and Maven dependency caching.
  * `actions/setup-node` supports Node version selection and npm dependency caching.
* Existing commands from README:
  * `cd backend && mvn -Dfrontend.skip=true test`
  * `cd frontend && npm run test && npm run build`
