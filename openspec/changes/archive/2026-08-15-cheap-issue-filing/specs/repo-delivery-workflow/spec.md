## ADDED Requirements

### Requirement: Filing an issue stays within a small share of the API budget

The tracked-delivery workflow SHALL file an issue without consuming a disproportionate share
of the hourly GraphQL budget, so that a batch of backlog work can be filed in one sitting.

Lookups the workflow performs SHALL request only the data the workflow reads. Matching a board
item needs its id, title, type and issue number; resolving a field needs that field's id and
the id of the option being set. Requesting every field value of every board item, or every
option of every field, is not permitted merely because a convenience command offers it.

Reducing the cost SHALL NOT cost behaviour: a same-titled draft item on the board is still
found and converted into the issue rather than duplicated.

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
