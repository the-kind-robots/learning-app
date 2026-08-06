# The :build alias resolves on a clean cache

## Why

`deps.edn`'s `:build` alias pins tools.build `:git/tag "v0.10.14"` with `:git/sha "3a3c177"` — the tag object sha of v0.10.13 (#227, found by the #214 container run). Machines with a warm gitlibs cache resolve by sha and work; a clean cache fails the build.

## What changes

Align the sha with the tag: v0.10.14's commit is `1176afd38dc39cb1880bd990d85718d4a61e194d`, so the coordinate becomes `:git/sha "1176afd"`. Audit the other tag+sha pair in the repo (cognitect-labs/test-runner v0.5.1 / `dfb30dd` in `tools/dictionary/deps.edn`) — it is consistent, no change.

## Impact

`deps.edn` only. Verified by `clojure -T:build uber` against a fresh `:mvn/local-repo` and a fresh `GITLIBS`.
