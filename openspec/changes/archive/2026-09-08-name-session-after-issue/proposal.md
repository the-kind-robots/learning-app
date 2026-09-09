## Why

A Claude Code session that starts an issue through `start_issue_flow.sh` keeps the
auto-generated session name, so the session list shows nothing about which issue it is on.
The script already knows the issue number and title at the moment it finishes; that is the
name the session should carry.

## What Changes

- New script `.skills/gh-project-workflow/scripts/rename_session.sh <number> <title>`
  renames the running Claude Code session to `<number> <title>` over the session messaging
  socket the harness exposes to its child processes.
- `start_issue_flow.sh` calls it at the tail, after the issue number is known, guarded so a
  rename failure never fails the flow.
- Outside a Claude Code session (no socket in the environment, socket gone, no `python3`)
  the rename is a silent no-op.
- `SKILL.md` and `AGENTS.md` mention the rename in one line each.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-delivery-workflow`: the start-work flow gains a requirement that the session it runs
  in is named after the issue it lands on, that this is a no-op outside a Claude session, and
  that it can never fail the flow.

## Impact

- `.skills/gh-project-workflow/scripts/rename_session.sh` — new.
- `.skills/gh-project-workflow/scripts/start_issue_flow.sh` — tail-only call.
- `.skills/gh-project-workflow/SKILL.md`, `AGENTS.md` — one line each.
- `openspec/specs/repo-delivery-workflow/spec.md` — one added requirement.
- The socket protocol is an undocumented Claude Code internal, measured on 2.1.263. A later
  harness may break it; the guard makes that a lost rename, not a lost issue.
