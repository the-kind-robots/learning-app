---
name: openspec-apply-change
description: Implement tasks from an existing OpenSpec change. Use this when the user wants to start implementation, continue implementation, or work through the remaining tasks of a change after the repo workflow has already opened tracking for it.
---

# OpenSpec Apply Change

Use this skill when the user wants to implement an existing OpenSpec change.

## Workflow

1. Resolve the target change.
   - If the user names it, use that change.
   - Otherwise inspect active changes and choose the only obvious candidate.
   - If multiple candidates are plausible, ask the user to pick one.

2. Inspect change status:

```bash
openspec status --change "<name>" --json
```

3. Load implementation instructions:

```bash
openspec instructions apply --change "<name>" --json
```

4. Handle instruction states:
   - `blocked`: explain what artifact is missing and suggest `openspec-propose-change`
   - `all_done`: summarize that implementation is complete and suggest archive
   - otherwise: continue

5. Read all files listed in `contextFiles`.

6. Implement pending tasks.
   - Keep changes minimal and scoped.
   - Mark completed tasks in the tasks artifact immediately.
   - Continue until done, blocked, or the user interrupts.

7. If implementation reveals a spec or design mismatch:
   - pause
   - explain the mismatch
   - suggest updating the change artifacts before continuing

## Output

Always report:

- which change is being used
- current progress
- tasks completed in this session
- whether the next step is more implementation, verify, or archive

## Guardrails

- Always read `contextFiles` before changing code.
- Do not guess through ambiguous tasks.
- If the change has no tasks yet, do not improvise implementation.
- Prefer finishing one coherent chunk of work before stopping.
