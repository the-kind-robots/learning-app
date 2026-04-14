---
name: openspec-propose-change
description: Start a new OpenSpec change and create the first ready artifact for it. Use this when the user wants to propose a feature, fix, or structured change in this repository using the repo's OpenSpec workflow, or when a newly reported repo bug/feature is being translated into tracked work.
---

# OpenSpec Propose Change

Use this skill to start the repo's OpenSpec flow.

The primary path in this repo is:

`propose -> apply -> archive`

For users coming from the older `opsx` flow, see `references/workflow-map.md`.

## When to use

Use this skill when the user wants to:

- start a new change
- turn an idea or bug report into an OpenSpec change
- create the first artifact for a change

Do not use this skill when:

- the change already exists and the user wants to implement tasks
- the user wants to archive or verify an existing change

## Workflow

1. Check that OpenSpec is initialized:

```bash
openspec status --json
```

2. Determine the change name.
   - If the user already gave a clear kebab-case name, use it.
   - Otherwise derive a short kebab-case name from the request.
   - If a matching active change already exists, continue that change instead of creating a duplicate.

3. Create the change:

```bash
openspec new change "<name>"
```

4. Inspect the workflow state:

```bash
openspec status --change "<name>" --json
```

5. Find the first artifact with status `ready`, then load instructions:

```bash
openspec instructions "<artifact-id>" --change "<name>" --json
```

6. Read any dependency/context files from the instruction output.

7. Create exactly one artifact.
   - Usually this is `proposal.md`, but trust the CLI output instead of assuming.
   - Use the provided template.
   - Treat `context`, `rules`, and similar fields as constraints for you, not file content.

8. Summarize:
   - change name
   - artifact created
   - what is now unlocked
   - suggest `openspec-apply-change` once tasks exist

## Guardrails

- Create only the change and the first ready artifact.
- Prefer the default schema unless the user explicitly asks for another one.
- Do not copy raw instruction JSON into the artifact.
- If the user's request is still vague after repo exploration, ask one focused question before writing.
