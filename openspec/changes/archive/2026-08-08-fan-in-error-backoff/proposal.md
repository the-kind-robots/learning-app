# Proposal: fan-in-error-backoff

## Why

The push fan-in loop retries every failed `_db_updates` turn after a flat 5s. That is right when CouchDB is unreachable, and wrong when CouchDB answers 401/403: a steady stream of failed authentications can trip the server's lockout, turning one misconfiguration into an outage of every admin call. GitHub issue: #253.

## What Changes

- `db/db-updates` includes the response `:status` in the ex-data it raises, so callers can tell a refusal from an outage.
- The fan-in loop backs off by error type: 401/403 waits minutes and logs `:level :error`; everything else keeps the 5s retry and the warning.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `push-sync`: the fan-in loop SHALL back off by failure type — auth refusals wait minutes and log an error, other failures keep the quick retry.

## Impact

- `src/backend/push.clj` — backoff decision by ex-data status.
- `lib/db/src/db.cljc` — `db-updates` ex-data carries `:status`.
- `test/backend/push_test.clj` — backoff decision and end-to-end ex-data shape.
