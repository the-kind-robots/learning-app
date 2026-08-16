## 1. AGENTS.md

- [x] 1.1 Replace the topic-list workspace choice under **Branches and Worktrees** with a dependency test: name the shared resource the work needs, or it goes to a worktree; keep subsystems as examples only
- [x] 1.2 State that a worktree still verifies browser behaviour on fixture data, since `localhost` is a secure context and OPFS, Web Locks and workers work there
- [x] 1.3 Record next to the delegation rule that a background coordinating session has no third option: its delegated edits go to a worktree, because writes to the shared checkout are refused

## 2. .claude/rules/repo-delivery.md

- [x] 2.1 Bring the restated rule into the delegation paragraph so it does not diverge from AGENTS.md

## 3. Verification

- [x] 3.1 Re-read both files whole; check the new rule does not contradict delegation, agent pinning, the `gh issue develop` + `git worktree add` order, or the do-not-route-around-a-refusal rule
- [x] 3.2 Confirm every file, script and hook the touched text names exists
