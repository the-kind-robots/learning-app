## Why

The browser offers installing right after the page's `load`, and the app listened only once the learner's data had opened, so the offer was lost and the install button never showed (#814; 3 of 3 reloads on desktop Chrome 156).

## Changes

- The app listens for the browser's install offer as it starts, before it opens the dictionary and the learner's data.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: the shell bar offers installing whenever the browser does — [delta](specs/app-navigation/spec.md).

## Decisions

- Listen in `main/init` before its first `await`, not hold the event in the adapter from module load: the state the listener writes exists there already, and the first render reads it.
- The notification Chrome shows while it installs the app on Android keeps Chrome's icon: Chrome posts it with its own small icon, and a page cannot change it.
