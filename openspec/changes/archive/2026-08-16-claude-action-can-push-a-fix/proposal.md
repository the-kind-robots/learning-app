## Why

`/install-github-app` generated a Claude Code workflow whose job runs with `contents: read` and
`pull-requests: read`. Mentioning `@claude` on a pull request therefore produces an answer and
nothing else: the agent can read the diff and the CI results, but the branch it was asked to fix
stays untouched, and the fix has to be copied out of a comment by hand. The workflow is not on
`master` yet, so the permissions can be corrected before anyone relies on the read-only behaviour.

## What Changes

- Add `.github/workflows/claude.yml`, triggered by `@claude` in an issue body or title, an issue
  comment, a pull request review, or a review comment.
- Grant the job `contents: write` and `pull-requests: write` so it can commit to the branch under
  review and update the pull request, instead of only reading them.
- Keep `actions: read`, which is what lets the agent read CI results, and `id-token: write`, which
  the action uses to authenticate.
- Authenticate through the existing `CLAUDE_CODE_OAUTH_TOKEN` repository secret.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repo-agent-infrastructure`: the repository already requires that the agent infrastructure its
  own rules depend on is tracked. This adds the requirement that an agent invoked from GitHub is
  granted the write access its task implies, so a review mention can end in a commit rather than a
  comment describing one.

## Impact

- `.github/workflows/claude.yml` — new file, the only code touched.
- Repository secret `CLAUDE_CODE_OAUTH_TOKEN` — already present, consumed by the workflow.
- The delivery flow in `AGENTS.md` is unchanged: an agent triggered from GitHub pushes to the
  branch of the pull request that invoked it and never to `master`, which stays protected.
- Branch `add-claude-github-actions-1786896889717`, created by `/install-github-app` and linked to
  no issue, is superseded by this change and is deleted with it.
