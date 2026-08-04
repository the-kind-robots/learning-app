# Settle on one worktree mechanism and one way to create branches

## Why

Four worktree conventions were live at once — `.worktrees/` (from the `/goal` skill, and not gitignored), `.git/beads-worktrees/` (left from a tool no longer installed), a `.git/worktree-slots*` permission, and `.claude/worktrees/` (where the built-in mechanism puts them). Branch creation was split the same way: `gh-project-workflow` registers the branch on the issue through `gh issue develop`, while `/goal` created branches locally, which left 15 branches with no issue link and no PR. Cleanup was never part of finishing, so 43 local and 28 remote branches had to be deleted in one sitting.

## What changes

- Worktrees come only from the built-in mechanism. `/goal` is removed: it existed to create a worktree and start servers, and the first is now built in while the second only makes sense in the main worktree.
- Branches are created only through `gh issue develop`, so every branch is linked to its issue.
- Where work happens becomes a decision with a stated rule: the main worktree when the task needs the full stand, a worktree otherwise. A worktree isolates files, not the runtime — CouchDB, nginx and the ports stay shared, and a checkout here costs about a gigabyte plus a cold build cache.
- Deleting the branch and removing the worktree becomes part of the merge step.

## Impact

- Removed capability: `goal-skill`.
- Modified: `repo-delivery-workflow` (branch creation, closeout), `repo-agent-infrastructure` (worktree location and its artifacts).
- Files: `.skills/goal/` deleted, `AGENTS.md` gains the rules, `repo-task-delivery` skill updated, stale `.worktrees/` directory and `worktree-slots` permission removed.
