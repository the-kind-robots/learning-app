## Why

The client's data export and import (`adapters.learner.backup`) were never reachable from a screen, and the learner capability no longer offers them. Their requirements describe code that is gone.

## Changes

- The client no longer exports or imports a payload of `user-db` documents.

## Capabilities

### Modified Capabilities
- `account-backup`: the export and import requirements are removed; provisioning by first-sync push and client-side key sharing stay.

## Decisions

- Remove the code with its requirements — over keeping an unused entry point to maintain.
