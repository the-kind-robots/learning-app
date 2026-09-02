## MODIFIED Requirements

### Requirement: A branch for tracked work is linked to its issue

Tracked work SHALL happen on a branch created from its issue, including when the work is isolated in a worktree. The repository SHALL record, for each situation it puts agents in, an order that satisfies both the branch rule and the worktree rule and that runs to a pushed branch without a refused command. That record SHALL rest on one invariant — everything under an agent's own worktree root is reachable to it, and nothing outside that root is — and SHALL name the tool that must not be used and why. A session that coordinates tracked work SHALL delegate repository edits rather than making them and SHALL stay in the main checkout.

Delegated edits SHALL go to a named agent definition, `.claude/agents/executor.md`, that carries the worktree isolation in its frontmatter and the branch recipe in its body: plain `git checkout <branch>` inside the agent's own worktree, and a nested linked worktree only when another worktree already holds that branch. `AGENTS.md` SHALL point at that definition rather than restating the isolation flag or the recipe, so the two cannot drift apart.

#### Scenario: Work is isolated in a worktree

- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on that branch, so the issue keeps its development link

#### Scenario: Work is delegated to an executor

- **WHEN** work that must land its own issue branch is delegated to an agent
- **THEN** that agent is launched as the `executor` agent, whose definition sets worktree isolation, because an agent launched without it can neither enter a worktree nor write into the repository through the editor tools

#### Scenario: A pinned agent must deliver anyway

- **WHEN** an agent already pinned to a worktree has to land a branch that another worktree holds
- **THEN** it follows the recipe in its own definition: it creates the issue branch first, adds a linked worktree inside its own worktree, works there with plain directory changes, and reaches a pushed branch with no refused command

#### Scenario: The session that coordinates the work

- **WHEN** a coordinating session needs a repository edit
- **THEN** it delegates that edit to the `executor` agent and stays in the main checkout

### Requirement: Commands that bypass tracked delivery are refused

The repository SHALL refuse the commands that create work outside the board. A raw issue
creation SHALL be denied. A pull request SHALL be denied from a branch that carries no
issue number, and allowed from a branch created for an issue. Each refusal SHALL name the
command to use instead.

Creating a branch by hand (`git checkout -b`, `git switch -c`) SHALL require approval.
That prompt SHALL be a permission rule in `.claude/settings.json`, not a hook decision,
because a permission rule expresses it exactly; the rule SHALL take precedence over the
blanket `git` allow in the same file, and that precedence SHALL be measured rather than
assumed. The hook SHALL keep only the decisions a permission rule cannot express: those
that depend on the current branch and those that must carry a message naming the command
to run instead.

The branch a pull request is judged on SHALL be the branch the command names when it names
one, and the current branch only when it does not. Judging a pull request by the working
directory's own branch decides about something other than what the command does: under the
coordinator rule the session that opens the pull request sits in the main checkout on the
default branch, which is now the normal configuration, so the current-branch test refuses
every correct invocation. Both the long and the short spelling of the flag SHALL be
recognised, in their separated and joined forms, and a fork owner prefix SHALL be stripped
before the branch is judged.

The invariant SHALL NOT weaken: the branch, wherever its name came from, SHALL still carry
an issue number, and a pull request named onto a branch without one SHALL be refused with
the same explanation.

#### Scenario: Issue created by hand

- **WHEN** an agent runs a raw issue-creation command
- **THEN** the call is denied and the refusal names the workflow script that creates the
  issue, places it on the board and sets its fields

#### Scenario: Branch created by hand

- **WHEN** an agent runs `git checkout -b` or `git switch -c`
- **THEN** the permission rule asks for approval, ahead of the blanket `git` allow, and a
  session that cannot ask treats the command as not permitted

#### Scenario: Pull request from an untracked branch

- **WHEN** an agent opens a pull request from a branch with no issue number
- **THEN** the call is denied, because a pull request with no issue behind it is work the
  board cannot see

#### Scenario: Pull request from an issue branch

- **WHEN** the branch was created for an issue
- **THEN** opening the pull request by hand is allowed, which is what a dirty worktree
  requires anyway

#### Scenario: Pull request that names its source branch

- **WHEN** the command names an issue branch explicitly while the working directory is on a
  branch that carries no issue number
- **THEN** the call is allowed, because the branch the pull request would actually be opened
  from is the one that was named

#### Scenario: Pull request named onto a branch with no issue

- **WHEN** the command names a branch that carries no issue number
- **THEN** the call is denied with the same explanation as an untracked current branch,
  because moving where the name comes from does not move the invariant

#### Scenario: The owner asks for the raw command

- **WHEN** the user explicitly wants the bypassed command
- **THEN** an explicit prefix downgrades the refusal to an approval prompt, so the human
  confirms rather than the agent deciding alone

### Requirement: The delivery rules are present in every session

The repository SHALL state the delivery flow in files the agent loads at session start,
not only inside a skill that must first be chosen. The statement SHALL name the
entrypoint skill, the sequence, and the cases where the flow does not apply.

The statement SHALL arrive once. `CLAUDE.md` imports `AGENTS.md`, which carries it; a
second unconditional copy under `.claude/rules/` SHALL NOT exist, because it loads the same
text twice into every session.

#### Scenario: A session begins

- **WHEN** an agent session starts in this repository, in the main checkout or in a
  worktree
- **THEN** the delivery flow and its entrypoint are already in context, without any file
  being read first

#### Scenario: A task arrives that will end in a commit

- **WHEN** the user reports a bug or proposes a change likely to become a committed edit
- **THEN** the agent enters the flow through the entrypoint skill rather than assembling
  the GitHub and OpenSpec steps by hand

#### Scenario: The rules arrive once

- **WHEN** the files loaded at session start are listed
- **THEN** the delivery flow appears in `AGENTS.md` through the `CLAUDE.md` import and in
  no unconditional rules file
