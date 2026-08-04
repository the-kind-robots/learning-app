# 0007. Trigger-driven sync (baseline)

- Status: accepted
- Date: 2026-06-08

## Context

Cross-device data = PouchDB↔CouchDB replication. The shipped impl starts continuous live replication (`{:live true :retry true}`) per online device.

Holding a connection is not inherently expensive — event-driven servers carry 100k+ idle sockets (C10k). The real costs here are specific:

- **CouchDB, not TCP.** A continuous `_changes` feed is per-database; with db-per-user, each online device keeps its `userdb` hot (shards, file descriptors, Erlang processes, `max_dbs_open`) plus an nginx upstream conn per feed — heavier than an idle socket. But at hobby scale it likely holds; the "wall" was overstated.
- **Mobile battery.** A held feed keeps the radio awake; every keepalive/change wakes it. This is the stronger driver — but it is **unmeasured** (battery-measurement TODO); treat it as a hypothesis, not a fact.

Needed freshness is narrow: remote data matters only when a screen shows synced data — words, lesson, collections.

## Decision

Replace permanent live replication with trigger-driven one-shot passes. Each pass is `db.sync(remote, {live: false})` — completes and closes (a `_changes`+bulk burst, no held network feed; PouchDB's `retry` applies only to live mode, so a failed pass just ends). Two sides:

- **Push** (local writes → server): driven by the **local** PouchDB `.changes` feed on `user-db` — an IndexedDB event stream, not a network feed — throttled, owned by the sync component. Any local write to a synced doc schedules a throttled push. The db's own change events are the single chokepoint (not a hand-listed set of Nexus write effects).
- **Pull** (freshness for this device): a one-shot pass on entering a data page (words / collections) and on the `online` event. After the pass, the current page reloads its store slice via a `:page/load` action, so fresh data shows.

Offline-first: entering a data page renders local data immediately and pulls in the background, re-rendering on arrival; render never blocks on the network. `navigator.onLine` is a cheap gate — it only reliably says "offline"; a never-rejecting pass plus the next trigger cover its false "online".

Every data screen pulls on entry — words, collections, and lesson. Lesson is safe to include because the pass never touches a running lesson: `:page/load` is nil there, so the post-pass reload is a no-op. The pass still lands remote changes in the local database for whatever comes next, while the lesson stays exactly as it started.

## Consequences

- Sync cost scales with devices *active now*, not all online; no permanently held feeds.
- Push uses a *local* changes listener (cheap, no network); pull is bursty short passes — better for battery (radio sleeps between), pending measurement.
- "Used offline → closed → reopened online" is an app-start/foreground pull (a fresh page already online never sees an `online` transition); offline writes flush on the next `online` event or pull.
- **Conscious gap: no real-time while two devices are open at once.** There is no notification, so device B sees A's change only on its next pull (page entry / online). This is the baseline.
- Closing that gap is a **future** decision: push-notified sync via CouchDB `_db_updates` (one global fan-in feed) + a WebSocket fan-out — a separate ADR. Web Push is rejected for it: built for user-visible notifications (`userVisibleOnly`), not silent data sync, and iOS-fragile.
- Rejected — sync on every user action (most actions touch no synced data; throttling collapses it back to push-on-write + periodic); permanent live feed (the held-feed costs above).
- This ADR is the trigger **baseline**; the `_db_updates` + WebSocket layer will sit on top as the real-time enhancement, with these triggers as the fallback.
