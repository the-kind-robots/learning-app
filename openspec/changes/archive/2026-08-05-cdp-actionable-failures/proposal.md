# CDP tooling failures name their own fix

## Why

With Chrome down, `doctor` reported five "ok" lines (it checks interop plumbing, not the endpoint), and every eval spun 40 interop-priced retries — 1.5–2 minutes of silence — before dying with a dead-end message. An agent read "all ok", concluded the tooling could not start Chrome, and launched chrome.exe by hand, hitting the debug-port/profile trap `start-local` exists to solve. The information was in three places (SKILL.md, usage, dispatcher) and all three were skippable; the only channel that cannot be skipped is the output of the failure itself.

## What changes

- `doctor` probes the endpoint and, when dead, prints the fix: `start-local`.
- Page-resolution paths preflight the endpoint once and fail in seconds, naming the fix; the "no matching tab" dead-end names it too.
- The skill description enumerates the commands, so `start-local` is visible without opening anything.
- AGENTS.md gains the trigger line: Chrome down → `start-local`, never launch chrome.exe by hand.

## Impact

Modified: `repo-browser-debugging`. Files: `learning_app_cdp.sh`, `SKILL.md`, `AGENTS.md`.
