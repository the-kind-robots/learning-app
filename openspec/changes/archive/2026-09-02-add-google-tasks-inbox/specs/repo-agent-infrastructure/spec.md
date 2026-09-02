## ADDED Requirements

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
