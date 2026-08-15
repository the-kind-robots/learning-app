## REMOVED Requirements

### Requirement: Repository-owned agent infrastructure is Codex-only
**Reason**: The repository outgrew it. `.claude/rules/`, `.claude/settings.json` and the `.claude/skills` symlink are already tracked, and the delivery guard this change adds has to be tracked too — a hook that only exists on one machine enforces nothing, and one that does not reach a worktree misses the place the workflow was skipped. Keeping the requirement would have meant the rule and the code disagreeing, with the code winning silently.

**Migration**: None. The tracked set already covered these files; this records it.

### Requirement: Local non-Codex agent artifacts do not reappear as repo noise
**Reason**: Same drift, stated from the other side. "Non-Codex" is the wrong line to draw — it would put `.claude/rules/repo-delivery.md` among the artifacts to ignore, when it carries a repository rule. The line that matters is repository infrastructure versus a developer's local preferences.

**Migration**: None. `.gitignore` already draws the line this way, ignoring `/.claude/projects/`, `/.claude/settings.local.json` and `.claude/worktrees/` while tracking the rest.

## ADDED Requirements

### Requirement: Repository-owned agent infrastructure covers the agents the repo relies on

The repository SHALL keep under version control the agent infrastructure its own rules
depend on, for every agent used to work on it — not for one agent only. Files that record
a developer's local preferences SHALL stay ignored.

The line is what the file is for, not which tool reads it: a hook that enforces the
delivery flow, a rules file that carries it, and the skills both agents share are
repository infrastructure and are tracked; a personal settings override is local and is
ignored.

#### Scenario: Shared repo agent files are reviewed

- **WHEN** repository-owned agent files are present in the worktree
- **THEN** the tracked set covers the rules, hooks, settings and skills the repo's own
  workflow depends on
- **AND** each is reviewable and reaches every worktree through git

#### Scenario: A developer keeps local tooling preferences

- **WHEN** local, developer-specific agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned agent infrastructure remains visible for tracking and review

### Requirement: Skill invocations use a path that resolves in a worktree

Documented invocations of repository skills SHALL use a path present in every checkout,
including worktrees. A path that exists only in the main checkout SHALL NOT be the
documented one.

#### Scenario: A skill is invoked from a worktree

- **WHEN** an agent follows a documented command from a skill or from the repo agent rules
  while working in a worktree
- **THEN** the path resolves and the script runs

#### Scenario: A path outside the repository

- **WHEN** an invocation refers to a user-global installation rather than a repository
  file
- **THEN** it is left as it is, since it is not a repository path
