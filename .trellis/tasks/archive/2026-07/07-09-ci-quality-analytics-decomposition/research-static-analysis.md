# Static Analysis Research

## Sources

* ESLint flat config official docs: https://eslint.org/docs/latest/use/configure/configuration-files
* eslint-plugin-vue user guide: https://eslint.vuejs.org/user-guide/
* SpotBugs Maven plugin usage: https://spotbugs.github.io/spotbugs-maven-plugin/usage.html
* SpotBugs Maven plugin check goal: https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html
* Maven Checkstyle plugin introduction: https://maven.apache.org/plugins/maven-checkstyle-plugin/
* Maven PMD plugin introduction: https://maven.apache.org/plugins/maven-pmd-plugin/
* Maven PMD plugin check goal: https://maven.apache.org/plugins/maven-pmd-plugin/check-mojo.html

## Findings

* ESLint now uses flat config as the modern default configuration style. This fits a new JavaScript/Vue lint setup because the frontend currently has no legacy ESLint config to migrate.
* eslint-plugin-vue provides flat-config bundle configurations for `eslint.config.js`, including Vue 3 recommended rule sets.
* SpotBugs Maven plugin provides a `spotbugs:check` goal that can fail the Maven build when findings are reported. Its check goal binds by default to Maven `verify` and is thread-safe.
* Maven Checkstyle plugin provides `checkstyle:check`, which can fail the build on style violations. It is useful for style consistency, but more likely to create broad style-rule churn in an existing project with no prior Checkstyle rules.
* Maven PMD plugin provides `pmd:check`, which fails the build when PMD violations are present. The official plugin docs also expose CPD checks for copy/paste detection, but CPD is a stronger duplicate-code gate and should be introduced deliberately.

## Mapping To This Repo

* Frontend is JavaScript + Vue SFCs, so ESLint with `@eslint/js`, `eslint-plugin-vue`, and browser/test globals is the natural first lint gate.
* Backend already has strong JUnit regression coverage and uses Spring Boot Java 21. SpotBugs is a better first static-analysis gate than Checkstyle because it focuses on bug patterns rather than formatting conventions.
* The user chose the Checkstyle/PMD family instead of SpotBugs. Within that family, PMD is the lower-noise first choice because it is rule/quality oriented; Checkstyle is more style-standard oriented and needs a project-owned rule file to avoid large style churn.
* Checkstyle can still be useful if introduced with a narrow project-owned rule file instead of a full third-party style preset.

## Recommended Tool Choice

* Add frontend `npm run lint` using ESLint flat config.
* Add backend PMD first, preferably as a Maven `verify`/CI check, with a deliberately small ruleset.
* Add Checkstyle only if the task scope explicitly chooses style enforcement too; start with a narrow config if selected.
* Avoid broad formatting tools in this task.
