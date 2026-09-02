## ADDED Requirements

### Requirement: Priority is set where the board reads it

Tracked work SHALL carry a Priority drawn from the organization's native issue field, whose
options are `Urgent`, `High`, `Medium` and `Low`. The board shows that field as a column and
SHALL NOT be written through: `updateProjectV2ItemFieldValue` refuses a column backed by an
issue field, and the project's own `Priority` field carries no options, so a project-field
lookup fails with a message that reads like a broken script rather than a moved field. The
start-work workflow SHALL therefore set Priority on the issue with `setIssueFieldValue`,
while Status, Area and Size stay ordinary project fields.

The retired scale — `Blocker`, `Critical`, `Major`, `Minor`, `Trivial` — SHALL be refused by
name, and the refusal SHALL state the replacement value, so a caller carrying the old
vocabulary is corrected rather than left guessing.

Reading Priority back SHALL use the `ProjectV2ItemIssueFieldValue` fragment. A plain
single-select query returns nothing for it, which is indistinguishable from an unset value
and is not the same thing.

#### Scenario: An issue is filed with a current priority

- **WHEN** the start-work workflow files an issue with a priority from the current scale
- **THEN** the value is written to the issue's native Priority field
- **AND** reading the board item back through the issue-field fragment reports that value

#### Scenario: An issue is filed with a retired priority

- **WHEN** the start-work workflow is given one of the retired priority values
- **THEN** the run fails before creating anything further
- **AND** the message names the value that replaced it

### Requirement: The board of record is named consistently

Every file that tells an agent where work is tracked SHALL name the same board: the
organization project `Learning app`, owner `the-kind-robots`, number `11`. That includes the
workflow configuration, the delivery documentation loaded at session start, the workflow
skills, and the text a delivery guard prints when it refuses a command. A guard that refuses
a command while naming a board that is no longer the board of record sends the agent to the
wrong place, so the guards' refusal text SHALL be kept current even though it is only text.

#### Scenario: A guard refuses a raw issue creation

- **WHEN** a delivery guard refuses a command and prints the workflow invocation to use
  instead
- **THEN** that invocation names the current board and a priority from the current scale

## MODIFIED Requirements

### Requirement: Filing an issue stays within a small share of the API budget

The tracked-delivery workflow SHALL file an issue without consuming a disproportionate share
of the hourly GraphQL budget, so that a batch of backlog work can be filed in one sitting.

Lookups the workflow performs SHALL request only the data the workflow reads. Matching a board
item needs its id, title, type and issue number; requesting every field value of every board
item is not permitted merely because a convenience command offers it.

Resolving a field is exempt from that rule when the CLI resolves the field and its option by
name, because the workflow then names what it wants instead of fetching a catalogue to search.
Hand-rolled id resolution SHALL NOT be kept once the CLI can do it, since a lookup layer that
duplicates the tool is cost the workflow pays twice.

Reducing the cost SHALL NOT cost behaviour: a same-titled draft item on the board is still
found and converted into the issue rather than duplicated, and that dedupe has no equivalent
in the CLI, so it stays.

#### Scenario: Filing twenty issues in a row

- **WHEN** twenty issues are filed one after another through the start-work script
- **THEN** every one of them is created, placed on the board, and given Status and Priority
- **AND** the hourly GraphQL budget is not exhausted

#### Scenario: A same-titled draft is still reused

- **WHEN** the board holds a draft item whose title matches the issue being filed
- **THEN** that draft is converted into the issue
- **AND** no second board item is created

#### Scenario: The cost is visible

- **WHEN** the script finishes a run
- **THEN** it reports what that run cost against the GraphQL budget
