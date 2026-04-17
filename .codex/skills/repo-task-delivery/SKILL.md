---
name: repo-task-delivery
description: "Use this when the user reports any problem, regression, warning, bug, improvement, or proposed repo change that is likely to result in a committed repository edit. Default to the full repo workflow: GitHub issue/project tracking, OpenSpec change, implementation, verification, pull request, merge, and closeout."
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

`GitHub issue -> OpenSpec change -> implementation -> verify -> archive -> PR/merge`

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
   - Treat any newly reported problem or proposed repo change as a new tracked task unless the user clearly says to avoid GitHub/OpenSpec.
   - If there is an obvious existing issue/change for the same problem, continue it instead of creating duplicates.
   - If it is not yet clear whether the user wants a tracked repo change, ask a short clarifying question before writing code.

2. Start GitHub tracking.
   - Use `gh-project-workflow` to create the issue/project item and branch.
   - In this repo, prefer base branch `master`.
   - For bug reports, default category/type to bug-oriented values when the project supports them.

3. Start OpenSpec.
   - Use `openspec-propose-change` to create the change and first artifact.
   - The change should describe the user-visible bug or feature outcome, not just an implementation detail.

4. Implement.
   - Use `openspec-apply-change`.
   - Read the change context first, then implement the scoped fix.
   - Update task checkboxes as work is completed.
   - Do not start repository code edits before issue + OpenSpec tracking exist, unless the user explicitly asks to skip the tracked flow.

5. Verify.
   - Run the relevant automated checks.
   - For UX or browser bugs, prefer a real browser validation pass, not only unit tests.
   - If the expected verification path depends on repo-owned tooling and that tooling is broken or flaky, fix the tooling first and record that fix in the current tracked work before trusting fallback verification.
   - For visual or layout-quality bugs, do not treat DOM shape, HTMX events, or end-state screenshots as sufficient by themselves.
   - When the question is whether UI is visually stable, anchored, centered correctly, or free of jumps, verify the actual rendered layout with precise visual instrumentation: frame-by-frame geometry, performance/layout traces, animation tooling, or an equivalent browser-level measurement.
   - Be explicit about what was and was not proven. If you only proved the DOM state or swap path, say that you did not yet prove visual stability.
   - Use `openspec-verify-change` when the change is implementation-complete.

6. Close the OpenSpec loop.
   - If the change is complete, archive it.
   - Keep the archived change and main spec updates in the same delivery branch when appropriate.

7. Deliver.
   - Commit only the task-related files.
   - Push, open PR, merge, and close the issue.
   - If branch protection blocks merge, inspect checks first rather than forcing admin overrides.

8. Reset task boundary after delivery.
   - After the issue is closed or the PR is merged, stop assuming follow-up repo work belongs to the same task.
   - Treat only obvious closeout steps as part of the finished task: archive the OpenSpec change, small deployment follow-up, or a tiny fix directly caused by the just-merged change.
   - If the user switches to a new repo scope after closeout, start a new tracked task by default.

## Dirty Worktree Guardrail

This repo often has unrelated local files in the worktree.

When finishing a task:

- do not blindly use a script that stages everything if unrelated changes are present
- stage only the files related to the current issue/change
- prefer manual `git add` / `git commit` / `gh pr create` / `gh pr merge` over all-in-one wrappers when the tree is dirty

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

## Output Expectations

When this skill is used, the assistant should naturally move through:

- issue creation
- change creation
- implementation
- verification
- PR/merge

without waiting for the user to restate each workflow step.
