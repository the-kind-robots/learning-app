## Why

The worktree workflow was built when the harness had no notion of it. Claude Code 2.1.258
has one, and several of our pieces re-implement or contradict what it provides: every
executor spawn retypes the same briefing and the `isolation: "worktree"` flag by hand;
`delivery-guard.sh` asks on `git checkout -b` where a native `permissions.ask` rule says the
same; `.claude/rules/repo-delivery.md` loads the `# Delivery` text a second time into every
session, next to the `@AGENTS.md` import that already loads it; and
`.skills/repo-task-delivery/SKILL.md` still tells a session to `EnterWorktree` and
`ExitWorktree`, which #382 established a coordinator cannot do. Issue #387.

## What Changes

- Add `.claude/agents/executor.md`: `isolation: worktree` in the frontmatter plus the
  standing briefing — read `AGENTS.md`; plain `git checkout <branch>` inside your own
  worktree, `git worktree add ./wt-<n>` only when another worktree holds the branch; never
  `git -C` outside your root; a refusal stops the work and is reported verbatim; stage
  explicitly; do not merge.
- Move the `git checkout -b` / `git switch -c` prompt from `delivery-guard.sh` into
  `.claude/settings.json` `permissions.ask`. The hook keeps what rules cannot express: the
  `gh issue create` refusal and the branch-conditional `gh pr create` refusal, each with the
  text that names the script to run instead. Precedence of `ask` over the existing
  `Bash(git *)` allow is measured, not cited.
- Delete `.claude/rules/repo-delivery.md`. It has no `paths:` key, so it loads
  unconditionally, on top of the import that made it redundant.
- `.skills/repo-task-delivery/SKILL.md`: the where-does-work-happen decision names the main
  checkout versus the `executor` agent; cleanup no longer tells the session to
  `ExitWorktree`.
- `AGENTS.md` **# Branches and Worktrees**: hand work to the `executor` agent; the
  definition owns the isolation flag and the pinned-agent branch recipe, so both bullets
  leave the section. Measurement note re-dated to 2026-09-02 / 2.1.258. Line 20 notes
  that `worktree.bgIsolation` covers a background coordinator's editor writes natively.
- `.claude/hooks/coordinator-guard.sh` header: same note. Comment text only — no logic
  change.

Not changed: coordinator-guard logic, the `gh` arms of delivery-guard, `.gitignore`.

## Capabilities

### New Capabilities

None. No product behaviour moves: this change edits agent process files, one permission
setting and a shell comment.

### Modified Capabilities

- `repo-delivery-workflow`: *A branch for tracked work is linked to its issue* — the
  delegation scenario names the `executor` agent definition as the carrier of isolation and
  of the pinned-agent recipe, replacing the by-hand `isolation: "worktree"` spawn and the
  recipe repeated in `AGENTS.md`. *Commands that bypass tracked delivery are refused* — the
  branch-creation prompt is a permission rule, not a hook decision; the hook's scope is
  restated as the refusals that need branch state or a message. *The delivery rules are
  present in every session* — satisfied by the `@AGENTS.md` import alone; a second copy is
  not required and is removed.

## Impact

- `.claude/agents/executor.md` (new)
- `.claude/settings.json` (`permissions.ask`)
- `.claude/hooks/delivery-guard.sh` (the `git` arm removed)
- `.claude/rules/repo-delivery.md` (deleted)
- `.skills/repo-task-delivery/SKILL.md`
- `AGENTS.md`
- `.claude/hooks/coordinator-guard.sh` — header comment only; `bash -n` must pass and the
  diff must contain no executable line.

No source code, no tests, no deployment surface.
