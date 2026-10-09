---
name: openspec-onboard
description: Guided onboarding for OpenSpec - walk through a complete workflow cycle with narration and real codebase work. Also use when the user says "openspec onboard" or "opsx onboard".
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.13.1"
---

Guide the user through their first complete OpenSpec workflow cycle: a teaching experience doing real work in their codebase while explaining each step.

**Store selection:** If the user names a store (standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` for ids, then pass `--store <id>` on commands that read/write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Sticky for the rest of the workflow; append it to every unscoped example below (e.g. `openspec status --change "<name>" --json --store "<id>"`). Other commands do not take the flag. Hints printed by commands already carry it. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** Before the first write (`new change`, `archive`, `sync specs`, authoring an artifact file), run `openspec list --json` (with `--store <id>` if selected) and read `root`. Root object = set up. `"root": null` = no `openspec/` dir (command exits non-zero; that is the answer, not a broken CLI - don't retry or work around).
- Exception: if a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml`/`config.yml`, the project uses a store this machine cannot resolve. Not uninitialized: stop before writing and show the user the error's `message` and `fix`.
- Otherwise, no root:
  - **Auto-selected** (user did not name OpenSpec, this skill, or its slash command): stop using OpenSpec, answer normally, don't mention setup.
  - **Explicit request**: stop before writing and ask: set up (`openspec init`), target a store (`--store <id>`), or continue without OpenSpec. Wait.
- Never create the root as a side effect: no `openspec init` until the user asks, no hand-created `openspec/` files.

---

## Preflight

```bash
# Unix/macOS
openspec --version 2>&1 || echo "CLI_NOT_INSTALLED"
# Windows (PowerShell)
# if (Get-Command openspec -ErrorAction SilentlyContinue) { openspec --version } else { echo "CLI_NOT_INSTALLED" }
```
If not installed: tell the user "OpenSpec CLI is not installed. Install it first, then come back to `/openspec-onboard`." and stop.

## Phase 1: Welcome

Short welcome (~15-20 min): pick small real task, explore, create change, build artifacts (proposal, specs, tasks; design only if needed), implement, archive.

## Phase 2: Task Selection

Scan for small improvements: TODO/FIXME/HACK/XXX; swallowed errors; untested functions; TS `any`; debug artifacts (`console.log`, `debugger`); unvalidated input. Also:
```bash
# Unix/macOS
git log --oneline -10 2>/dev/null || echo "No git history"
# Windows (PowerShell)
# git log --oneline -10 2>$null; if ($LASTEXITCODE -ne 0) { echo "No git history" }
```

Present 3-4 suggestions (location `path:line`, scope, why good) plus "Something else?"; user picks a number or describes own.

If nothing found: ask "I didn't find obvious quick wins in your codebase. What's something small you've been meaning to add or fix?"

**Scope guardrail (soft):** if the pick is too large (major feature, multi-day), say it's larger than ideal for a first run and offer: (1) slice it smaller (suggest a specific slice), (2) pick something else, (3) do it anyway (takes longer). Let the user override.

## Phase 3: Explore Demo

Briefly show **explore mode**: 1-2 minutes reading relevant files, brief analysis/considerations; mention `/openspec-explore` is usable anytime. Next: create a change.

**PAUSE** for user acknowledgment.

## Phase 4: Create the Change

EXPLAIN: a change is a container for a piece of work's planning, at the `changeRoot` from `openspec status --change "<name>" --json`.

DO (kebab-case derived name):
```bash
openspec new change "<derived-name>"
```
SHOW: `Created: <changeRoot>`; contents: `proposal.md` (why), `specs/` (requirements), `tasks.md` (checklist), optional `design.md`.

## Phase 5: Proposal

EXPLAIN: the proposal captures **why** and **what**, the elevator pitch.

DO: draft (don't save yet). `<capability-path>` = spec dir relative to `specs/` (e.g. `user-auth`, `identity/user-auth`); use the exact existing path for modified capabilities; follow project organization for new ones.

Sections: Why (1-2 sentences), What Changes (bullets), Capabilities (New: `<capability-path>`; Modified: `<existing-capability-path>`), Impact (files).
Ask whether it captures the intent. **PAUSE** for approval/feedback.

After approval:
```bash
openspec instructions proposal --change "<name>" --json
```
Write the content to its `resolvedOutputPath`. Note the proposal can be refined later. Next: specs.

## Phase 6: Specs

EXPLAIN: specs define **what** in precise, testable requirement/scenario form; a small task may need only one spec file.

DO: resolve the file:
```bash
openspec instructions specs --change "<name>" --json
# Use resolvedOutputPath. If a glob, choose the concrete file path using the schema instruction and the change's context.
```
Draft:
Format: `# Spec Delta` > `## ADDED Requirements` > `### Requirement: <Name>` + description > `#### Scenario: <name>` with `- **WHEN**`, `- **THEN**`, `- **AND**` (optional) lines; reads as test cases. Save to the concrete path chosen from `resolvedOutputPath`.

## Phase 7: Design (optional)

design.md is OPTIONAL: only for cross-cutting changes or when a new ADR is warranted. For a small task, skip it and tell the user why (decisions fit in proposal/tasks). If needed, EXPLAIN it captures **how** (decisions, tradeoffs), draft with sections Context, Goals / Non-Goals, Decisions (each with rationale), and save to `resolvedOutputPath` from `openspec instructions design --change "<name>" --json`.

## Phase 8: Tasks

EXPLAIN: tasks are checkboxes that drive apply; small, clear, in logical order.

DO: generate from specs (and design if any):
Format: `# Tasks`, `## 1. [Category or file]` with `- [ ] 1.1 [Specific task] — verify: [test, command, observable behavior, or delivered artifact]`; final `## N. Integration Verification` with `- [ ] N.1 Verify [broader behavior] with [end-to-end test or observable result]`.
Ask if ready to implement. **PAUSE** for confirmation. Save to `resolvedOutputPath` from `openspec instructions tasks --change "<name>" --json`.

## Phase 9: Apply

DO, for each task:
1. Announce "Working on task N: [description]"
2. Implement in the codebase
3. Reference specs/design naturally ("The spec says X, so I'm doing Y")
4. Mark complete in tasks.md: `- [ ]` -> `- [x]`
5. Brief status: "Task N complete"

Keep narration light. After all tasks, list them as done and say one step remains: archive.

## Phase 10: Archive

DO (`--yes` answers confirmation prompts, which you cannot answer from a tool call):
```bash
openspec archive "<name>" --yes
```
SHOW: `Archived to: <planningHome.changesDir>/archive/<target-name>/` (target name prepends today's date unless the name already starts with `YYYY-MM-DD-`, then kept as-is). Code is in the codebase, decision record preserved.

## Phase 11: Recap & Next Steps

Congratulate; recap cycle (Explore, New, Proposal, Specs, Design if needed, Tasks, Apply, Archive). Show command reference:

| Command | What it does |
|---|---|
| `/openspec-propose <name>` | Create a change and generate all artifacts |
| `/openspec-explore` | Think through problems (no code changes) |
| `/openspec-apply-change <name>` | Implement tasks |
| `/openspec-archive-change <name>` | Archive a completed change |
| `/openspec-new-change <name>` | Start a new change, one artifact at a time |
| `/openspec-continue-change <name>` | Continue an existing change |
| `/openspec-verify-change <name>` | Verify implementation matches artifacts |

Suggest trying `/openspec-propose` on something they want to build.

## Graceful Exit

- **User wants to stop/pause or seems disengaged:** change is saved at `changeRoot`; `openspec status --change "<name>" --json` shows where it stands. Resume with `/openspec-continue-change <name>` (artifact creation) or `/openspec-apply-change <name>` (if tasks exist). No pressure.
- **User just wants the command reference / skip tutorial:** show the command table above, suggest `/openspec-propose`, exit gracefully.

## Guardrails

- EXPLAIN -> DO -> SHOW -> PAUSE at key transitions (after explore, proposal draft, tasks, archive); don't over-pause
- Don't skip phases even if the change is small (design is the one optional artifact)
- Handle exits gracefully; never pressure
- Real codebase tasks only; steer toward smaller scope but respect user choice
