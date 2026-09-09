## Why

Notes about the app are captured in Google Tasks during the day, from a phone. Nothing
carries them into the repo: they are re-read and re-typed by hand, and some never reach the
board at all. Issue #386.

The gap is a transport, not a decision: the note already exists and the delivery flow already
knows what to do with it. What is missing is a command that reads the list and a rule that
says what becomes an issue, what becomes a comment, and when the source note is closed.

## What Changes

- Add a project skill `.skills/google-tasks/`, shaped like the other repo skills: `SKILL.md`,
  `scripts/`, `references/`, `agents/openai.yaml`.
- `scripts/google_tasks.sh` — bash + curl + jq against the Google Tasks REST API v1, with
  subcommands `doctor`, `auth`, `lists`, `pull`, `done`. No SDK, no new runtime dependency.
- Authentication through `google/oauth2l`, the official Google OAuth CLI, invoked for a
  header per request. Rejected: `BRO3886/gtasks` (addresses tasks by positional index, which
  races any script), GAM (Workspace-only, needs an admin console). No Google Tasks CLI ships
  its own OAuth client id, so a one-time Cloud Console step is unavoidable in every option.
- `references/setup.md` — the one-time manual setup, including the publishing-status trap:
  a consent screen left in *Testing* is issued a refresh token that expires after 7 days.
- Configuration under `${XDG_CONFIG_HOME:-$HOME/.config}/learning-app/google-tasks.env`, with
  env-var overrides. `references/google-tasks.env.example` carries placeholders only.

Not changed: the delivery flow. A pulled note is filed with `start_issue_flow.sh` exactly as
a note typed by hand would be.

## Capabilities

### New Capabilities

None as product behaviour. This change adds agent infrastructure only.

### Modified Capabilities

- `repo-agent-infrastructure`: gains two requirements — a note captured outside the
  repository has a command that pulls it and a rule that turns it into tracked work, and the
  credentials that command needs live outside the repository.

## Impact

- `.skills/google-tasks/` — new skill.
- No source code, no tests, no deployment surface, no committed credential.
- External prerequisite the repository cannot satisfy: an OAuth client of type *Desktop app*
  in a Google Cloud project with the Tasks API enabled. Only the account owner can create it,
  so the first live call is the owner's.
