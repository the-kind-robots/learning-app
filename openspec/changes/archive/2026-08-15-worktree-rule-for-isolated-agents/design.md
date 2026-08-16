## Context

Claude Code isolates an agent launched with worktree isolation by pinning it to one worktree and
refusing Bash calls that leave it. Four things are checked: file edits, the command's resolved
working directory, git redirects (`git -C <path>`, even at a linked worktree of the same repo), and
command shape — a compound command it cannot statically prove stays in bounds is refused outright,
so `cd <in-bounds path> && ...` chains are refused too. Per the harness documentation the check
cannot be turned off: no settings key, environment variable or permission entry relaxes it, and
`worktree.baseRef` (`fresh` / `head`) is decided at launch and never names an existing branch.

Measured in this repository, not inferred: `EnterWorktree` into a newly created sibling worktree
succeeds and moves file access, after which every Bash call is refused — `pwd` included — because
the guard still expects the launch worktree. `ExitWorktree` refuses a pinned agent outright.
Entering the launch worktree again by path restores Bash. `git add -A` in a parent worktree stages a
nested worktree as an embedded repository.

## Goals / Non-Goals

**Goals:**

- A rule whose sequence runs to a pushed branch in both situations, with no refused Bash call.
- No nested worktree can be committed by accident.
- Closeout removes what each situation creates.

**Non-Goals:**

- Changing or defeating the harness guard. It is not ours; nothing in this repository can turn it
  off, and no rule should pretend otherwise.
- A second worktree location for ordinary work. `.claude/worktrees/` stays the only one.

## Decisions

**The launch decision comes first, because it is the only clean fix.** An agent expected to land a
specific `gh issue develop` branch is launched without worktree isolation. Isolation is for work
that stays on the branch the agent already holds. This is stated before any sequence, so the
awkward case is entered deliberately rather than discovered halfway through.

**For an agent already pinned, the fallback is a worktree nested inside its own.** It is the only
sequence that runs: the path stays inside the guard's boundary, so `git worktree add`, `cd`, commit
and push all pass. The alternative — switching the pinned worktree onto the new branch — was
rejected: that worktree usually belongs to another task, and taking its branch away is worse than an
ugly path.

**`EnterWorktree` is excluded from the fallback, by name.** It appears to be the sanctioned tool and
is precisely the trap: it moves the agent's file access but not the guard, and the pinned agent
cannot undo it. Plain `cd` in Bash is enough, since the nested path is in bounds.

**`.gitignore` carries the nested path.** Not a second location for work — a guard against
committing one. `git add -A` in the parent stages the inner checkout as a gitlink, and this repo's
finish script stages everything by default.

## Risks / Trade-offs

- **A nested worktree is invisible to `git worktree list` consumers that assume `.claude/worktrees/*`
  is flat** → closeout names the nested case explicitly, and the ignore rule keeps it out of commits
  meanwhile.
- **The fallback normalises something the repo otherwise forbids (a worktree made by hand)** → it is
  written as an exception with its trigger stated, not as an alternative style.
- **The harness may fix the `EnterWorktree` interaction later** → the rule records what was measured
  and when; if the guard starts following `EnterWorktree`, the fallback loses its reason to exist
  and can be deleted in one edit.

## Open Questions

- Whether the harness gap deserves a `/feedback` report from the owner. It is the only route: the
  repository cannot patch it.
