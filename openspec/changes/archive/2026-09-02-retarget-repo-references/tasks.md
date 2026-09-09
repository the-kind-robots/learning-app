## 1. Retarget the read-only `gh` allowlist

- [x] 1.1 Replace `u473t8/learning-app` with `the-kind-robots/learning-app` in the ten `match` entries of `codex/rules/gh-read.rules`, leaving rule structure, patterns and justifications untouched.
- [x] 1.2 Confirm the file still parses.

## 2. Retarget documentation and config

- [x] 2.1 Update the two `gh api repos/...` paths in the `addBlockedBy` recipe in `AGENTS.md`.
- [x] 2.2 Update `GHWF_REPO` in `.skills/gh-project-workflow/references/config.env.example`; leave `GHWF_OWNER` and `GHWF_PROJECT_NUMBER` as they are, because the board did not move.
- [x] 2.3 Update the `gh issue develop -R ...` line inside the refusal message in `.claude/hooks/delivery-guard.sh`, and nothing else in that file.

## 3. Verify

- [x] 3.1 `bash -n .claude/hooks/delivery-guard.sh` passes and the diff on that file is one line.
- [x] 3.2 No `u473t8/learning-app` remains outside `openspec/changes/archive/**`.
- [x] 3.3 The board is still project number 2 owned by `u473t8`.
