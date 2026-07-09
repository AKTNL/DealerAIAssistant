# Update README And Start Local Project

## Goal

Update the repository README so local startup and the newly added quality gates are easy to find, then start the project locally for the user.

## What I Already Know

* The project is a Spring Boot backend plus Vue/Vite frontend.
* Backend runs on `http://localhost:8081`.
* Frontend dev server runs on `http://localhost:5173` and proxies `/api` to the backend.
* Backend startup requires `APP_ACCESS_KEY`, `APP_SESSION_SECRET`, and `APP_API_KEY`.
* Frontend now has `npm run lint`.
* Backend now has PMD via `mvn "-Dfrontend.skip=true" pmd:check`.

## Requirements

* Update README with the current local development commands.
* Include Windows PowerShell-friendly environment variable examples.
* Document frontend lint and backend PMD quality gates.
* Start backend and frontend locally.
* Report the URLs and login key to the user.

## Acceptance Criteria

* [x] README includes current quality commands.
* [x] README includes PowerShell startup commands.
* [x] Backend starts successfully on port 8081.
* [x] Frontend starts successfully on port 5173.
* [x] Working tree status is reported after the README change.

## Definition Of Done

* README updated without broad unrelated rewrites.
* Local services are running or any startup blocker is clearly reported.
* User receives the local URL and credentials used for the demo session.

## Out Of Scope

* Changing application runtime behavior.
* Changing auth or model configuration defaults.
* Adding new tests for documentation-only changes.

## Technical Notes

* Relevant files: `README.md`, `backend/src/main/resources/application.yml`, `frontend/package.json`, `frontend/vite.config.js`.
