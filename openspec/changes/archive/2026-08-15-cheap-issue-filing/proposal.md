## Why

Filing one issue through `start_issue_flow.sh` costs about 323 points of the 5000-point
hourly GraphQL budget, so a batch of backlog work exhausts the budget after eleven issues and
then waits an hour. Measured today while filing twenty-five issues: the run stopped twice.

Measured per call, by reading `rate_limit` before and after:

| call | points |
|---|---|
| `gh project item-list --limit 200` | 203 |
| `gh project field-list` | 102 |
| `gh project view` | 2 |
| `gh issue list --search` | 1 |

The two expensive ones are expensive for the same reason: the GitHub CLI asks for far more
than the script reads. `item-list` pulls every field value of every board item when the script
only matches on title, type and number; `field-list` pulls every option of every field when
the script needs the ids of two single-select fields.

The board walk itself has to stay — it is what lets a same-titled **draft** item be converted
into the issue instead of being duplicated, and the board holds such drafts today.

## What Changes

- The board walk and the field lookup ask for the fields the script actually reads, through
  direct GraphQL queries, instead of the CLI's exhaustive ones.
- The script reports what the run cost, so a regression is visible immediately rather than
  inferred an hour later from a rate-limit error.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-delivery-workflow`: filing an issue gets a cost ceiling — the workflow must not
  consume a disproportionate share of the hourly API budget per issue, and must keep reusing
  a same-titled draft item while doing so.

## Impact

- `.skills/gh-project-workflow/scripts/start_issue_flow.sh`
- Anything that shells out to it: `repo-task-delivery`, `gh-project-workflow`, and agents
  filing tracked work.
- No effect on the GitHub Project schema, the issue bodies, or the board itself.
