## Context

The behaviour is in `specs/app-navigation/spec.md`. The owner's decision is on PR #516: the app drops the query and opens the screen of the path.

## Decisions

**The query is checked once, before anything reads the address.** `:app/router`'s start decodes `location.search` with `decodeURIComponent`. If that throws, it replaces the address with the path and the fragment, before `put-home-beneath!` copies the address and before `rfe/start!` parses it. A valid query is left alone. *Alternative:* catch the error around `rfe/start!` — by then `put-home-beneath!` has already pushed the malformed address into history.

## Risks / Trade-offs

- [A malformed path rather than query] → not handled; no such address has been seen.
