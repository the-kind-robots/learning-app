---
name: openspec-verify-change
description: Verify that implementation matches an OpenSpec change before archiving. Use this when the user wants a completion check across tasks, specs, design, and code before calling the change done.
---

# OpenSpec Verify Change

Use this skill for a verification pass before archive.

## Workflow

1. Resolve the target change.

2. Run OpenSpec validation first:

```bash
openspec validate "<name>" --type change --json
```

3. Inspect workflow state:

```bash
openspec status --change "<name>" --json
openspec instructions apply --change "<name>" --json
```

4. Read all available `contextFiles`.

5. Verify three dimensions:
   - completeness: incomplete tasks, missing artifacts, obviously unimplemented requirements
   - correctness: implementation or tests that appear to diverge from the change
   - coherence: design mismatches or project-pattern regressions

6. Report findings first, ordered by severity.

## Output

Use a review-style result:

- `CRITICAL`: must fix before archive
- `WARNING`: likely gaps or divergences
- `SUGGESTION`: smaller improvements

If there are no findings, say so explicitly and mention any residual uncertainty.

## Guardrails

- Prefer concrete file references.
- Treat OpenSpec validation output as a baseline, not the whole answer.
- When uncertain, downgrade severity instead of overstating.
