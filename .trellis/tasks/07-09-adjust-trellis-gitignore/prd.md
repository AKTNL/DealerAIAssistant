# Adjust Trellis Git Ignore Strategy

## Goal

Stop ignoring the entire `.trellis/` directory so Trellis workflow, tasks, specs, scripts, and workspace memory can be versioned with the repository, while keeping generated runtime/management files out of normal commits.

## Requirements

* Remove the broad `.trellis/` ignore rule from `.gitignore`.
* Add narrower ignore rules for Trellis runtime/management data that should remain local:
  * backup directories under `.trellis/.backup-*`
  * Trellis worktrees under `.trellis/worktrees/`
  * template hash state under `.trellis/.template-hashes.json`
  * session runtime state under `.trellis/.runtime/`
  * caches under `.trellis/.cache/`
* Preserve existing ignores for local AI tool directories such as `.agent/`, `.agents/`, `.claude/`, and `.codex/`.
* Do not modify Trellis scripts, specs, or workflow files in this task.

## Acceptance Criteria

* [x] `.trellis/workflow.md`, `.trellis/config.yaml`, `.trellis/scripts/**`, `.trellis/spec/**`, `.trellis/tasks/**`, and `.trellis/workspace/**` are no longer ignored by git.
* [x] `.trellis/.runtime/**`, `.trellis/.cache/**`, `.trellis/worktrees/**`, `.trellis/.backup-*`, and `.trellis/.template-hashes.json` remain ignored by git.
* [x] `git status --short` shows only intentional changes.

## Definition of Done

* Ignore behavior is verified with `git check-ignore`.
* The change is reviewed for unintended broad unignore effects.
* The task is committed if the resulting diff is correct.

## Technical Approach

Replace the single broad `.trellis/` rule with the specific generated/runtime subpaths recommended by the local Trellis safe-commit helper. This keeps Trellis persistence files versionable and leaves ephemeral management state local.

## Decision

**Context**: The repository currently ignores `.trellis/` as a whole, which prevents shared Trellis specs and workflow scripts from being committed.

**Decision**: Do not ignore `.trellis/` globally. Ignore only generated/runtime Trellis subpaths: `.trellis/.backup-*`, `.trellis/worktrees/`, `.trellis/.template-hashes.json`, `.trellis/.runtime/`, and `.trellis/.cache/`.

**Consequences**: Future `git status` output will surface changes to Trellis specs/scripts/configs, task records, and workspace memory. Generated runtime and management state remains local unless intentionally force-added.

## Out of Scope

* Changing Trellis workflow behavior.
* Changing task or workspace file formats.
* Reworking other AI-tool ignore rules.

## Technical Notes

* Current broad rule: `.gitignore` contains `.trellis/`.
* Verification targets:
  * `.trellis/workflow.md`
  * `.trellis/spec/guides/index.md`
  * `.trellis/scripts/task.py`
  * `.trellis/tasks/07-09-adjust-trellis-gitignore/prd.md`
  * `.trellis/workspace/kevin/journal-1.md`
  * `.trellis/.runtime/sessions`
  * `.trellis/.template-hashes.json`
