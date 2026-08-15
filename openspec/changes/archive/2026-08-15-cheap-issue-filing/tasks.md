## 1. Measure the baseline

- [x] 1.1 Record the GraphQL cost of one full `start_issue_flow.sh` run, read from
      `gh api rate_limit` before and after — 323 points
- [x] 1.2 Record the cost of each call the script makes — `item-list` 203,
      `field-list` 102, `project view` 2, `issue list --search` 1, `label list` 1

## 2. Ask for what the script reads

- [x] 2.1 Replace `gh project item-list` with a GraphQL query for id, title, type and issue
      number, paginated, shaped so the existing jq matchers keep working
- [x] 2.2 Replace `gh project field-list` with a GraphQL query for single-select field ids and
      option ids only — the same query now also returns the project id
- [x] 2.3 Keep `--no-reuse-existing` skipping the board lookup entirely — untouched
- [x] 2.4 Drop archived items from the walk, matching what the CLI listing returned

## 3. Make the cost visible

- [x] 3.1 Report the run's GraphQL cost when the script finishes

## 4. Verify

- [x] 4.1 Re-measure a full run: 19 points against the 323-point baseline; the board walk
      alone went from 203 to 2
- [x] 4.2 File a real issue end to end — #332 landed on the board with Status Backlog and
      Priority Minor, confirmed by querying the item's field values
- [x] 4.3 Re-run with the same title — the existing #332 and its item id came back, no
      duplicate, 10 points
- [x] 4.4 Compare the walk against `gh project item-list` — 156 items each, identical except
      #288, where the query returns the issue's current title and the CLI returns the stale
      one stored on the board. Draft items appear with their titles, so draft conversion still
      has its input; converting a live draft was not exercised, as that would alter the board.
- [ ] 4.5 Run shellcheck over the changed script — not installed in this environment;
      `bash -n` passes
