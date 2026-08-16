## ADDED Requirements

### Requirement: An agent invoked from GitHub can commit what it was asked to fix

The repository SHALL keep a workflow that runs the coding agent when it is mentioned on an issue
or a pull request, and that workflow SHALL grant the agent write access to the repository contents
and to pull requests, because the work it is invoked for is a change to the branch under review.
Read-only access SHALL NOT be the granted level: it turns a request to fix something into a
comment describing the fix, which a person then has to apply by hand.

The write access SHALL reach the branch of the pull request that invoked the agent and no further;
`master` stays protected and is changed only through a reviewed pull request, as it is for a human.

The workflow SHALL authenticate through a repository secret and SHALL NOT carry a credential in
its own text.

#### Scenario: A review asks the agent for a fix

- **WHEN** a comment on a pull request mentions the agent
- **THEN** the job it starts holds write access to the repository contents and to pull requests
- **AND** it can commit the fix to the branch under review instead of only describing it

#### Scenario: The agent reads why the branch is failing

- **WHEN** the agent is invoked on a pull request whose checks have run
- **THEN** the job holds read access to Actions
- **AND** the CI results are part of what it works from

#### Scenario: The agent is not a way around review

- **WHEN** the agent has pushed to the branch of the pull request that invoked it
- **THEN** `master` is unchanged
- **AND** the work reaches `master` only by that pull request being reviewed and merged

#### Scenario: The workflow needs a credential

- **WHEN** the workflow authenticates the agent
- **THEN** it reads a repository secret
- **AND** the workflow file itself contains no token
