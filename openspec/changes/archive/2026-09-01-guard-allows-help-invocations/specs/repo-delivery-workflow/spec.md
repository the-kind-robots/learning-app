## MODIFIED Requirements

### Requirement: Commands that bypass tracked delivery are refused

The repository SHALL refuse the commands that create work outside the board. A raw issue
creation SHALL be denied. A pull request SHALL be denied from a branch that carries no
issue number, and allowed from a branch created for an issue. Each refusal SHALL name the
command to use instead.

The guard SHALL judge only invocations that can create work. A statement carrying a
standalone help flag prints documentation and mutates nothing, so it SHALL pass untouched —
neither denied nor prompted — and that test SHALL live once, ahead of the per-command
dispatch, so every rule the guard carries is covered by it rather than by a rule-specific
exemption. The flag SHALL be matched as a whole word, because a statement reaches the guard
already split on whitespace.

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

#### Scenario: A command's documentation is read

- **WHEN** a statement that would otherwise be refused or prompted carries a standalone help
  flag, such as an issue-creation or pull-request command asked for its usage text
- **THEN** the statement passes untouched, because reading documentation creates no issue,
  no pull request and no branch

#### Scenario: Help text quoted inside an argument

- **WHEN** a help flag appears as a word inside a quoted argument rather than as a flag
- **THEN** the guard's judgement is undefined for that statement, because word-splitting has
  already erased the argument boundary by the time the guard sees it — the same blind spot
  the branch-name test already documents

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
