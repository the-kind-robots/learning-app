## Context

In-force ADRs: ADR-0007 (this change), alongside ADR-0006 (provisioning) and ADR-0008 (vocab ids) — none superseded. Current state: `sync/start!` runs `db.sync(..., {:live true :retry true})` per online device. The client routes via Reitit controllers whose `:start` already dispatches per-page load effects (`:effect/load-words`, etc.); each page's show action writes its state slice through `:effect/save`.

## Goals / Non-Goals

**Goals:**
- Cost scales with devices actively in use, not all online devices.
- Pull only where freshness is needed (data pages); push on every local write.
- Offline-first: never block render on the network.

**Non-Goals:**
- Real-time updates for two devices open simultaneously — the conscious gap of ADR-0007's baseline; closed later by the `_db_updates` + WebSocket layer (separate ADR).
- Account provisioning (ADR-0006) and conflict de-duplication semantics (ADR-0008 — this change only runs the resolver per pass).

## Decisions

- **One-shot over live.** `db.sync(remote, {live: false})` single pass instead of `{:live true}`. Rationale: removes the held-feed costs; cost follows activity. PouchDB's `retry` applies only to live mode, so a failed pass just ends; the pass never rejects (failure logs and resolves nil) and the next trigger retries. Each pass resolves vocab conflicts before completing, so triggers can fire it freely.
- **Push off the local changes feed, not Nexus write effects.** The sync component listens to `user-db`'s own `.changes({since: "now", live: true})` — IndexedDB events, no network — and runs a throttled pass. Rationale: a hand-listed effect set drifts (it already had a stale `:effect/delete-collection!` name and missed transitive writes); the database is the single chokepoint that cannot miss a write.
- **Throttle, not debounce.** Leading-edge throttle (3s): the first write syncs immediately, a sustained burst (a lesson's reviews) coalesces into periodic passes. Debounce starves under sustained writing — the pass would never fire until the burst ends.
- **Pull on data-page entry; `online` pull unconditional, reload state-gated.** The words, collections and lesson controllers dispatch `:effect/sync-pull` next to their load effect. The `online` listener pulls on any page — the pass also flushes offline writes, so gating it by page would delay push. What is page-gated is the *reload*: after the pass, `:action/reload-page` re-dispatches the effect stored in `:page/load`, which each page's show action sets ( `[:effect/load-words]`, `[:effect/load-collections]`, nil for home/lesson). State carries the target; no page→effect map to maintain.
- **Lesson pulls on entry too, and that is safe.** The lesson route dispatches `:effect/sync-pull` beside its load effect. It cannot disturb the session: the pass is asynchronous (the lesson builds from local data without waiting), and the post-pass `:action/reload-page` reads `:page/load`, which is nil on lesson — so no reload fires. Remote changes land in the local database for what comes next while the running lesson stays as it started. Alternative (excluding lesson entirely) rejected: it skipped a natural sync point for no gain, since the reload gate already prevents mid-session disruption.
- **`navigator.onLine` as a cheap gate** (true ≠ reachable): a false "online" just yields a failed pass that resolves nil; the next trigger retries.
- **No `stop!`.** The sync component lives for the page lifetime; the listener dies with the page. A stop handler would be dead code.

## Risks / Trade-offs

- Two devices open simultaneously on a data page don't update in real time → baseline gap; the `_db_updates` + WebSocket ADR closes it.
- "Used offline → closed → reopened online" is an app-start/foreground pull, not an `online` event (a fresh page already online never observes the transition) → covered by data-page-entry pull, not the listener.
- A pull that writes remote docs locally fires the local changes feed → one throttled echo pass that finds nothing new (replication checkpoints converge); harmless, costs one short round-trip.
- Throttle window too long loses recency, too short hammers the server → 3s placeholder; tune against the battery measurement methodology (tracked separately).

## Migration Plan

Swap `{:live true}` for the trigger wiring in one change; no data migration. Rollback: restore the `{:live true}` call (in git history). Independent of provisioning, so it can ship before or after ADR-0006.

## Open Questions

- Throttle exact value — tune against the battery measurement methodology (tracked separately).
- Whether the `_db_updates` + WebSocket layer obsoletes the `online`-event pull or keeps it as the reconnect fallback.
