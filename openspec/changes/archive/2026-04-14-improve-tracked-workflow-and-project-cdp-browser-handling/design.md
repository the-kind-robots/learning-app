## Context

This repository uses repo-local Codex skills to drive issue tracking, OpenSpec changes, implementation, and delivery. It also uses a project-specific Chrome CDP wrapper because the learning app runs as a PWA with a service worker and local state. Generic browser tooling is often slower or less reliable for this app's debugging needs.

The current pain points are not product features but workflow mismatches:

- orchestration can still be bypassed for small-seeming issues
- GitHub Project wrappers fail hard on optional field assumptions
- browser debugging may start with the wrong tool
- CDP sessions can observe stale service-worker-controlled builds

## Goals / Non-Goals

**Goals:**
- Make tracked delivery the default for any likely repo-bound change.
- Keep GitHub Project start-work reliable even when optional fields are missing.
- Prefer the project-specific CDP browser workflow for this repository.
- Provide an explicit service-worker refresh/update workflow for local Chrome debugging.

**Non-Goals:**
- Replacing GitHub Project automation entirely.
- Replacing OpenSpec with a different planning system.
- Building a generic browser abstraction for all repositories.

## Decisions

### 1) Trigger tracked delivery on likely repository changes, not only explicit bugs/features
- Expand orchestration skill wording from “new bug/feature” to “any problem or proposed repo change likely to become a commit”.
- Add explicit anti-patterns to discourage immediate coding before issue + OpenSpec exist.
- After merge/close, reset the task boundary so the next non-trivial repo scope is treated as a new tracked task by default unless it is clearly only closeout tail work.

### 2) Treat optional GitHub Project fields as optional
- Keep `Status` assignment strict when requested.
- If `Category` or another requested optional single-select field is absent on the project, skip it with a warning instead of failing the workflow.
- Preserve the ability to override field names explicitly.

### 3) Prefer project CDP over generic browser flows for this app
- Update the project CDP skill so browser debugging against `sprecha.local` / `sprecha.de` is described as the default path for this repo.
- Make UI metadata eligible for implicit invocation so Codex can pick it up naturally.
- Document that generic browser tools are fallback-only here.

### 4) Build service-worker-aware refresh helpers into the project CDP wrapper
- Add wrapper commands that enable Chrome's service-worker “update on reload” behavior through CDP before reloading the page.
- Keep the helper project-specific so it encodes `sprecha.local` / `sprecha.de` assumptions locally, not in the global low-level CDP skill.
- Prefer “refresh local with SW update” as the default local debugging refresh path.

## Risks / Trade-offs

- **Risk:** Skipping a missing project field could hide a configuration mistake.  
  **Mitigation:** Emit a clear warning naming the missing field and continue only for optional fields.

- **Risk:** Over-prioritizing CDP could make simple browser checks feel heavier.  
  **Mitigation:** Keep generic browser tooling as fallback, but state clearly that CDP is preferred for this repo.

- **Risk:** Forcing service-worker update on reload can change behavior compared with a normal user refresh.  
  **Mitigation:** Scope it to explicit debugging commands and document the difference.

## Migration Plan

1. Update the orchestration skill wording and metadata.
2. Harden the GitHub Project start-work wrapper to skip absent optional fields.
3. Update the project CDP skill metadata/instructions to become the preferred browser path.
4. Add service-worker-aware refresh/update commands to the project CDP wrapper.
5. Validate skill loading, issue creation on the current project, and CDP helper command parsing.

Rollback: revert skill/rule/script changes together; no product data migration is involved.

## Open Questions

- Should the GitHub wrapper treat every missing field as optional, or only specifically configured optional fields such as `Category`?
