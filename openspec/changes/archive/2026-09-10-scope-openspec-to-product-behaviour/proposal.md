# Scope OpenSpec to product behaviour

## Why

Every tracked change is required to carry an OpenSpec change, and `openspec validate` refuses
a change with no delta. Together those two rules mean an edit to a shell script or a rules
document must invent a normative requirement about itself before it can be delivered.

Measured over the 30 days to 2026-09-09 on `master` (#400): 25 pull requests merged, 11 of
which touched `src/` or `resources/` and 14 of which did not; every one of them carried 5 to
14 OpenSpec files whatever its size. #385 changed one line of hook logic and carried six files,
five of them spec. Over those 25 pull requests the specs caught one real problem — and that
delta existed only because the flow demanded one, so the system caught a problem it had
created. What carried the weight instead was `AGENTS.md`, the two `PreToolUse` guards, and
ordinary code comments.

## What Changes

- The OpenSpec requirement is narrowed to work that changes product behaviour someone can
  verify afterwards. Repository process — agent rules, hooks, skills, scripts, CI
  configuration, delivery documentation — carries no OpenSpec change.
- The decision is stated as a test, not a list of topics: name the behaviour the work alters
  that someone could check without reading the diff. Can you name one, it needs a change;
  cannot, there is no requirement to state and no change to write.
- `AGENTS.md` carries that test, since it is loaded every session and is where process rules
  live (the second copy was deleted in #388 for duplicating it).
- `.skills/repo-task-delivery/SKILL.md` makes its OpenSpec steps conditional on that test.
- Nothing else in the flow moves: the issue on the board, the branch from that issue, both
  `PreToolUse` guards and the pull request are unchanged. #288, #289, #290 and #292 were lost
  because they were never issues, not because they had no spec, and that failure mode is
  enforced by `delivery-guard.sh`.

## Impact

- Affected specs: `repo-delivery-workflow`
- Affected code: `AGENTS.md`, `.skills/repo-task-delivery/SKILL.md`
- Not affected: `.claude/hooks/delivery-guard.sh`, `.claude/hooks/coordinator-guard.sh`,
  `.claude/settings.json`, `.claude/agents/executor.md`. No existing spec or archived change
  is deleted; this changes what is required going forward, not the history.

## Note on this change itself

This is a process change, and under the rule it introduces it would carry no OpenSpec change
at all. It carries one because the rule in force when the work started is the old one. This is
expected to be the last repository-process change that needs an OpenSpec change.
