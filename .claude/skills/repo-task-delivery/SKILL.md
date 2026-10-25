---
name: repo-task-delivery
description: "Use this when the user reports any problem, regression, warning, bug, improvement, or proposed repo change that is likely to result in a committed repository edit. Default to the full repo workflow: GitHub issue/project tracking, an OpenSpec change when product behaviour changes, implementation, verification, pull request, merge, and closeout."
---

# Repo Task Delivery

Use this skill as the default entrypoint for new tracked work in this repository.

This is the orchestration skill that turns a user report like:

- "нашёл баг"
- "есть проблема"
- "нужно починить"
- "давай сделаем вот такую фичу"
- "в консоли странный warning"
- "можно это улучшить?"
- "тут бы стоило поменять поведение"

into the repo's standard delivery flow:

`GitHub issue -> [OpenSpec change] -> implementation -> verify -> [OpenSpec sync/archive] -> PR/merge`

The bracketed steps run only when the work changes product behaviour. Step 3 carries the test.

## When to use

Use this skill when:

- the user reports any new bug, regression, warning, error, broken behavior, or suspicious system behavior in this repo
- the user proposes a feature, improvement, cleanup, UX adjustment, refactor, or behavior change in this repo
- it becomes likely that the outcome should be a committed repository edit rather than a one-off local observation
- the user expects the task to be tracked and delivered, not just discussed

Do not use this skill when:

- the user explicitly wants only local exploration or brainstorming
- there is already an active issue/change and the user is clearly continuing it
- the user asks only for a narrow sub-step like "archive this change" or "open a PR"

In those cases, use the narrower workflow skill directly.

## Default Workflow

1. Recognize new work.
   - Treat any newly reported problem or proposed repo change as a new tracked task unless the user clearly says to avoid GitHub tracking.
   - If there is an obvious existing issue/change for the same problem, continue it instead of creating duplicates.
   - If it is not yet clear whether the user wants a tracked repo change, ask a short clarifying question before writing code.

