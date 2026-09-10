# Design

## Where the rule lives

`AGENTS.md` carries the test. It is imported by `CLAUDE.md` and therefore loaded at session
start, which is the only place a rule about *whether to open a change* can act — a rule stored
in a spec is read after the decision it governs. `.claude/rules/repo-delivery.md` was deleted
in #388 for duplicating `AGENTS.md`, so no new rules file is created here.

`.skills/repo-task-delivery/SKILL.md` is the executable copy of the flow. Its step 3 becomes a
decision step and step 6 becomes conditional on it; step 4 gains the no-change path. Its
frontmatter `description` is what a session reads when deciding to invoke the skill at all, so
it is corrected too.

## Why a test and not a list

A topic list ("hooks, skills, scripts…") invites argument at every boundary and goes stale as
the repo grows. #352 replaced exactly such a list with a dependency test for where work runs,
and that wording is the model followed here. The test asks for something concrete — name the
behaviour a reader could check afterwards without reading the diff — so the answer is produced
rather than looked up, and "cannot name one" is itself the verdict.

The test also explains the failure it removes. `openspec validate` refusing a delta-less change
is not a bug: a change with nothing to state as a requirement is a change that should not
exist. Under the old rule that refusal had nowhere to go but fabrication.

## What deliberately does not move

The two `PreToolUse` guards, `.claude/settings.json`, and `.claude/agents/executor.md` are
untouched. The tracking failure the repo actually suffered — #288, #289, #290, #292 — was
issues that were never filed, and that is enforced by `delivery-guard.sh`, not by OpenSpec.
Loosening the spec requirement must not be read as loosening tracking, so the spec delta states
the issue/branch/PR obligation explicitly in the same requirement that narrows OpenSpec.

No existing spec or archived change is deleted. Archived changes are history and stay as
written.

## This change under its own rule

Under the rule introduced here this change would carry no OpenSpec change. It carries one
because the rule in force when the work started is the old one, and because narrowing the
requirement is itself a change to `repo-delivery-workflow`, which is a real delta. It is
expected to be the last repository-process change that needs one.
