## Why

`.claude/hooks/delivery-guard.sh` refuses `gh issue create --help`. Reading a command's
documentation is not running it: `--help` prints text and creates no issue, no pull request
and no branch, so the guard is blocking the one way an agent can find out what the command
it is being steered towards actually accepts. Measured 2026-09-01.

## What Changes

- The guard skips any statement carrying a standalone `--help` or `-h`, before the `git`
  and `gh` dispatch, so the `issue create` deny, the `pr create` deny and the
  `checkout -b` / `switch -c` ask are all covered by one test rather than three patches.
- Nothing else in the hook moves: no new rules, no reworded refusals, no restructuring.
  In particular no `--dry-run` handling is added — `gh issue create` has no such flag.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-delivery-workflow`: the requirement that names which commands are refused gains the
  boundary it was missing — the guard judges commands that create work, and a
  documentation-only invocation creates none, so it is not refused.

## Impact

- `.claude/hooks/delivery-guard.sh` — one shared skip, before the dispatch.
- `openspec/specs/repo-delivery-workflow/spec.md` — one modified requirement.
- No application code. `.claude/hooks/coordinator-guard.sh` is out of scope.
