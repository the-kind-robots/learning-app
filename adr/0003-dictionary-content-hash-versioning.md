# 0003. Dictionary file versioned by content hash

- Status: accepted
- Date: 2026-05-08

## Context

The dictionary SQLite file must be versioned so the server, Service Worker cache, and OPFS Worker agree on the current file without a separate version-pointer document. The build pipeline already fingerprints JSONL outputs via `emit/sha256`.

## Decision

The dictionary filename includes the first 12 hex characters of the SHA-256 of the file content: `dict.{hash12}.sqlite`. The file is written to a temp path, hashed, then renamed. The hash is recorded in the manifest so all consumers reference the same file by name.

## Consequences

- Same content always yields the same URL — deterministic builds do not generate new files unnecessarily.
- Cache-busting is automatic: any content change produces a new filename.
- HTTP caching and Service Worker caching work correctly with no additional version-pointer logic.
- Old files must be explicitly deleted after a successful version swap; the Worker is responsible for this cleanup.
- The manifest is the authoritative source for the current hash; server and Worker must read from the same manifest.
