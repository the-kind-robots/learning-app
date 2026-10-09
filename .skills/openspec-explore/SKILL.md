---
name: openspec-explore
description: Enter OpenSpec explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements in a project that uses OpenSpec. Use when the user wants to think through something before or during an OpenSpec change. Also use when the user says "openspec explore" or "opsx explore".
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.13.1"
---

Enter explore mode. Think deeply. Follow the conversation wherever it goes. This is a stance, not a workflow: no fixed steps, no required outputs.

**Explore mode is for thinking, not implementing.** Read files, search code, run read-only commands/tools freely, but NEVER write code or implement features. If asked to implement: say explore mode does not implement, point to `/openspec-propose` (turns discussion into a change; work happens there). You MAY create/update OpenSpec change artifacts (proposal, specs, design, tasks) within a confirmed scope.
- Answering design/clarifying questions is never consent to write.
- Before the first write-capable action (including `openspec new change`): name the artifacts/files and what you'd change, ask a direct yes/no question, wait for confirmation in a separate message. Confirmation covers only the described scope; ask again before expanding.
- Exception: a user's own explicit request to capture the exploration as a new change confirms the change and the artifacts the request names (scaffold first, see below). A yes to your offer confirms only what the offer named, so name the change and artifacts in the offer.

**Store selection:** If the user names a store (standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` for ids, then pass `--store <id>` on commands that read/write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Sticky for the rest of the workflow; append it to every unscoped example below (e.g. `openspec status --change "<name>" --json --store "<id>"`). Other commands do not take the flag. Hints printed by commands already carry it. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** Before the first write (`new change`, `archive`, `sync specs`, authoring an artifact file), run `openspec list --json` (with `--store <id>` if selected) and read `root`. Root object = set up. `"root": null` = no `openspec/` dir (command exits non-zero; that is the answer, not a broken CLI - don't retry or work around).
- Exception: if a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml`/`config.yml`, the project uses a store this machine cannot resolve. Not uninitialized: stop before writing and show the user the error's `message` and `fix`.
- Otherwise, no root:
  - **Auto-selected** (user did not name OpenSpec, this skill, or its slash command): stop using OpenSpec, answer normally, don't mention setup.
  - **Explicit request**: stop before writing and ask: set up (`openspec init`), target a store (`--store <id>`), or continue without OpenSpec. Wait.
- Never create the root as a side effect: no `openspec init` until the user asks, no hand-created `openspec/` files.

---

## Stance

- Curious, not prescriptive: questions emerge naturally, no script
- Open threads, not interrogations: surface several directions, let the user pick
- Visual: ASCII diagrams (plain ASCII only: `+ - |`, `--> <-- ^ v`, `* x`; Unicode glyphs misalign) when they help
- Adaptive, patient: pivot on new info, don't rush to conclusions
- Grounded: explore the actual codebase

## Planning a Change

For open-ended discussion, just follow the conversation. When planning a change, guide toward shared understanding with focused discovery:

- Before asking a factual question, inspect OpenSpec artifacts, source, tests, docs, config. Don't ask the user to repeat verifiable facts. Summarize findings without reproducing private context/rules. If evidence is missing/conflicting/inaccessible, say so and ask only what's needed.
- Follow dependencies: resolve the blocking decision before dependent details (outcome/scope before API/data model); revisit downstream assumptions when an earlier answer changes; skip irrelevant branches.
- One focused question at a time, briefly say why it matters and what it unlocks. Batch only if the user asks.
- Give grounded recommendations (preferred option, why, alternatives/tradeoffs). Don't invent intent, priorities, or constraints; ask when only the user knows.
- Keep the record in conversation, not files: separate confirmed decisions, proposed defaults, open questions. Silence is not acceptance; accepting recommendations is not permission to write.
- Stop asking once clarity is enough. Let the user pause, pivot, or defer; don't force a proposal.

## What You Might Do

- Explore the problem: clarify, challenge assumptions, reframe, find analogies
- Investigate the codebase: map architecture, integration points, existing patterns, hidden complexity
- Compare options: brainstorm, comparison tables, tradeoffs, recommend if asked
- Surface risks and unknowns: gaps, spikes

## OpenSpec Awareness

Use it naturally, don't force it.

### Check for context

At start:
```bash
openspec list --json     # active changes: names, schemas, status
openspec list --specs    # durable capabilities (add --json for ids/requirement counts)
```
Append `--store "<id>"` only for a registered standalone store. Inspect one spec cheaply: `openspec show "<spec-id>" --type spec --json --no-scenarios` (`--type spec` avoids ambiguity with a same-named change). That is only an overview: before deciding what is covered or should change, read each relevant spec in full with `openspec show "<spec-id>" --type spec`.

Then read `<root.path>/openspec/config.yaml` (or `.yml`; skip if absent):
- `context`: stack, conventions, constraints
- `rules`: keyed by artifact id; apply only when writing that artifact

These are constraints for you; do NOT copy them into conversation or artifacts.

### When no change exists

Think freely; when insights crystallize, offer e.g. "Want me to create a proposal?" or keep exploring.

If the user asks to capture the exploration as a new change (see confirmation rules above):

1. `openspec new change "<name>"` before any artifact. Never hand-create a change dir under `openspec/changes/` (CLI creates required metadata such as `.openspec.yaml`). Keep `--store <id>` on follow-ups.
2. `openspec status --change "<name>" --json`; process requested artifacts in dependency order. For each `ready` requested artifact run `openspec instructions "<artifact-id>" --change "<name>" --json`. Evaluate any condition in its `instruction` against the explored change; record a deliberate skip if it doesn't apply. If a requested artifact is blocked by a direct prerequisite the user did not request, run `instructions` for that prerequisite too (ready or blocked), evaluate its condition: skip deliberately only if it doesn't apply; otherwise (applies or unconditional) ask before expanding the capture. Don't create unrequested prerequisites without approval.
3. Follow returned `template` and `instruction`. Read completed dependency files from `dependencies`; apply `context`/`rules` as constraints without copying. If the instruction delegates to a skill/command, invoke it; otherwise write to `resolvedOutputPath` (pick a concrete path if a glob). Verify the file exists.
4. After each artifact re-run `status`; continue until every requested artifact is `done`, `skipped`, or deliberately skipped by condition. Tell the user about conditional skips, remember them, don't reconsider. If a requested artifact is `blocked` only by deliberately skipped conditional prerequisites, run its `instructions` anyway and create it. If blocked by an unrequested, non-skippable prerequisite, explain and ask before expanding.

Don't ask the user to invoke another workflow command for the requested capture. If they only asked to start a change, stop after scaffolding and show status. When done, stop and name where work continues: `/openspec-propose` writes remaining planning artifacts; `/openspec-apply-change` implements once tasks exist. Capturing never starts implementing.

### When a change exists

1. Run `openspec status --change "<name>" --json`; use `changeRoot`, `artifactPaths`, `actionContext`; read files in `artifactPaths.<artifact>.existingOutputPaths`.
2. Reference artifacts naturally in conversation (e.g. "The proposal scopes this to premium users, but we now think everyone...").
3. Offer to capture when decisions are made. `<capability-path>` = spec dir relative to `specs/` (e.g. `user-auth`, `identity/user-auth`); preserve an existing capability's full path, follow project organization for new ones.

   | Insight | Capture in |
   |---|---|
   | New/changed requirement | `specs/<capability-path>/spec.md` |
   | Design decision | `design.md` (optional; only for cross-cutting changes / new ADR, else put it in proposal or tasks) |
   | Scope changed | `proposal.md` |
   | New work | `tasks.md` |
   | Assumption invalidated | relevant artifact |

4. User decides: offer and move on; no pressure, no auto-capture.

## Entry Points (brief)

- Vague idea: lay out the spectrum of options/complexity, ask where their head is.
- Specific problem: read the codebase, sketch current flow, name the tangles, ask which is burning.
- Stuck mid-implementation (`/openspec-explore <change>`): read change artifacts, locate the task, trace it, suggest paths; offer to update the design/artifact or add a spike task.
- Option comparison: ask context first, state key constraints, compare in a table, recommend.

## Ending

No required ending: flow into `/openspec-propose`, update artifacts, just give clarity, or continue later. Optional summary: problem, approach, open questions, next steps (turn into a change with `/openspec-propose`, or keep exploring). Sometimes the thinking is the value.

## Guardrails

- Don't implement: no code. Workflow configuration (schemas, templates, `openspec/config.yaml`) counts as a change, not thinking. Only OpenSpec change artifacts within confirmed scope may be written. When the user is ready to build, hand off to `/openspec-propose`.
- Don't fake understanding; dig deeper when unclear
- Don't rush or force structure
- Don't auto-capture: offer to save. Read-only commands/tools need no confirmation; the first write-capable action (including `openspec new change`) needs a named scope, yes/no question, and explicit confirmation in a separate user message. Scope-limited; ask again to expand. Exception: the user's own capture request (above).
- Don't manually scaffold change dirs; always `openspec new change "<name>"` (with `--store <id>` when applicable)
- Do visualize, explore the codebase, question assumptions (the user's and your own)
