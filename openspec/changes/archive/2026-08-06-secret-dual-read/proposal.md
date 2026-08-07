# Finish LEARNING_APP__ env naming — phase 2a, secret dual read

## Why

`LEARNING_APP_DB_AUTH_SECRET` is the sharp rename in #217: the name is exported by the systemd unit (ships in the deb) and read by the jar — two deploy artifacts. A naive rename opens a window where the packaged app throws at boot. The rename must be transitional: this release makes the jar read both names, so the later unit rename (phase 2b) cannot brick a boot in either deploy order.

## What Changes

- `src/backend/core.clj`: the secret resolves through a pure function over an env map — prefers `LEARNING_APP__DB_AUTH_SECRET`, falls back to `LEARNING_APP_DB_AUTH_SECRET`, then the checkout well-known value; the packaged boot-refusal message names the new variable.
- The old-name fallback is transition scaffolding, removed in phase 2c after the deb rename deploys; #217 tracks the removal.
- **Out of scope**: the unit's `Environment=` / ExecStart export and `LEARNING_APP_DB_AUTH_SECRET_PATH` — deb territory, phase 2b. The jar-side dual read alone makes old-unit + new-jar work: the old unit exports the old name, the jar falls back to it.

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**: `backend-configuration` — the naming-convention requirement gains the dual-read transition scenarios for the secret.

## Impact

`src/backend/core.clj`, `test/backend/core_test.clj`. No infra files. Behavior is identical for any environment that sets either name; only a packaged boot with neither name changes its error text.
