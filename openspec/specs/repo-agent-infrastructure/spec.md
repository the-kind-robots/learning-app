# repo-agent-infrastructure Specification

## Purpose
Define the repository-owned infrastructure that coding agents work through: worktree placement, skill paths, the coordinator guard, the inbox that feeds the board, and the GitHub-invoked agent.
## Requirements
### Requirement: Backend port is overridable via environment variable
The backend server SHALL read its listening port from `LEARNING_APP_PORT` when set, falling back to 8083.

#### Scenario: Default port
- **WHEN** `LEARNING_APP_PORT` is not set
- **THEN** the backend listens on 8083

#### Scenario: Custom port for a parallel worktree
- **WHEN** `LEARNING_APP_PORT=8183`
- **THEN** the backend listens on 8183

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism (under `.claude/worktrees/`), except that an agent pinned to a worktree SHALL create its linked worktree inside the one it was given. The only worktree ignore rule SHALL be the one keeping such a nested worktree from being committed. Work SHALL run in the main checkout only where a concrete shared resource can be named (live CouchDB, nginx routing, migration on real data); otherwise it SHALL run in a worktree, which can verify browser behaviour on fixture data.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task depends on a nameable shared resource
- **THEN** it runs in the main checkout

#### Scenario: A stand subsystem is touched without a shared resource
- **WHEN** a task touches the dictionary but names no live CouchDB, nginx routing or real-data migration
- **THEN** it runs in a worktree

#### Scenario: A pinned agent creates its nested worktree
- **WHEN** a pinned agent adds a linked worktree inside its own
- **THEN** staging the parent cannot commit it and closeout removes it with the branch

### Requirement: Repository-owned agent infrastructure covers the agents the repo relies on
Agent infrastructure the repository's rules depend on (enforcement hooks, rules, settings, shared skills) SHALL be tracked for every agent used; personal local overrides SHALL stay ignored.

#### Scenario: Shared agent files are reviewed
- **WHEN** repository-owned agent files are present
- **THEN** they are tracked and reach every worktree through git

#### Scenario: Local preferences
- **WHEN** developer-specific agent files are created
- **THEN** git ignores them

### Requirement: Skill invocations use a path that resolves in a worktree
Documented invocations of repository skills SHALL use a path present in every checkout, including worktrees (`.skills/...`), not one existing only in the main checkout.

#### Scenario: A skill is invoked from a worktree
- **WHEN** an agent follows a documented skill command in a worktree
- **THEN** the path resolves and the script runs

### Requirement: The coordinator rule is enforced, not only written
A `PreToolUse` hook SHALL refuse repository edits by the coordinating session through the editor tools, with a refusal naming what to do instead. The same hook SHALL observe shell commands but only prompt on a statement that looks like a repository write, since a shell target is a guess; on the shell event it SHALL emit a prompt or nothing, never an explicit allow, so it cannot undercut another guard. Its list of shell write shapes SHALL be short and declared incomplete in the hook's comment, along with which signals are measured versus documented. Subagents, writes outside the repository (scratch, device files, home paths) and the untracked local settings override SHALL pass untouched, and the delivery sequence SHALL run without a refusal from it. When it cannot decide, it SHALL fall through to the normal permission flow.

#### Scenario: The coordinating session edits the repository
- **WHEN** the coordinating session edits a repository file on a branch with no issue number
- **THEN** the call is refused, naming the workflow script and the executor

#### Scenario: The executor edits in its worktree
- **WHEN** a delegated agent edits in its worktree
- **THEN** this hook does not refuse

#### Scenario: The coordinating session edits inside an issue-branch worktree
- **WHEN** the target's worktree is on an issue branch
- **THEN** the call is put to the human, since the hook cannot separate this from full-stand work

#### Scenario: Shell write into the repository
- **WHEN** the coordinating session runs an in-place edit, copy, move or redirection to a repository path
- **THEN** the call is put to the human, naming both ways out

#### Scenario: Shell write outside the repository or unlisted shape
- **WHEN** a statement writes to scratch or home paths, or reaches a repository file by a route not on the list
- **THEN** nothing is emitted

#### Scenario: The hook cannot decide
- **WHEN** a dependency is missing or the target cannot be placed
- **THEN** the call proceeds through the normal permission flow

### Requirement: Agent infrastructure names the repository's current coordinate
Agent infrastructure spelling out an `owner/repo` coordinate (read-only allowlist, `gh` recipes, skill config defaults, guard refusal text) SHALL name the current one, because the allowlist matches exact strings. The project board's owner and number SHALL be left alone on a repository transfer, and archived changes SHALL be left as written.

#### Scenario: The repository is transferred
- **WHEN** the repository moves to a new owner
- **THEN** those files name the new coordinate and pre-approved read-only commands still match

#### Scenario: Board or archive coordinates
- **WHEN** infrastructure names the board's owner and number, or an archived change names the old repository
- **THEN** they are left unchanged

### Requirement: A note captured outside the repository has a path onto the board
The repository SHALL provide a command that reads open notes from the external inbox. A note SHALL become an issue only when a PR can close it, it is a bug, or it is a decision worth recording; otherwise it becomes a comment on the hosting issue. Issues SHALL be filed through the workflow script, and a note turned into tracked work SHALL be closed at the source by its stable identifier, not its list position.

#### Scenario: A captured note is work
- **WHEN** a pulled note is closable by a PR, a bug, or a decision
- **THEN** it is filed through the workflow script with Status and Priority and the source note is marked done

#### Scenario: A captured note is a finding
- **WHEN** a pulled note is a finding or design note about tracked work
- **THEN** it becomes a comment on that issue and the source note is marked done

#### Scenario: The same note is pulled twice
- **WHEN** a note was already marked done
- **THEN** a later pull does not return it

### Requirement: Credentials for an external inbox live outside the repository
The inbox command SHALL take credentials and account-specific settings from outside the repository; no client id, secret, token or list identifier SHALL be committed. It SHALL report its own readiness without a real run (dependencies, credential file, token refresh, default list resolution), naming what is missing and where to put it, printing no secret. Missing dependency, absent credential file and rejected API call SHALL each exit non-zero with an actionable sentence, never a stack trace or raw response dump.

#### Scenario: Nothing is configured yet
- **WHEN** the readiness check runs with no credential placed
- **THEN** it exits non-zero naming the missing file, its location and the setup document

#### Scenario: The API rejects a call
- **WHEN** the remote API answers with an error
- **THEN** the command prints the API's error message and exits non-zero

#### Scenario: Readiness reports credentials
- **WHEN** the check reports on credentials and tokens
- **THEN** it reports presence and usability without printing contents

### Requirement: An agent invoked from GitHub can commit what it was asked to fix
The workflow that runs the coding agent on issue or PR mentions SHALL grant write access to repository contents and pull requests (and read access to Actions for CI results), reaching only the invoking PR's branch; `master` SHALL change only through a reviewed PR. It SHALL authenticate through a repository secret and carry no credential in its text.

#### Scenario: A review asks the agent for a fix
- **WHEN** a PR comment mentions the agent
- **THEN** the job holds write access and Actions read access and can commit the fix to the branch under review

#### Scenario: The agent is not a way around review
- **WHEN** the agent has pushed to the PR branch
- **THEN** `master` is unchanged until the PR is reviewed and merged

#### Scenario: The workflow needs a credential
- **WHEN** the workflow authenticates the agent
- **THEN** it reads a repository secret and the file contains no token
