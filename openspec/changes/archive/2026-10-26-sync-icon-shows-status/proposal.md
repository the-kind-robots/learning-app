## Why

The sync control is a grey icon at the far end of the bar: it opens the menu but says nothing about the sync itself (#677). Whether this device is in touch with the others, and whether data is moving right now, is not visible anywhere.

## What Changes

- The control moves beside the word mark, shows on the home page with or without an account, and shows the state of the sync by colour (design: board B and Placement, option 1, of the canvas https://claude.ai/artifact/DtLmgLpz6JDQ48d9pAbbCx).

What it must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `push-sync`: «The device-sync control is on the home page only» — where the control stands and what it shows; «A device without an account asks to turn the sync on» — the menu item and its form (design: variant F of https://claude.ai/artifact/No4pp96NvgsAk1JbVSWMNq, without the pre-written text).

## Decisions

- «Online» is the browser's online state, which already pauses and resumes the passes. A failed pass does not fade the icon.
- No flicker while a pass runs: the owner chose colour alone.
- The form sends the request to the backend of #689 by its contract (PR #687 comments); #687 merges after #689.
- A granted request opens the account in place, found within a minute while the page is shown: the account port starts the adapter again and hands the new account to its listeners, so no reload happens under the learner. The start does not wait on the network.
- Offline with an account is orange, not faded green (owner, review of PR #687).
