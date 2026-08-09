# Proposal: mint-invite-cli

## Why

Minting an invite (ADR-0006 phase 1) exists only as a REPL call, and the packaged app has no REPL — so for a deployed instance the operation degrades to hand-written SQL against the live database. GitHub issue: #262.

## What Changes

- The jar entry point takes arguments: no arguments serves as before; `mint-invite` mints a single-use grant and prints the invite URL, then exits without starting the server.
- The invite URL points at the configured public origin (`LEARNING_APP__PUBLIC_URL`, defaulting to the canonical one), with the token in the URL fragment.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `identity-provisioning`: an operator SHALL be able to mint an invite from the packaged app, without a REPL or manual SQL.

## Impact

- `src/backend/core.clj` — argument dispatcher in `-main`, `mint-invite!`, URL helpers.
- `test/backend/core_test.clj` — dispatcher and URL tests.
- No schema, data-model, or client changes.
