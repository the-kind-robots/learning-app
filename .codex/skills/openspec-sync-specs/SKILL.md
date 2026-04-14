---
name: openspec-sync-specs
description: Sync delta specs from an active OpenSpec change into the main specs without archiving the change. Use this when the user wants the spec updates now but wants to keep the change active.
---

# OpenSpec Sync Specs

Use this skill when the user wants to update `openspec/specs/` before archiving.

## Workflow

1. Resolve the target change.

2. Find delta specs under:

`openspec/changes/<name>/specs/*/spec.md`

3. For each delta spec:
   - read the delta spec
   - read the corresponding main spec under `openspec/specs/<capability>/spec.md`
   - merge changes intelligently instead of copying wholesale

4. Handle sections intentionally:
   - `ADDED Requirements`: add missing requirements
   - `MODIFIED Requirements`: patch only the changed portions and preserve untouched scenarios
   - `REMOVED Requirements`: remove the requirement block
   - `RENAMED Requirements`: rename in place

5. If the main spec does not exist:
   - create it with a short purpose section
   - add the new requirements from the delta

6. Summarize which capabilities changed and how.

## Notes

- This skill is mainly for cases where the user wants specs synced before archive.
- If the user is ready to close the change, `openspec-archive-change` is usually the better path.

## Guardrails

- Preserve existing content not mentioned by the delta.
- Keep the merge idempotent.
- Ask before making destructive removals when the delta is ambiguous.
