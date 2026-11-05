## Why

Moving between screens, the big bottom button and the sync icon move when they need not (#767). On desktop «НАЧАТЬ УРОК» is 640 px wide and 32 px off the bottom on home, 320 px and 16 px on the words list, and the lesson's main button 568 px and 16 px: each screen set the footer's width and padding its own way. The sync icon shows on home only, so it appears and disappears beside the word mark.

## Changes

- The footer sets one width and one bottom gap for the main button; no screen overrides them. The lesson's column (#751) is the measure.
- The sync icon stands beside the word mark on every screen once the learner's data is loaded.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: the main button keeps one place across screens — [delta](specs/app-navigation/spec.md).
- `push-sync`: the sync control is on every screen — [delta](specs/push-sync/spec.md).

## Decisions

- The lesson's 568 px column is the width every screen takes: it was agreed in #751, and home's 640 px and the words list's 320 px had no reason of their own.
