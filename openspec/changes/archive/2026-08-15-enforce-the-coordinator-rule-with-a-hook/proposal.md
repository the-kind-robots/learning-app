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
- `.claude/settings.json` gains the registration.
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
  SHALL NOT be circumvented.

## Impact

- `.claude/hooks/coordinator-guard.sh` (new), `.claude/settings.json`, `AGENTS.md`,
  `.claude/rules/repo-delivery.md`.
- No application code.
- Three separate things, kept separate: the rule belongs to `repo-delivery-workflow` and is
  delivered by #346/#347; this change adds its enforcement, and the response to that
  enforcement. Neither delta restates #347's requirement text.
