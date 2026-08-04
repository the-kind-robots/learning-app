# Derive the service worker version at build time

## Why

`SW_VERSION` was computed per request by hashing `resources/public` off the filesystem. A packaged run has no such directory — a jar is a flat list of entries — so the walk found nothing and the digest degraded to the sha256 of empty input, the same string on every release. Browsers saw an unchanged worker and kept serving a cached build indefinitely.

It failed silently, and development masked it: running from source, the directory is there and the mechanism works.

## What changes

The version is computed where the assets are still files — during `clojure -T:build uber`, right after resources are copied — and written into the artifact as a single named resource. The server reads that name back, which is an operation a jar does serve. A source checkout keeps computing from disk, so a rebuild during development is still picked up.

## Impact

- `build.clj` stamps `sw-version` into the uberjar.
- `core.clj` reads it, falling back to the on-disk digest.
- One test guards the only thing the two sides share: the name.
