## Why

#346 established the rule: the session that coordinates tracked work delegates repository
edits rather than making them, and stays in the main checkout. Nothing enforces it, and the
rule exists precisely because it was broken — the coordinator edited a script itself,
entered a worktree to do it, and every agent raised from there inherited that pin.

The repository already has the shape of an answer. `.claude/hooks/delivery-guard.sh` refuses
the commands that create work the board cannot see, and names the command to use instead. A
`PreToolUse` hook on the editor tools can hold the coordinator rule the same way.

## What Changes

- `.claude/hooks/coordinator-guard.sh` — a `PreToolUse` hook on `Edit|Write|NotebookEdit`.
  A subagent passes untouched. The coordinating session is refused when it writes into the
  repository, and the refusal names the way out: file the issue, hand it to an executor.
- The same hook is registered on `Bash` as well, where it **asks** and never denies. The
  editor matcher was the only guarded route, and the unguarded one beside it got used three
  times while this change was being built: edits applied through Bash after Edit/Write was
  blocked, a commit assembled with low-level git and pushed straight to a ref, and a `cp`
  over a hook script after a denial. The asymmetry is deliberate — an editor target arrives
  in the payload and is certain, a shell target is parsed out of a string and is a guess, and
  a hard refusal on a guess would fire on the compound commands people run all day. On the
  shared `Bash` event the hook emits `ask` or nothing, never `allow`, so it cannot undercut
  the refusals `delivery-guard.sh` returns for the same command. The list of write shapes is
  short, explicitly incomplete, and cannot be completed; the header comment says so, and says
  that the guarantee is the stop-and-report rule with the hook as its tripwire.
- `.claude/hooks/delivery-guard.sh` judges `gh pr create` by the branch the command names
  with `--head`/`-H` when it names one, and by the current branch only when it does not.
  It took the current branch unconditionally, so it decided about something other than what
  the command does. Under the coordinator rule the session opening the pull request sits in
  the main checkout on `master`, which is now the normal configuration, and the old test
  refused every correct invocation — found while trying to open the pull request for this
  change. The invariant is untouched: the named branch must still be `<number>-<slug>`, a
  fork's `owner:` prefix is stripped first, and a named non-issue branch is refused with the
  same explanation.
- `.claude/settings.json` gains both registrations.
- `AGENTS.md` names the hook where it already names `delivery-guard.sh`.
- `AGENTS.md` and `.claude/rules/repo-delivery.md` also state what to do when a guard
  refuses: stop, and report the action and the refusal verbatim to the owner. Three
  circumventions are named because each has happened — a commit assembled in a side worktree
  and pushed straight to the branch ref, a command reworded past a static check, and
  `DELIVERY_GUARD=off` set on the agent's own judgement.

The decision rests on two independent signals rather than one, because only one of them
could be measured:

- `agent_id`, present in the payload only for subagents, is the coordinator marker. Taken
  from the hooks documentation; capturing a live payload turned out to be impossible from
  inside a session, so it is documented and not observed.
- the branch of the worktree that owns the **target file** — the same `<number>-<slug>`
  invariant `delivery-guard.sh` applies to `gh pr create`.

Combined so a wrong assumption costs an approval prompt rather than a wall: a coordinator
writing onto a non-issue branch is denied outright, and a coordinator writing onto an issue
branch is asked, because that shape is also a main session doing the full-stand work
`AGENTS.md` sends to the main checkout. Scratch outside the repository and
`.claude/settings.local.json` stay open.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-agent-infrastructure`: the coordinator rule stops being advisory — the repository
  SHALL enforce it with a hook that refuses editor tools from the coordinating session,
  leaves subagents alone, and records in its own comment what it cannot distinguish.
- `repo-delivery-workflow`: a refusal SHALL stop the work and be reported verbatim, and
  SHALL NOT be circumvented. The pull-request refusal SHALL judge the branch the command
  names rather than the one the working directory happens to be on.

## Impact

- `.claude/hooks/coordinator-guard.sh` (new), `.claude/hooks/delivery-guard.sh`,
  `.claude/settings.json`, `AGENTS.md`, `.claude/rules/repo-delivery.md`.
- No application code.
- Three separate things, kept separate: the rule belongs to `repo-delivery-workflow` and is
  delivered by #346/#347; this change adds its enforcement, and the response to that
  enforcement. Neither delta restates #347's requirement text.
