## 1. State the rule where sessions load it

- [x] 1.1 `AGENTS.md`, `# Delivery`: the flow line makes the OpenSpec change and its archive conditional.
- [x] 1.2 `AGENTS.md`, `# Delivery`: add the verifiability test, the process exemption, and the statement that issue, branch, guards and PR are unchanged.

## 2. Make the delivery skill follow it

- [x] 2.1 `.skills/repo-task-delivery/SKILL.md`: `description` frontmatter no longer promises an OpenSpec change on every task.
- [x] 2.2 Flow line brackets the conditional steps; step 3 becomes the decision step; step 4 gains the no-change path; step 5 and step 6 are conditional.
- [x] 2.3 Anti-patterns: forbid manufacturing a requirement to satisfy `openspec validate`, and forbid opening a change for hook/skill/script/rules/CI edits.

## 3. Spec

- [x] 3.1 Delta on `repo-delivery-workflow`: `Likely repository changes default to tracked delivery` narrowed to product behaviour, with the issue/branch/PR obligation restated as unconditional.

## 4. Verify

- [x] 4.1 `grep -rn` shows no remaining statement that every change requires OpenSpec outside `openspec/changes/archive/**`.
- [x] 4.2 Both `PreToolUse` hooks are unmodified: `bash -n` clean, and harness-shaped payloads still deny `gh issue create`, pass `gh issue create --help`, and leave `git checkout -b` to the `.claude/settings.json` ask rule.
- [x] 4.3 `OPENSPEC_TELEMETRY=0 openspec validate --specs --strict` clean.
- [x] 4.4 Change archived on the branch before the pull request.
