# Tasks

- [x] Establish what a `PreToolUse` hook receives, and whether it distinguishes the main
      session from a subagent. Result: the hook that runs is the main checkout's copy
      (confirmed — a worktree copy instrumented to log produced nothing), and no live payload
      could be captured from inside a session, so `agent_id` stands as documented and not
      observed. Written into the hook's header comment in those words.
- [x] Write `.claude/hooks/coordinator-guard.sh`: read the payload, decide
      `allow`/`deny`/`ask` with a reason, allow on any internal failure.
- [x] Gate on `agent_id` plus the branch of the worktree that owns the target file, so a wrong
      assumption about `agent_id` costs an approval prompt and not a wall.
- [x] Place the target by comparing `git rev-parse --git-common-dir`, not by deriving a root
      from the hook's own path — the latter guards only the main checkout.
- [x] Keep the exception list to scratch outside the repository and
      `.claude/settings.local.json`.
- [x] Register the hook in `.claude/settings.json` with matcher `Edit|Write|NotebookEdit`.
- [x] Record the limits in the header comment: `Bash` is not covered, and the harness's own
      write guard keys on the parent session rather than the caller or the target.
- [x] Name the hook in `AGENTS.md` where `delivery-guard.sh` is already named.
- [x] State the response to a refusal — stop and report verbatim, with the three named
      circumventions — in `AGENTS.md`, mirrored in one sentence in
      `.claude/rules/repo-delivery.md`, and as its own requirement in
      `repo-delivery-workflow`, distinct from #347's rule and from the enforcement above.
- [x] Say in the hook's comment that it warns and leaves a trace rather than locking
      anything, so a refusal is not read as an obstacle to route around.
- [x] Verify: drive the hook with real payload shapes and show a refused coordinator edit in
      the main checkout and a passing executor edit in a worktree.
- [x] Verify: confirm the executor sequence — issue branch, worktree on it, edits — runs with
      no refusal from this hook.
- [x] Register the same hook on `Bash`, as a second independent entry, because the editor
      matcher was the only guarded route and the unguarded one beside it was used three times
      while this change was being built.
- [x] Make the Bash decision `ask` and never `deny`: an editor target is given in the payload,
      a shell target is parsed out of a string. Write the reason for that asymmetry into the
      header comment.
- [x] Emit `ask` or nothing on the Bash event — never `allow` — so this hook cannot undercut
      the refusals `delivery-guard.sh` returns for `gh issue create` and `gh pr create` on the
      same event.
- [x] Scope the Bash check to the coordinator only, to a short list of write shapes, and to
      targets that look like they are inside the repository. Reuse `delivery-guard.sh`'s
      heredoc stripping and statement splitting so prose in an issue or pull request body is
      never parsed as a command.
- [x] State in the header comment that the list of Bash write shapes is knowingly incomplete
      and cannot be completed, that anything not on it reaches the repository unchallenged,
      and that the guarantee is the stop-and-report rule with the hook as its tripwire.
- [x] Fix `delivery-guard.sh`: judge `gh pr create` by the branch named with `--head`/`-H`
      when the command names one — separated and joined forms, fork `owner:` prefix stripped —
      and by the current branch only when it does not. Found while trying to open this pull
      request: under the coordinator rule the opening session sits in the main checkout on
      `master`, so the current-branch test refused every correct invocation.
- [x] Verify both hooks with crafted payloads: `--head` accepted and refused, the no-`--head`
      path and the `gh issue create` / `git checkout -b` / `DELIVERY_GUARD=off` decisions
      unchanged, the Bash matcher asking on repository writes, staying silent on `/tmp` and on
      subagents, and never emitting `allow`.
- [x] Archive the change on the delivery branch before opening the pull request.
