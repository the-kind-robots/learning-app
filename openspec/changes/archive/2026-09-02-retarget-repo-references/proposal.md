## Why

The repository was transferred from `u473t8/learning-app` to `the-kind-robots/learning-app` on 2026-09-01. Fourteen references still name the old owner. GitHub redirects the old paths, so nothing is broken today, but `codex/rules/gh-read.rules` matches `gh` invocations as exact strings: entries naming the old path stop being pre-approved and start prompting once commands are typed with the new one.

## What Changes

- Retarget the ten `match` entries in `codex/rules/gh-read.rules` to `the-kind-robots/learning-app`, leaving the rule structure untouched.
- Retarget the two `gh api repos/...` paths in the `addBlockedBy` recipe in `AGENTS.md`.
- Retarget the `GHWF_REPO` default in `.skills/gh-project-workflow/references/config.env.example`.
- Retarget the `gh issue develop -R ...` line inside the refusal message in `.claude/hooks/delivery-guard.sh`. Guard logic is not touched.
- Leave `openspec/changes/archive/**` as written; it is history.

The project board is unchanged: it remains the user project `Learning app` number 2 owned by `u473t8`. `GHWF_OWNER` and the `--owner @me` rules stay as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repo-agent-infrastructure`: records that agent infrastructure spelling out an `owner/repo` coordinate names the repository's current one, that coordinates the transfer did not move — the project board — are left alone, and that archived changes stay as written.

The edits themselves change no behaviour: the same commands are refused, allowed and produced as before. What the delta adds is the invariant this issue violated, so the next transfer has something to check against rather than a redirect hiding the breakage.

## Impact

- `codex/rules/gh-read.rules` — read-only `gh` allowlist; stale entries prompt instead of auto-approving.
- `AGENTS.md`, `.skills/gh-project-workflow/references/config.env.example`, `.claude/hooks/delivery-guard.sh` — documentation, config default and refusal text.
- No application code, build or runtime is affected.

## Links

- Issue: https://github.com/the-kind-robots/learning-app/issues/390
