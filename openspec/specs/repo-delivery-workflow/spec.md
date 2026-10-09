# repo-delivery-workflow Specification

## Purpose
Define what tracked delivery means in this repository: which work goes through an issue, a branch and a pull request, and when it also carries an OpenSpec change. Process rules live in `AGENTS.md`; this spec holds only the verifiable contract.
## Requirements
### Requirement: Likely repository changes default to tracked delivery
Work likely to end in a committed edit SHALL be tracked: an issue on the board, a branch created from that issue, and a pull request, without exception. An OpenSpec change SHALL be required only when the work alters behaviour someone could check afterwards without reading the diff, and SHALL NOT be created otherwise. Repository process work (agent rules, hooks, skills, scripts, CI, delivery docs) SHALL NOT need a change, and a requirement SHALL NOT be invented to satisfy `openspec validate`. The test and process rules SHALL live in `AGENTS.md`, not in a spec.

#### Scenario: The work changes what the product does
- **WHEN** the work alters verifiable product behaviour
- **THEN** it carries an OpenSpec change with a delta, archived on the branch before the pull request

#### Scenario: The work changes how the repository is worked
- **WHEN** the work touches only process files
- **THEN** it carries no OpenSpec change but still carries its issue, issue branch and pull request

### Requirement: Task boundaries reset after delivery closeout
Repo work that starts after issue/PR closeout SHALL be a new tracked task unless it is only closeout tail work.

#### Scenario: New scope after merge
- **WHEN** the previous task is merged or closed and the user starts a new non-trivial scope
- **THEN** a new issue is started rather than continuing the old issue or branch

### Requirement: Optional GitHub Project fields do not block start-work
The start-work workflow SHALL continue when an optional single-select field is absent on the project.

#### Scenario: Project has Status but no Category
- **WHEN** start-work runs against a project without `Category`
- **THEN** the issue and project item are created and the missing field is skipped with a warning

### Requirement: Visual UI work favors perceptual stability
Unnecessary visible UI jumps or geometry shifts SHALL count as regressions, and verification SHALL use real browser evidence (measured geometry, traces) rather than DOM shape alone.

#### Scenario: A layout or transition change is verified
- **WHEN** a task changes interactive layout, swapping, focus flow or panel geometry
- **THEN** browser-level evidence shows the screen does not visibly jump

### Requirement: Repo-owned verification tooling is repaired before fallback
Failures in repo-owned verification tooling SHALL be repaired as part of the tracked work before alternate stacks are used; a fallback SHALL be used only when the user allows it or repair is stated to be blocked.

#### Scenario: Preferred CDP workflow is flaky
- **WHEN** the CDP workflow attaches to the wrong target, loses route state or races setup
- **THEN** the repo-owned tooling is repaired rather than bypassed with alternate browser tooling

### Requirement: A branch for tracked work is linked to its issue
Branches for tracked work SHALL be created with `gh issue develop <number>`, including when work is isolated in a worktree, and SHALL NOT come from `git checkout -b`, `git switch -c` or `git worktree add -b`. The coordinating session SHALL stay in the main checkout and delegate repository edits to the `executor` agent (`.claude/agents/executor.md`), whose definition carries the worktree isolation and the branch recipe; `AGENTS.md` SHALL point at it rather than restate them. Delegated edits from a background coordinator SHALL be launched with worktree isolation.

#### Scenario: Work is isolated in a worktree
- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on it, keeping the development link

#### Scenario: A branch has no issue
- **WHEN** a branch exists that no issue points to
- **THEN** it is treated as untracked work: an issue is created for it or the branch is removed

#### Scenario: The coordinating session needs an edit
- **WHEN** a coordinating session needs a repository edit
- **THEN** it delegates to the `executor` agent and stays in the main checkout

### Requirement: Delivery ends with cleanup
Closeout SHALL delete the delivered branch locally and on the remote and remove its worktree.

#### Scenario: A pull request is merged
- **WHEN** the pull request is merged
- **THEN** the branch is gone from both sides and no worktree remains

### Requirement: Merge order is machine-enforced
A PR declaring `Depends-on: #N` SHALL NOT be mergeable while any referenced issue or PR is open or missing; enforcement SHALL come from branch protection.

#### Scenario: The blocker is open
- **WHEN** a PR body contains `Depends-on: #N` and N is open
- **THEN** the required check fails and merge is disabled

#### Scenario: The blocker lands
- **WHEN** a push to master closes N
- **THEN** the dependent's check re-runs and turns green

### Requirement: A required check reports on every pull request
Every PR SHALL receive a pass, fail or skipped verdict from each required check regardless of touched paths.

