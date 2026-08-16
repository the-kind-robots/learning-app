# repo-agent-infrastructure Delta

## ADDED Requirements

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
