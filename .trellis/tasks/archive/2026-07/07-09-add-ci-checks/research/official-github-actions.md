# Official GitHub Actions References

## Question

Which official GitHub Actions should the CI workflow use for Java, Node.js, checkout, and dependency caching?

## Sources

* `actions/setup-java`: https://github.com/actions/setup-java
* `actions/setup-node`: https://github.com/actions/setup-node
* `actions/checkout`: https://github.com/actions/checkout

## Findings

* `actions/setup-java` supports installing a requested Java version and caching dependencies managed by Maven.
* `actions/setup-node` supports choosing a Node.js version, npm caching, and `cache-dependency-path` for lockfiles outside the repository root.
* `actions/checkout` v7 is the current documented checkout usage for standard repository checkout on GitHub-hosted runners.

## Applied To This Repo

* Backend job uses `actions/setup-java@v5` with Temurin Java 21 and Maven cache.
* Frontend job uses `actions/setup-node@v6` with Node.js 24 and an npm cache keyed by `frontend/package-lock.json`.
* Both jobs use `actions/checkout@v7` before running commands.
