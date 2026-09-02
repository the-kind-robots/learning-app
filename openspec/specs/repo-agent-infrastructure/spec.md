# repo-agent-infrastructure Specification

## Purpose
TBD - created by archiving change keep-only-codex-agent-files. Update Purpose after archive.
## Requirements
### Requirement: Backend port is overridable via environment variable
The backend server SHALL read its listening port from the `LEARNING_APP_PORT` environment variable when set, falling back to 8083 when absent.

#### Scenario: Backend starts with default port
- **WHEN** `LEARNING_APP_PORT` is not set in the environment
- **THEN** the backend listens on port 8083

#### Scenario: Backend starts on a custom port for a parallel worktree
- **WHEN** `LEARNING_APP_PORT=8183` is set in the environment
- **THEN** the backend listens on port 8183

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism, which keeps them under `.claude/worktrees/`, except where that mechanism cannot reach the branch the work must land on: an agent pinned to a worktree SHALL create its linked worktree inside the worktree it was given, since a path outside it is refused. The repository SHALL NOT define a second location for ordinary work, and the only ignore rule it SHALL carry for worktrees is the one that keeps such a nested worktree from being committed, because staging everything in the parent records the inner checkout as an embedded repository.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism, and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task touches sync, the dictionary, migrations, schema, or anything served through CouchDB or nginx
- **THEN** it runs in the main worktree, because a worktree isolates files while ports, CouchDB and nginx stay shared

#### Scenario: A pinned agent creates the only worktree it can use
- **WHEN** an agent pinned to a worktree adds a linked worktree inside it
- **THEN** staging everything in the parent worktree cannot commit that checkout, and closeout removes it together with the branch

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

### Requirement: The coordinator rule is enforced, not only written

The repository SHALL refuse repository edits made by the coordinating session, through a
`PreToolUse` hook on the editor tools rather than by convention. The refusal SHALL name what
to do instead, so the rule is actionable at the moment it is hit.

The same hook SHALL also observe shell commands, because the editor matcher is the only
guarded route and the unguarded one next to it gets used. A shell statement that looks like
a write into the repository SHALL be put to the human. It SHALL NOT be refused outright: the
target of an editor call arrives in the payload and is certain, while the target of a shell
command is parsed out of a string and is a guess, and a hard refusal on a guess would fire
on the compound commands that are run legitimately. Certainty earns a refusal; a guess earns
a prompt.

On the shell event the hook SHALL emit a prompt or nothing, and SHALL NOT emit an explicit
allow, because another guard shares that event and an explicit allow could undercut its
refusal. Silence SHALL be the way this hook defers.

The set of shell write shapes SHALL be short, explicit and declared incomplete. It cannot be
completed, and the hook's own comment SHALL say so, together with the fact that the guarantee
is the stop-and-report rule and the hook is only the tripwire that makes a lapse visible.

Writes outside the repository SHALL stay silent — scratch paths, device files, and paths
under the home directory outside the project — so that ordinary scratch work does not train
anyone to switch the guard off.

Enforcement SHALL NOT stand in the executor's way: a subagent SHALL pass untouched, and the
sequence that delivers tracked work — issue branch, worktree on that branch, edits in it —
SHALL run without a refusal from this hook.

Scratch outside the repository SHALL remain writable, as SHALL a developer's untracked local
settings override. The exception list SHALL stay short and explicit.

Where the hook cannot tell two situations apart, it SHALL record that in its own comment,
naming which of its signals is measured and which is taken from documentation, so a later
reader does not assume it is stronger than it is. A signal the hook cannot observe SHALL NOT
be invented.

The hook SHALL allow whenever it cannot decide. A missing dependency, a path it cannot place,
or a failed lookup SHALL end in the normal permission flow rather than a refusal.

#### Scenario: The coordinating session edits the repository

- **WHEN** the coordinating session tries to edit a file in the repository on a branch that
  carries no issue number
- **THEN** the call is refused, and the refusal names the workflow script that files the issue
  and the executor that does the editing

#### Scenario: The executor edits in its worktree

- **WHEN** an agent delegated the work edits files in the worktree it was given
- **THEN** no refusal comes from this hook, and the delivery sequence runs to a pushed branch

#### Scenario: The coordinating session edits inside a worktree

