---
name: gh-project-workflow
description: "End-to-end GitHub execution workflow for project delivery: create project items, assign available single-select fields (such as Status and optional Area), convert draft items to issues when needed, create and check out development branches via `gh issue develop`, then commit, push, create/merge pull requests, and run deployment commands on request. Use when the user explicitly asks to operate GitHub issue/PR lifecycle, or when another repo workflow skill delegates the GitHub tracking and delivery steps to it."
---

# GH Project Workflow

Use this skill to run the GitHub lifecycle in one repository.

In this repo it is often used as a sub-skill of the higher-level tracked-delivery flow:

`repo-task-delivery -> gh-project-workflow + OpenSpec skills`

## Quick Start

1. Ensure GitHub auth includes project scope:
```bash
gh auth refresh -s project
```
2. Load repo-level defaults from `references/config.env.example` (or your own env file).
3. Run `scripts/start_issue_flow.sh` to create and classify work and start coding branch.
4. Run `scripts/finish_issue_flow.sh` to commit, push, create PR, and merge.
5. Run `scripts/deploy.sh` only when the user explicitly asks to deploy.

## One-Time Setup

- Set these environment variables before running scripts:
  - `GHWF_OWNER` (project owner login)
  - `GHWF_REPO` (`owner/repo`)
  - `GHWF_PROJECT_NUMBER` (Project V2 number; this repo uses `2` for the `Learning app` project)
- Optionally set:
  - `GHWF_AREA_FIELD` (default: `Area`, optional if your project does not expose it)
  - `GHWF_PRIORITY_FIELD` (default: `Priority`)
  - `GHWF_SIZE_FIELD` (default: `Size`)
  - `GHWF_STATUS_FIELD` (default: `Status`)
  - `GHWF_DEFAULT_ASSIGNEES` (default: `@me`)
  - `GHWF_DEFAULT_STATUS` (default: `Backlog` in this repo)
  - `GHWF_REVIEW_STATUS` (default: `In review` in this repo)
  - `GHWF_DONE_STATUS` (default: `Done`)
  - `GHWF_DEFAULT_BASE` (default: `master`)
  - `GHWF_DEPLOY_CMD` (deploy command used by `scripts/deploy.sh`)

## Start Work

Run this when the user wants to create a new item and begin implementation.

```bash
.codex/skills/gh-project-workflow/scripts/start_issue_flow.sh \
  --title "Add offline start screen" \
  --body "Allow basic lesson entry without network" \
  --area "Backend" \
  --priority "Major" \
  --status "In progress" \
  --assignees "@me" \
  --labels "enhancement,offline" \
  --base master
```

Important behavior:
- Before creating anything, the script searches the configured project for an exact-title item and reuses it when found.
- Existing issue project items are reused as-is; existing draft items are converted to issues.
- If `--issue <number>` is passed, that issue is reused and added to the project when needed.
- If no project item exists, the script searches repo issues by exact title before creating a new issue.
- Default mode (`--mode issue`) creates an issue only when no reusable project item or issue exists, adds it to project, sets fields, and creates/checks out a dev branch.
- Draft mode (`--mode draft-convert`) creates a draft item only when no reusable item exists, sets fields, converts it to an issue, then creates/checks out branch.
- Branch creation uses `gh issue develop <number> --checkout`.
- Requested optional fields like `Area`, `Priority`, and `Size` are skipped with a warning when the target project does not expose them.
- Missing labels are skipped with a warning instead of aborting issue creation.
- If no assignee or status is provided, the workflow defaults to `@me` and `Backlog`.
- For active work, pass `--status "In progress"` explicitly.
- After the current task is merged or closed, do not keep reusing its branch/issue for the next non-trivial repo scope; start a fresh issue/branch unless the user is clearly asking only for final closeout steps.

## Finish Work

Run this when coding is complete and user asks to wrap up.

```bash
.codex/skills/gh-project-workflow/scripts/finish_issue_flow.sh \
  --issue 142 \
  --base master \
  --merge-method squash
```

Important behavior:
- Stages all changes, generates a commit message when not provided, commits, pushes branch.
- Creates PR if needed.
- Merges PR (or falls back to enabling auto-merge when direct merge is blocked).
- After a successful merge, attempts to set the GitHub Project `Status` field to `Done`.
- When a PR is created and merge is not skipped, attempts to set project `Status` to `In review` before merging.
- After a successful merge, attempts to set project `Status` to `Done`.
- Successful finish changes the task boundary: later repo work should be treated as a new issue/branch by default unless it is obviously just archive/deploy/closeout cleanup for the just-finished task.

If the worktree contains unrelated local changes, prefer manual Git/PR steps instead of staging everything through the wrapper script.

## Deploy

Run only on explicit user request.

```bash
.codex/skills/gh-project-workflow/scripts/deploy.sh
```

Or override command:

```bash
.codex/skills/gh-project-workflow/scripts/deploy.sh --cmd "./infra/deploy.sh staging"
```

## Command-Approval Strategy

To avoid repetitive approvals in Codex sessions, request persistent approval for narrow command prefixes such as:
- `gh project`
- `gh issue`
- `gh pr`
- `git push`
- Your deploy command prefix

Keep prefixes scoped; do not request broad approvals.
