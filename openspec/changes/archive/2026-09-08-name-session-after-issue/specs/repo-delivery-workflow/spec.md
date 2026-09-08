## ADDED Requirements

### Requirement: The session is named after the issue it lands on

When the start-work script ends with an issue number, it SHALL rename the Claude Code session
it runs in to `<number> <issue title>`, so the session list says which issue each session is
on. The title SHALL be taken from the issue as GitHub holds it, whether the issue was just
created, reused from the board, or passed in by number. Whitespace in the name SHALL be
collapsed and control characters removed.

Outside a Claude Code session — no messaging socket in the environment, a socket path that
does not exist, or no interpreter to speak to it — the rename SHALL be a silent no-op with a
zero exit.

A failed rename SHALL NOT fail the flow: the issue, the board item and the branch are the
work; the name is a convenience. The script SHALL report the name it set on success and
nothing on a skip.

The mechanism is an undocumented harness internal, measured on Claude Code 2.1.263, and the
script SHALL say so in its header so a later breakage is recognised as such.

#### Scenario: The flow ends with an issue inside a Claude session

- **WHEN** `start_issue_flow.sh` finishes with an issue number in a Claude Code session
- **THEN** the session is renamed to `<number> <title>` and the script prints the name

#### Scenario: The flow runs outside a Claude session

- **WHEN** the script runs with no session socket in its environment, or the socket is gone
- **THEN** nothing is sent, nothing is printed about the name, and the flow still exits zero

#### Scenario: The rename fails

- **WHEN** the socket refuses the connection or the interpreter is missing
- **THEN** the flow still reports the issue, item and branch and exits zero
