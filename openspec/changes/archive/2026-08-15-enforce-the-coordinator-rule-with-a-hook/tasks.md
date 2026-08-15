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
- [x] Archive the change on the delivery branch before opening the pull request.
