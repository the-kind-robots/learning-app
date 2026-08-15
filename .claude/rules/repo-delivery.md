# Delivery

Tasks that end in a committed edit run the full flow. Invoke the `repo-task-delivery` skill and let it drive — do not hand-roll `gh` commands.

`issue on the board -> branch from the issue -> OpenSpec change -> implement -> verify -> archive -> PR -> merge -> Done -> cleanup`

Issues come from `bash .skills/gh-project-workflow/scripts/start_issue_flow.sh`, branches from `gh issue develop <n> --checkout`. A raw `gh issue create` is refused by a hook: it produces an issue that is on no board and has no Status or Priority.

Delegate work that must land its own issue branch to an agent launched **without** worktree isolation. A pinned agent cannot reach a worktree created beside its own: the guard refuses every Bash call outside its worktree, `EnterWorktree` moves file access but not the guard, and `ExitWorktree` refuses a pinned agent. If you are already pinned, the one sequence that runs is `gh issue develop <n>` (no `--checkout`), `git worktree add ./wt-<n> <branch>` inside your own worktree, then plain `cd` (#346).

Exceptions: questions, pure exploration or measurement, continuing an already active issue, or the user saying to skip GitHub/OpenSpec.

Full rules: AGENTS.md, `# Delivery`.
