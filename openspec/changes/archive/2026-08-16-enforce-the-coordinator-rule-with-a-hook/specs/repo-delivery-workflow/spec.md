# repo-delivery-workflow Delta

## MODIFIED Requirements

### Requirement: Commands that bypass tracked delivery are refused

The repository SHALL refuse the commands that create work outside the board. A raw issue
creation SHALL be denied. A pull request SHALL be denied from a branch that carries no
issue number, and allowed from a branch created for an issue. Each refusal SHALL name the
command to use instead.

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

## ADDED Requirements

### Requirement: A refusal stops the work and is reported

A refusal from a delivery guard or from workspace isolation SHALL stop the work that hit it
and SHALL produce a report to the owner naming the action attempted, what was refused, and
the verbatim text of the refusal. The owner decides what happens next.

A refusal SHALL NOT be circumvented. In particular:

- a commit SHALL NOT be assembled in a side or detached worktree and pushed straight to the
  branch ref because ordinary writes were blocked;
- a command SHALL NOT be reworded to slip past a static check instead of being run the
  permitted way;
- `DELIVERY_GUARD=off` SHALL NOT be set on the agent's own judgement, only on a direct
  request from the owner.

The guards inspect command text and the working directory, so they protect against accident,
not against intent. An agent that circumvents one does not defeat the protection — it defeats
the assurance that delivered work went down a verified path. Where the harness flags a move
only after the fact rather than preventing it, the absence of prevention SHALL NOT be read as
permission.

#### Scenario: An agent hits a refusal

- **WHEN** a guard or workspace isolation refuses a command or an edit
- **THEN** the agent stops that line of work and reports the action, the refusal and its
  verbatim text
- **AND** it does not retry the same intent by another route

#### Scenario: A route that is merely unguarded

- **WHEN** a path exists that the guard does not cover, such as a tool the matcher never sees
- **THEN** it is not used to accomplish what was just refused
