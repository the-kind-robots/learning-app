## Why

Repository-level agent infrastructure has drifted across multiple tool ecosystems. The repo currently contains Codex, Claude, and OpenCode files side by side, which makes ownership unclear and causes local tool artifacts to keep reappearing in the worktree.

## What Changes

- Consolidate repository-owned agent infrastructure around Codex only.
- Remove Claude- and OpenCode-specific agent files from the repository.
- Keep only Codex-owned skills, rules, and supporting files that are meant to be shared through version control.
- Add ignore rules so local non-Codex agent artifacts do not keep showing up as repo noise.

## Capabilities

### New Capabilities
- `repo-agent-infrastructure`: Defines which agent files are repository-owned, keeps Codex as the single supported in-repo agent system, and excludes local-only artifacts from version control.

### Modified Capabilities
- None.

## Impact

- Affected paths: `.codex/`, `codex/`, `.claude/`, `.opencode/`, `.gitignore`
- Affected systems: repository workflow, local developer tooling, tracked task delivery infrastructure