2. Start GitHub tracking.
   - Use `gh-project-workflow` to create the issue/project item and branch.
   - First let `gh-project-workflow` search the configured project for an existing exact-title item or issue; reuse it instead of creating duplicates.
   - For bug reports, default category/type to bug-oriented values when the project supports them.
   - The branch rule, the Priority scale and the dependency edges: AGENTS.md (# Delivery, # Sessions and Worktrees, # Issues).
   - A status that lies is a process bug. Starting work moves the issue to **In progress** (`start_issue_flow.sh --status "In progress" --no-checkout`, or `gh project item-add` + `item-edit`). An issue filed but not started stays **Backlog**.
   - Filing an issue is not done until it is on the board with a Priority and its dependencies declared as native blocked-by relations.
   - Decide where the work happens: full-stand work in the main checkout; everything else in a background session of its own (AGENTS.md, # Sessions and Worktrees).
   - Work that builds or reshapes a screen: AGENTS.md, "Design a screen before building it".

3. Decide whether this work needs an OpenSpec change, then start it if it does.
   - The test is verifiability, not topic. Name the behaviour this work alters that someone could check afterwards without reading the diff — a word that now syncs, a document shape a migration must produce, a screen that now answers differently. Can you name one? It needs an OpenSpec change. Cannot name one? It does not, and there is nothing to write down as a requirement.
   - Repository process fails that test by construction: agent rules, hooks, skills, scripts, CI configuration, documentation about how work is delivered. Such work opens no OpenSpec change — it skips the rest of this step and all of step 6, and goes straight to implementation. Where a process rule needs writing down, it goes in `AGENTS.md`.
   - Skipping OpenSpec never skips the issue, the branch, the guards or the PR. Those are unchanged on both sides of the test.
   - When the work does need a change: use `openspec-new-change` to create it and the first artifact, or `openspec-propose` to generate all artifacts in one step.
   - The change should describe the user-visible bug or feature outcome, not just an implementation detail.
   - Prefer proper OpenSpec delta specs under `openspec/changes/<name>/specs/**/spec.md`.
   - A change that genuinely alters no specs declares `skip_specs: true` in its own `openspec/changes/<name>/.openspec.yaml` (CLI 1.7.0+). That is the supported way to say "no deltas here": `openspec instructions apply` then skips the spec artifact instead of warning, and `archive` stops asking for deltas. The marker is only honoured when that `.openspec.yaml` is valid change metadata — `schema: <name>` naming a known schema — and a change declaring it must carry no files under `specs/`. Use the flag; do not invent a "direct-spec mode" in the task notes.

4. Implement.
   - With an OpenSpec change, use `openspec-apply-change` and read the change context first, then implement the scoped fix.
   - Without one, implement the scoped fix directly; the issue body is the context.
   - Update task checkboxes as work is completed.
   - Do not start repository code edits before the issue and its branch exist — and, when step 3 called for an OpenSpec change, before that change exists — unless the user explicitly asks to skip the tracked flow.

5. Verify.
   - Run the relevant automated checks.
   - For UX or browser bugs, prefer a real browser validation pass, not only unit tests.
   - If the expected verification path depends on repo-owned tooling and that tooling is broken or flaky, fix the tooling first and record that fix in the current tracked work before trusting fallback verification.
   - For Windows CDP/browser proof: AGENTS.md, # Browser / PWA Verification.
   - For scripted Service Worker/PWA checks, prefer committed tests or purpose-built repo verifiers over ad-hoc CDP snippets.
   - For visual or layout-quality bugs, do not treat DOM shape, HTMX events, or end-state screenshots as sufficient by themselves.
   - When the question is whether UI is visually stable, anchored, centered correctly, or free of jumps, verify the actual rendered layout with precise visual instrumentation: frame-by-frame geometry, performance/layout traces, animation tooling, or an equivalent browser-level measurement.
   - If you only proved the DOM state or swap path, say that you did not yet prove visual stability.
   - Use `openspec-verify-change` when there is an OpenSpec change and it is implementation-complete.
   - When the branch changes Clojure sources, run the `smell-reviewer` agent on the branch diff before opening the PR, and fix or answer each finding. "No findings" is not approval.
   - Run OpenSpec CLI commands plainly — `openspec ...`. Telemetry is off by one-time per-machine setup: `openspec config set telemetry.enabled false`.

6. Close the OpenSpec loop, when step 3 opened one.
   - Work that carries no OpenSpec change has nothing to close here; go to step 7.
   - Close OpenSpec before opening or merging the feature PR.
   - Use the proper OpenSpec tools: `openspec-sync-specs` when specs must be synced while the change stays active, or `openspec-archive-change` when the change is complete.
   - If the change is complete, archive it on the delivery branch before PR creation.
   - **Move the change's ADRs out first.** An ADR is born in `openspec/changes/<name>/adr/` and belongs in the repository's top-level `adr/` folder once accepted. Before `openspec archive` runs, `git mv openspec/changes/<name>/adr/*.md adr/` — a move, not a copy, and not after the fact. Archiving first carries the ADRs into `openspec/changes/archive/`, where nothing reads them: the design and adr steps build the supersession graph from top-level `adr/` only.
   - Keep the archive move and main spec updates in the same delivery branch and same PR as the implementation.
   - Do not merge implementation first and archive later unless the user explicitly chooses a follow-up PR.

7. Deliver.
   - Commit only the task-related files.
   - Push, open PR, merge, and close the issue.
   - If branch protection blocks merge, inspect checks first rather than forcing admin overrides.
   - Never push directly to `master`; use PRs for feature work and closeout work.
   - Opening the PR moves the issue to **In review** — that is the owner's signal to look (`finish_issue_flow.sh --no-merge` does it, or set the status directly). The merge moves it to **Done**.
   - The owner reviews in the diff (Start a review → Submit review). A pending review is invisible to the API — if the owner says they reviewed and nothing shows, ask whether they submitted, do not conclude there were no comments.
   - When the owner says the review round is done, process **every** thread on every open PR of theirs:
     - Fetch threads with `gh api repos/<owner>/<repo>/pulls/<n>/comments` — each carries `path`, `line`, `diff_hunk`, `body`, `in_reply_to_id`.
     - Agreed items: fix, push to the same branch, reply in the thread naming the commit, resolve the thread (GraphQL: query `pullRequest.reviewThreads` for ids, then `resolveReviewThread(input: {threadId})`). Commit first, reference after — a hash written before the commit exists is fiction, and it has already cost one edited comment.
     - Disagreements: never silently "fix" — reply with the argument, leave the thread unresolved; the owner decides.
     - ```suggestion blocks may be applied verbatim.
     - The round ends with every thread either resolved-with-commit or answered-and-open; report the split to the owner.
   - Delete branches only after confirming the PR state is MERGED — a failed merge followed by unconditional cleanup deletes the branch and closes the PR unmerged, which has already happened once.
   - Clean up as part of the merge, not later: a background session first runs `git fetch`, then `git switch --detach origin/master` in its worktree, so the harness sees nothing unpushed and deletes the session on the first ctrl+x; then delete the branch locally and on the remote; the worktree is the harness's to remove.

8. Reset task boundary after delivery.
   - After the issue is closed or the PR is merged, stop assuming follow-up repo work belongs to the same task.
   - Treat only obvious closeout steps as part of the finished task: archive the OpenSpec change, small deployment follow-up, or a tiny fix directly caused by the just-merged change.
   - If the user switches to a new repo scope after closeout, start a new tracked task by default.

## Dirty Worktree Guardrail

Stage only the files related to the current issue/change: `finish_issue_flow.sh --add <path>`, or plain `git add <path>`. Run the finish script only once OpenSpec sync/archive is already on the branch.

## Decision Rules

- If a user reports a repo problem and does not say otherwise, assume they want the tracked workflow.
- If a user proposes changing repo behavior and does not say otherwise, assume they want the tracked workflow.
- If a user reports a warning, error, or suspicious behavior that may plausibly lead to a code change, start this workflow or ask one short clarifying question before editing code.
- If the likely result is a commit that should land in the repository, this skill should trigger by default.
- If the user says "let's fix this" after a new problem report, that still counts as tracked workflow unless there is an obvious active issue/change already.
- If the user says "just patch it locally" or "no GitHub/OpenSpec", skip this workflow.
- If the previous task has already been merged/closed, treat the next non-trivial repo scope as a new tracked task unless it is clearly just the closeout tail of the finished task.
- Phrases like "next", "now let's do X", "let's work on Y", or "new scope" after task closeout should be treated as a new tracked-task trigger by default.
- If the user adds new follow-up glitches inside an active issue/change, first add them to the current OpenSpec change/tasks so the session can resume cleanly after interruption.
- After adding those follow-up glitches to spec/tasks, handle them one at a time by default in this order: implement one fix, verify that exact fix, then commit it before starting the next.
- Do not batch separate follow-up fixes from the same active issue into one shared patch unless the user explicitly asks for batching.
- If browser verification relies on a repo-owned CDP/browser skill and that skill misbehaves, treat the tooling failure as the first bug to fix before using alternate browser automation.

## Anti-Patterns

- Do not jump straight into code because the problem looks small.
- Do not treat warnings, logs, or minor UX complaints as "just debugging" when they are likely to become repo fixes.
- Do not wait for the user to explicitly say "create an issue" if the repo's normal expectation is tracked delivery.
- Do not silently continue on the previous issue/branch just because the conversation did not pause; re-evaluate task boundaries after each closeout.
- Do not silently switch to another browser tool just because the preferred repo-owned CDP workflow failed once; repair the preferred tooling first unless the user explicitly approves a fallback.
- Do not archive OpenSpec after the feature PR is merged. Archive before PR merge so one PR contains implementation, spec updates, and archive.
- Do not push archive commits directly to protected `master`; if late archive is unavoidable, create a separate PR and note the workflow miss.
- Do not manufacture a requirement to satisfy `openspec validate`. "No deltas found" on work that changes product behaviour means the delta is missing; on repository process it means step 3 should not have created a change at all. When a change exists and genuinely alters no specs, `skip_specs: true` in its `.openspec.yaml` is the supported answer — never an invented requirement. That flag does not license opening a change for repository process work: such work still opens none.

## Output Expectations

When this skill is used, the assistant should naturally move through:

- issue creation
- change creation, when product behaviour changes
- implementation
- verification
- PR/merge

without waiting for the user to restate each workflow step.
