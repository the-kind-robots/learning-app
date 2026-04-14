## Context

This repository has accumulated agent files for multiple tool ecosystems. At the moment, Codex-specific repo automation lives alongside Claude- and OpenCode-specific files, which makes repository ownership unclear and creates recurring local-worktree noise.

The intended direction is simpler: the repository should own and version only the Codex-related agent infrastructure it expects contributors to share.

## Goals / Non-Goals

**Goals**
- Keep Codex-related repo automation in version control.
- Remove Claude- and OpenCode-specific repo files from the repository.
- Add ignore rules so local non-Codex agent artifacts do not keep reappearing.

**Non-Goals**
- Designing a shared abstraction for multiple agent ecosystems.
- Preserving backwards compatibility for Claude/OpenCode repo workflows.
- Reworking the Codex skill architecture beyond what is needed for ownership cleanup.

## Decisions

### 1) Codex is the only repository-owned agent system
- Keep `.codex/skills/*` that are intended for this repository's tracked workflow and browser/debugging workflow.
- Keep `codex/rules/*` that support repository-local Codex command approvals or workflow guidance.
- Treat other agent ecosystems as local-only and keep them out of the repository.

### 2) Claude and OpenCode files are removed from version control
- Remove tracked `.claude/*` files.
- Remove tracked `.opencode/*` files.
- Do not replace them with mirrored copies under new paths.

### 3) Ignore local non-Codex agent artifacts
- Add `.gitignore` entries for `/.claude/` and `/.opencode/`.
- Keep the ignore policy narrow so repository-owned Codex files stay visible.

## Risks / Trade-offs

- **Risk:** Removing tracked Claude/OpenCode files may affect local workflows that still rely on them.  
  **Mitigation:** Make the repository policy explicit: Codex-only in repo; other ecosystems are local-only.

- **Risk:** Some files under `.codex/` could still be local-only by accident.  
  **Mitigation:** Review the current `.codex/` and `codex/` contents before committing and keep only repository-owned workflow files.

## Migration Plan

1. Define the repository capability for Codex-only agent infrastructure.
2. Remove tracked `.claude/` and `.opencode/` files.
3. Add ignore rules for local non-Codex agent artifacts.
4. Stage and verify only Codex-owned files plus the cleanup changes.

Rollback: restore the removed directories from git history if the repository decides to support additional agent ecosystems again.
