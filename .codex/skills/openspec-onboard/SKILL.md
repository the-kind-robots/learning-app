---
name: openspec-onboard
description: Guided onboarding for this repo's OpenSpec workflow. Use this when the user wants to learn the local change workflow end to end on a small real task.
---

# OpenSpec Onboard

Use this skill to teach the repo's OpenSpec workflow through one small real example.

## Flow to teach

`openspec-propose-change -> openspec-apply-change -> openspec-archive-change`

Supporting skills:

- `openspec-verify-change`
- `openspec-sync-specs`

## Workflow

1. Confirm OpenSpec is initialized:

```bash
openspec status --json
```

2. Suggest a few small, real tasks from the codebase.
   - Prefer tight-scoped fixes or polish work.
   - Avoid large features for onboarding.

3. Walk the user through:
   - choosing a small task
   - creating a change
   - drafting the first artifact
   - implementing tasks
   - verifying and archiving

4. Narrate why each step exists, but keep momentum and do real repo work.

## Guardrails

- Keep the example small enough to finish in one session when possible.
- Use current repo conventions, not abstract theory.
- If the selected task grows too large, recommend slicing it down.
