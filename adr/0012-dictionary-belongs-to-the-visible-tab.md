# 0012. The dictionary belongs to the visible tab

- Status: accepted
- Date: 2026-08-16
- Supersedes: ADR-0002 (in part)

## Context

ADR-0002 put the dictionary in a dedicated Web Worker because `SyncAccessHandle` is
Worker-only outside Chromium. That reasoning still holds; what it left unsaid is that
`opfs-sahpool` takes exclusive access handles, so one worker per browsing context means one
candidate holder per tab competing for a pool only one of them can have. The implementation
made that explicit with a Web Lock taken `ifAvailable: true`, so the second tab lost
immediately and had no dictionary at all (#351): observed with five tabs open on a phone,
the OPFS database fully present, suggestions returning only after the extra tabs were
closed.

Two measurements decide what replaces it.

A `SharedWorker` cannot be the owner. On Chrome 144, inside a `SharedWorkerGlobalScope`,
`FileSystemFileHandle.prototype.createSyncAccessHandle` is `undefined`, so
`installOpfsSAHPoolVfs` fails with `Missing required OPFS APIs`; `Worker` is `undefined`
there too, so it cannot spawn a dedicated worker to hold the pool for it. A `ServiceWorker`
is killed and restarted at the browser's discretion and would drop the pool mid-session.
The pool is dedicated-worker territory, one holder at a time, and the application cannot
negotiate that.

A backgrounded tab cannot be the owner either. Android freezes a backgrounded tab whole —
worker and message delivery together — about a minute after it leaves the screen. Measured
with two tabs and one query every 10 s while the tab holding the database sat in the
background: 12 ms, 14, 17, 22, 31, 31, and from 60 s on nothing at all, 14 consecutive
timeouts until it was brought back. The failure is silent: no error, no state change, the
promise never settles. So any arrangement that leaves the database behind a background tab
— sharing it out from there, electing a long-lived holder — builds a dictionary that stops
answering after a minute.

## Decision

The dictionary belongs to the tab being typed into. A tab takes the
`sqlite-opfs-sahpool` lock when it comes to the foreground, opens the database and serves
only itself; when it leaves the foreground it closes the database, pauses the pool and
releases the lock. One stretch of holding that lock, from grant to release, is a *turn*; a
tab has no database outside its turns. A tab waiting for one answers the queries asked of
it with no completions and keeps nothing: the answer is what it has at that instant, and
being asked again takes another keystroke.

Not queueing them is a choice, and the measurements are what make it a cheap one. The
windows a query can fall into are ~14 ms to take the lock back from the tab in front and
~94 ms for a tab's first turn, against a 100 ms trailing suggest debounce and a keystroke
every 120-180 ms — so on a warm start the turn has begun before the query is sent. The one
window a user can actually see is a cold start, where the dictionary is still being
downloaded and every keystroke until it lands gets an empty list. Holding queries buys that
case and costs a mechanism to do it; the empty answer is accepted for now, and revisiting
it is a separate decision.

Foreground means on screen **and** holding the keyboard: `visibilityState === "visible" &&
hasFocus()`. Visibility alone does not identify the tab being used — two tabs can be
visible at the same time in split screen or side-by-side windows, and `hidden` is false for
both, so the database would go to whichever took the lock first rather than to the one the
user is working in. The page computes the answer and reports it to its worker as a single
boolean; the worker is never told how it was reached.

There is no communication between tabs at all — no shared owner, no channel, no queries
crossing a tab boundary — which is what makes the freeze irrelevant: the tab holding the
database is by construction one the system is not about to freeze.

`visibilitychange` and `blur` fire the moment a tab stops being the one in front, and the
freeze lands 50-60 s later, so the handover has three orders of magnitude more time than it
needs. A tab killed
outright never runs its handler, and the browser releases its lock instead.

Between turns a tab keeps its pool installed and paused rather than tearing it down.
Measured: taking it back costs `unpauseVfs` 3-5 ms and opening the database 1-2 ms, against
`installOpfsSAHPoolVfs` 13-27 ms plus a manifest fetch to build it again; giving it back
costs 1 ms.

The SQLite engine is loaded on a tab's first turn rather than at app boot, so a tab that
never comes to the front never pays for it. This is the one part of ADR-0002 that no longer
holds — its consequence "WASM cold-start latency is off the critical path: the Worker
initialises in the background on app boot". Everything else in ADR-0002 stands: OPFS
`SyncAccessHandle` is Worker-only outside Chromium, so the engine still runs in a Dedicated
Worker and never on the main thread. ADR-0002 did not decide which context owns the
database, and nothing here overturns a decision it did not make.

## Consequences

- Whoever is typing has the dictionary, and it answers in single-tab time because it is a
  single-tab arrangement.
- Two tabs visible at once get the dictionary in whichever one has the keyboard, and it
  moves as the user clicks between them. When the browser window itself is behind something
  else no tab holds it — there is nobody to ask, and the first tab to come back takes it in
  ~14 ms.
- A tab that has not had a turn yet has no dictionary and says so by answering with no
  completions, which is indistinguishable from a prefix that has none. Telling the two
  apart is what `:dictionary/ready?` is on the port for, and describing it to the user is
  #312.
- Turns change hands more often than they would on visibility alone: a click in the address
  bar or in devtools is a `blur`, so it costs a round trip. ~14 ms per switch once a tab has
  had a turn, ~94 ms for its first. Both are far below the ~100 ms suggest debounce, and the
  work happens in a worker that is not the one the user is waiting on.
- The file is still downloaded and imported once per origin, because the OPFS pool outlives
  every turn.
- Nothing coordinates between tabs, so nothing has to be designed for concurrent clients —
  but equally, anything the dictionary gains later that must be shared cannot lean on the
  holder, because there is no long-lived one.
