# repo-agent-infrastructure Delta

## ADDED Requirements

### Requirement: The coordinator rule is enforced, not only written

The repository SHALL refuse repository edits made by the coordinating session, through a
`PreToolUse` hook on the editor tools rather than by convention. The refusal SHALL name what
to do instead, so the rule is actionable at the moment it is hit.

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

#### Scenario: Scratch and local settings

- **WHEN** the target is outside the repository, or is the untracked local settings override
- **THEN** the write proceeds

#### Scenario: The hook cannot decide

- **WHEN** a dependency is missing, or the target cannot be placed in a repository
- **THEN** the call proceeds through the normal permission flow, because a guard that denies
  on its own error is a guard that gets removed
