## Why

The workspace rule picks the working directory by naming topics — "sync, dictionary,
migrations, schema, anything talking to CouchDB or nginx" go to the main checkout. A topic
list is easy to match and easy to over-match: a task only has to mention the dictionary to be
routed to the main checkout, whether or not it needs the stand.

That misrouting just happened. Work on #351 (multi-tab access to the SQLite dictionary) was
sent to the main checkout because it is dictionary work. It needed no stand: proving that two
tabs both get suggestions needs one origin, OPFS and a Web Lock, and the fixture dictionary
the browser tests already use serves that on `localhost`. The real database matters only for
cold-start timing.

The cost was not only a wrong directory. A background session cannot write to the shared
checkout at all — the harness blocks it and points at worktree isolation — so the delegated
agent stopped on its first write having done nothing.

## What Changes

- Restate the workspace rule as a test on dependencies instead of a list of topics: name the
  concrete thing that needs the stand (a live CouchDB, nginx routing, a migration against
  real data), or the work goes to a worktree. Topics stay only as examples.
- State what a worktree can still verify: browser behaviour on fixture data, because
  `localhost` is a secure context and OPFS, Web Locks and workers all work there.
- Record, where the delegation rule already lives, that a background coordinating session has
  no third option — the coordinator stays in the main checkout and background writes to the
  shared checkout are blocked, so every delegated edit lands in a worktree.
- Keep `.claude/rules/repo-delivery.md` consistent with AGENTS.md; it restates the same rule.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repo-agent-infrastructure`: the workspace-choice scenario stops enumerating subsystems and
  becomes a dependency test, plus what a worktree can still verify.
- `repo-delivery-workflow`: the branch/delegation requirement records that a background
  coordinating session's delegated edits must go to a worktree.

## Impact

- `AGENTS.md`, section **Branches and Worktrees** (workspace choice) and the delegation bullet.
- `.claude/rules/repo-delivery.md`, the delegation paragraph.
- Documentation only. No application code, build or runtime behaviour changes.
