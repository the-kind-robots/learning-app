---
name: openspec-archive-change
description: Archive a completed OpenSpec change using the current OpenSpec CLI. Use this when the user wants to finalize work, update main specs as part of archiving, and close out a change cleanly.
---

# OpenSpec Archive Change

Use this skill when the user wants to archive a completed change.

## Workflow

1. Resolve the target change.
   - Prefer the user-provided name.
   - Otherwise inspect active changes and ask only if more than one candidate remains.

2. Inspect change completion:

```bash
openspec status --change "<name>" --json
```

3. Read the tasks artifact if it exists and note incomplete checkboxes.

4. Warn about incomplete artifacts or tasks before archiving.
   - If the user still wants to proceed, continue.

5. Archive with the built-in CLI:

```bash
openspec archive "<name>"
```

Useful variants:

- `openspec archive "<name>" --skip-specs` for tooling, infra, or doc-only changes
- `openspec archive "<name>" -y` when the user already explicitly confirmed archiving

6. Summarize:
   - archive result
   - whether specs were updated or skipped
   - whether any warnings were overridden

## Notes

- Prefer the built-in `openspec archive` command over manual file moves.
- If the user wants delta specs synced without archiving, use `openspec-sync-specs` instead.