#### Scenario: A PR outside the tested paths
- **WHEN** a PR touches no path the heavy job cares about
- **THEN** the check reports skipped and branch protection is satisfied

#### Scenario: A PR inside the tested paths
- **WHEN** a PR touches the application or its build inputs
- **THEN** the full suite runs and gates the merge

### Requirement: The delivery rules are present in every session
The delivery flow, its entrypoint skill and its exceptions SHALL be stated once, in `AGENTS.md` (imported by `CLAUDE.md`), and SHALL NOT be duplicated in an unconditional rules file.

#### Scenario: A session begins
- **WHEN** an agent session starts in the main checkout or a worktree
- **THEN** the flow and entrypoint are already in context, and appear in no unconditional rules file

### Requirement: Commands that bypass tracked delivery are refused
The repository SHALL deny raw issue creation and deny a pull request from a branch carrying no issue number, each refusal naming the command to use instead. The guard SHALL judge only invocations that can create work: a statement with a standalone help flag (whole word) SHALL pass untouched, tested once ahead of per-command dispatch. Manual branch creation (`git checkout -b`, `git switch -c`) SHALL require approval via a permission rule in `.claude/settings.json` taking precedence over the blanket `git` allow. A pull request SHALL be judged on the branch the command names (long or short flag, separated or joined, fork prefix stripped), else on the current branch; a named branch without an issue number SHALL still be refused. `DELIVERY_GUARD=off` SHALL downgrade a refusal to an approval prompt and only on the owner's direct request.

#### Scenario: Issue created by hand
- **WHEN** an agent runs a raw issue-creation command
- **THEN** it is denied and the refusal names the workflow script

#### Scenario: A command's documentation is read
- **WHEN** a statement carries a standalone help flag
- **THEN** it passes untouched

#### Scenario: Branch created by hand
- **WHEN** an agent runs `git checkout -b` or `git switch -c`
- **THEN** the permission rule asks for approval

#### Scenario: Pull request from an untracked branch
- **WHEN** a pull request is opened from, or names, a branch with no issue number
- **THEN** the call is denied

#### Scenario: Pull request names an issue branch
- **WHEN** the command names an issue branch while the working directory is on the default branch
- **THEN** the call is allowed

### Requirement: Filing an issue stays within a small share of the API budget
Issue filing SHALL request only the data it reads (board item id, title, type, issue number), SHALL resolve fields by name through the CLI instead of hand-rolled id lookups, SHALL keep reusing a same-titled draft item instead of duplicating it, and SHALL report the run's GraphQL cost.

#### Scenario: Filing twenty issues in a row
- **WHEN** twenty issues are filed through the start-work script
- **THEN** each is created, placed on the board with Status and Priority, and the hourly GraphQL budget is not exhausted

#### Scenario: A same-titled draft is reused
- **WHEN** the board holds a draft with the same title
- **THEN** it is converted into the issue and no second item is created

### Requirement: A refusal stops the work and is reported
A refusal from a delivery guard or workspace isolation SHALL stop the work and be reported to the owner with the action attempted, what was refused and its verbatim text. It SHALL NOT be circumvented: no commit assembled in a side worktree and pushed to the branch ref, no rewording past a static check, no `DELIVERY_GUARD=off` on the agent's own judgement. An unguarded route SHALL NOT be used to do what was just refused.

#### Scenario: An agent hits a refusal
- **WHEN** a guard or isolation refuses a command or edit
- **THEN** the agent stops, reports action and verbatim refusal, and does not retry by another route

### Requirement: Priority is set where the board reads it
Priority SHALL be drawn from the organization's native issue field (`Urgent`, `High`, `Medium`, `Low`) and set with `setIssueFieldValue`, since `updateProjectV2ItemFieldValue` refuses a column backed by an issue field. Retired values (`Blocker`, `Critical`, `Major`, `Minor`, `Trivial`) SHALL be refused by name with the replacement stated. Reading it back SHALL use the `ProjectV2ItemIssueFieldValue` fragment.

#### Scenario: Current priority
- **WHEN** an issue is filed with a current priority
- **THEN** the native field is written and the issue-field fragment reads it back

#### Scenario: Retired priority
- **WHEN** a retired priority is given
- **THEN** the run fails before creating anything further and names the replacement

### Requirement: The board of record is named consistently
Every file telling an agent where work is tracked, including guard refusal text, SHALL name the same board: organization project `Learning app`, owner `the-kind-robots`, number `11`.

#### Scenario: A guard refuses a raw issue creation
- **WHEN** a guard prints the workflow invocation to use instead
- **THEN** it names the current board and a priority from the current scale
