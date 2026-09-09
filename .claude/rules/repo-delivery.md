# Delivery

Tasks that end in a committed edit run the full flow. Invoke the `repo-task-delivery` skill and let it drive — do not hand-roll `gh` commands.

`issue on the board -> branch from the issue -> OpenSpec change -> implement -> verify -> archive -> PR -> merge -> Done -> cleanup`

Issues come from `bash .skills/gh-project-workflow/scripts/start_issue_flow.sh`, branches from `gh issue develop <n> --checkout`. A raw `gh issue create` is refused by a hook: it produces an issue that is on no board and has no Status or Priority.

The coordinating session stays in the main checkout and delegates repository edits, its own included. Hand work out by spawning the executor with `isolation: "worktree"` — without it an agent cannot reach a worktree at all and its editor tools are refused repository-wide. Everything under your own worktree root is yours and nothing outside it is: a sibling worktree is unreachable, and `EnterWorktree` onto one claims success while moving nothing. Already pinned and needing another branch: `gh issue develop <n>` (no `--checkout`), `git worktree add ./wt-<n> <branch>` inside your own worktree, then plain `cd` (#346).

A refusal from a guard or from workspace isolation stops the work: report the action, the refusal and its verbatim text to the owner, and do not route around it — not by a side worktree and a direct push, not by rewording the command, not with `DELIVERY_GUARD=off`.

Exceptions: questions, pure exploration or measurement, continuing an already active issue, or the user saying to skip GitHub/OpenSpec.

Full rules: AGENTS.md, `# Delivery`.
