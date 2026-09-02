## Why

The board moved to the organization and Priority became a native GitHub issue field. The
delivery tooling still describes the old world, and it no longer works: `start_issue_flow.sh
--priority High` fails with `Option 'High' not found in field 'Priority'`, because it looks
Priority up among project fields and the board's Priority column is fed by the issue, not by
the project.

## What Changes

- **BREAKING** The priority vocabulary is `Urgent/High/Medium/Low`. The retired
  `Blocker/Critical/Major/Minor/Trivial` are rejected with the replacement named.
- Priority is written on the issue with `setIssueFieldValue`, not through the project.
  `updateProjectV2ItemFieldValue` refuses a column backed by an issue field.
- Status, Area and Size stay ordinary project fields, resolved by name.
- The board of record is the organization project `Learning app` (`the-kind-robots`, 11).
  Config, documentation and both guard hooks name it instead of the old personal board.
- The id-resolution layer both scripts carried to work around a `gh` that could not resolve
  fields by name is removed; `gh` 2.98.0 resolves them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repo-delivery-workflow`: filing an issue must set Priority on the issue field rather than
  on the board, using the current vocabulary; and the budget requirement's ban on convenience
  field lookups has to admit name resolution now that the CLI does it.

## Impact

- `.skills/gh-project-workflow/scripts/start_issue_flow.sh`
- `.skills/gh-project-workflow/scripts/finish_issue_flow.sh`
- `.skills/gh-project-workflow/references/config.env.example` (live config, despite the name)
- `.skills/gh-project-workflow/SKILL.md`, `.skills/repo-task-delivery/SKILL.md`
- `AGENTS.md`, `.claude/rules/repo-delivery.md`
- `.claude/hooks/delivery-guard.sh`, `.claude/hooks/coordinator-guard.sh` (refusal text only)

Requires `gh` >= 2.98.0.
