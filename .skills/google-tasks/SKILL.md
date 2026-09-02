---
name: google-tasks
description: "Pull notes captured in Google Tasks from a phone into this repository's delivery flow, then close them at the source. Use when the user says a note, idea or bug is in Google Tasks, asks what is in the task inbox, asks to turn captured notes into issues, or asks to mark a pulled note done. Commands: doctor, auth, lists, pull, done. Credentials live outside the repository; run doctor before the first use of a session."
---

# Google Tasks

Notes about the app are captured in Google Tasks during the day, from a phone. This skill is
the transport that brings them into the repository: read the open notes, decide what each one
is, file it, and close it at the source so it is never read twice.

The skill does not decide anything the repository has not already decided. A pulled note is
judged and filed exactly like a note typed by hand.

## When to use

- The user says a note, an idea, or a bug "is in Google Tasks" or "is in my tasks".
- The user asks what is waiting in the task inbox, or asks to go through it.
- A note has become an issue or a comment and must be closed at the source.

Do not use it to write notes. This is a one-way inbox: read, then complete.

## Setup

One-time, and only the account owner can do it — it needs a Google Cloud OAuth client:
`references/setup.md`.

Credentials and account settings live outside the repository, under
`${XDG_CONFIG_HOME:-$HOME/.config}/learning-app/`. Nothing secret is ever committed, and
nothing secret is ever printed: `doctor` reports that a token works, never what it is.

Run this once per session before anything else:

```bash
bash .skills/google-tasks/scripts/google_tasks.sh doctor
```

It prints one line per check and exits non-zero when something is missing, naming what and
where. If it says there is no usable token, run `auth` and paste the printed URL into a
browser — auto-opening one does not work under WSL2.

## Commands

```bash
bash .skills/google-tasks/scripts/google_tasks.sh doctor
bash .skills/google-tasks/scripts/google_tasks.sh auth
bash .skills/google-tasks/scripts/google_tasks.sh lists
bash .skills/google-tasks/scripts/google_tasks.sh pull
bash .skills/google-tasks/scripts/google_tasks.sh pull --list "App notes" --max 20
bash .skills/google-tasks/scripts/google_tasks.sh pull --json
bash .skills/google-tasks/scripts/google_tasks.sh done <task-id>
```

`--help` works on the script and on every subcommand.

`pull` returns open tasks only, so a note already marked done never comes back. It prints the
title, the stable id, the due date if there is one, and the note body:

```
App notes

Lesson footer jumps on swap
  id    YWJjZGVmZ2hpams
  due   2026-09-05
  note  Footer moves ~8px when the answer swaps.
        Only on the phone, portrait.
```

## The workflow this skill exists for

For each note, in order:

**1. Pull.** `pull` the list. Read the whole note body, not just the title — the title is what
fits on a phone screen, the body is usually the actual report.

**2. Decide what it is.** From `AGENTS.md` **# Issues**:

- An issue exists only for work a pull request can close, a bug, or a decision worth
  recording.
- Findings, measurements and design notes are **comments on the existing issue** — never a new
  issue.
- Before filing, search open issues for an existing home. One issue may host several PRs.
- If the note is not any of these — a reminder, a duplicate, something already fixed — nothing
  is filed. It is still closed in step 4.

Ask the user when a note is ambiguous. Guessing produces board noise, which is the failure
this skill is meant to reduce, not cause.

**3. File it.**

New work, a bug, or a decision:

```bash
bash .skills/gh-project-workflow/scripts/start_issue_flow.sh \
  --title "..." --priority Major --status "In progress" --base master
```

Never a raw `gh issue create`: a `PreToolUse` hook refuses it, because it makes an issue that
is on no board, with no Status and no Priority — work nobody can see.

A finding on tracked work:

```bash
gh issue comment <number> --body "..."
```

Keep the wording neutral. The repository is public: no production facts in issues, comments or
commits.

**4. Close it at the source.**

```bash
bash .skills/google-tasks/scripts/google_tasks.sh done <task-id>
```

Only after the note has actually become tracked work, or after deciding it needs none. The id
comes from `pull` and is stable; nothing here addresses a task by its position in the list, so
editing the list from the phone mid-run cannot close the wrong one.

## Files

- `scripts/google_tasks.sh` — the CLI: `doctor`, `auth`, `lists`, `pull`, `done`
- `references/setup.md` — the one-time Google Cloud and oauth2l setup
- `references/google-tasks.env.example` — config template, placeholders only

## Notes

- Talks to the Google Tasks REST API v1 with `curl` and `jq`; the access token comes from
  `oauth2l`, Google's official OAuth CLI. No SDK, no new runtime dependency.
- `showCompleted` defaults to **true** in that API, so `pull` passes it explicitly. A command
  written by hand against the same endpoint will return closed notes unless it does the same.
- A consent screen left in *Testing* is issued a refresh token that expires after 7 days. If
  `doctor` starts failing on the token about a week after setup, that is why —
  `references/setup.md` says how to avoid it.