- **WHEN** the coordinating session tries to edit a file whose worktree is on an issue branch
- **THEN** the call is put to the human rather than allowed silently, because that shape is
  both the failure the rule prevents and the full-stand work the repository sends to the main
  checkout, and the hook cannot separate them

#### Scenario: The coordinating session writes through the shell

- **WHEN** the coordinating session runs a shell statement that writes into the repository —
  an in-place edit, a copy, a move, an output redirection to a repository path
- **THEN** the call is put to the human rather than refused, and the prompt names both ways
  out: stop and report the refusal that led here, or hand the edit to an executor

#### Scenario: A shell write outside the repository

- **WHEN** the statement writes to a scratch path, a device file, or a path under the home
  directory outside the project
- **THEN** nothing is emitted, because a guard that interrupts ordinary scratch work is a
  guard that gets switched off

#### Scenario: The shell event is shared with another guard

- **WHEN** the hook has nothing to say about a shell command
- **THEN** it emits nothing at all rather than an explicit allow, so it can never weaken the
  refusal another guard is returning for the same command

#### Scenario: A write shape that is not on the list

- **WHEN** a shell command reaches a repository file by a route the list does not name
- **THEN** it passes unchallenged, and that is a known and stated property rather than a
  defect, because the enforcement of last resort is the rule and not the hook

#### Scenario: Scratch and local settings

- **WHEN** the target is outside the repository, or is the untracked local settings override
- **THEN** the write proceeds

#### Scenario: The hook cannot decide

- **WHEN** a dependency is missing, or the target cannot be placed in a repository
- **THEN** the call proceeds through the normal permission flow, because a guard that denies
  on its own error is a guard that gets removed

### Requirement: A note captured outside the repository has a path onto the board

The repository SHALL provide a command that reads notes captured in the external inbox the
owner writes to from a phone, and SHALL state the rule that turns such a note into tracked
work. Reading a note SHALL NOT be a separate decision procedure: a note becomes an issue only
when it is work a pull request can close, a bug, or a decision worth recording, and otherwise
becomes a comment on the issue that already hosts it — the same judgement the repository
applies to a note typed by hand.

An issue made from a note SHALL be filed through the workflow script that puts it on the
board, never through a raw issue-creation command, since an issue on no board is work nobody
can see.

A note that has become tracked work SHALL be closed at the source, addressed by its stable
identifier rather than by its position in the list, so that the same note is not read twice
and so that a concurrent edit of the list cannot close the wrong one.

#### Scenario: A captured note is work

- **WHEN** a pulled note describes something a pull request can close, a bug, or a decision
  worth recording
- **THEN** it is filed through the workflow script that creates the issue on the board with a
  Status and a Priority
- **AND** the source note is marked done, so the next pull does not return it

#### Scenario: A captured note is a finding

- **WHEN** a pulled note is a finding, a measurement, or a design note about work already
  tracked
- **THEN** it becomes a comment on that issue rather than a new issue
- **AND** the source note is marked done

#### Scenario: The same note is pulled twice

- **WHEN** a note has already been turned into tracked work and marked done
- **THEN** a later pull does not return it, because the command reads open notes only

### Requirement: Credentials for an external inbox live outside the repository

The command that reaches an external account SHALL take its credentials and its account-specific
settings from outside the repository, and the repository SHALL carry placeholders only. No
client id, client secret, token, or account-specific list identifier SHALL be committed.

The command SHALL be able to report its own readiness without being run for real: whether each
dependency is present, whether the credential file exists, whether a token can be refreshed, and
whether the configured default list resolves. That report SHALL name what is missing and where
to put it, and SHALL print no secret.

Failure SHALL be legible. A missing dependency, an absent credential file, and a rejected API
call SHALL each end in a non-zero exit with a sentence a human can act on — never an unbound
variable, a stack trace, or a raw response dump.

#### Scenario: Nothing is configured yet

- **WHEN** the readiness check runs on a machine where no credential has been placed
- **THEN** it exits non-zero and names the missing file, its expected location, and the setup
  document that explains how to obtain it

#### Scenario: The credential path is wrong

- **WHEN** the configured credential file does not exist at the path given
- **THEN** the failure names that path rather than failing later inside a dependency

#### Scenario: The API rejects a call

- **WHEN** the remote API answers with an error
- **THEN** the command prints the API's own error message and exits non-zero

#### Scenario: A secret would be printed

- **WHEN** the readiness check reports on credentials and tokens
- **THEN** it reports their presence and usability without printing their contents

