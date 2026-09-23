# Design

Behaviour is normative in `specs/example-cache/spec.md` and `specs/example-backfill/spec.md`.
This note records the mechanism and the decisions behind it, and restates neither.

> History. Several mechanisms described below were replaced on the same branch: the per-pass cap
> and the `pr-str` key material by `2026-09-21-backfill-without-a-cap`, and the query for what is
> already queued by `2026-09-22-one-key-one-task-one-theme`. Read those for what holds now.

## Where the cache lives

The server's SQLite database, one table added by `resources/migrations/003-example-cache.sql`. The
alternative — a process-local map — dies with every restart and is not shared by two server
processes; a generated sentence is worth keeping across both.

The row stores the request's digest as primary key, the normalized word, glosses and context beside
it, and the example as the JSON the endpoint already serves. Storing the serialized body rather than
its parts keeps the hit path to one read and one write of bytes the endpoint would otherwise build
again, and leaves the table readable by hand.

Digest, not the composed string, as the key: the composed string's length is unbounded, and SQLite
indexes a 64-character digest at a fixed cost. The string is `pr-str` of `[word glosses context]`
rather than the three joined by a separator. Any separator can be typed into a gloss — with a unit
separator between glosses, one gloss holding it composes the key of a request with two — and printing
escapes exactly what would otherwise imitate structure. The readable `translations` column joins with
a comma, which is ambiguous and does not have to be anything else: only the digest identifies a row.

## Why the glosses are sorted for the key and not for the prompt

The prompt keeps the order the client sent, because the provider reads the first gloss as the leading
sense. The key sorts them, because the order the client sends depends on the order translations were
merged on that device and carries no meaning across devices. The consequence is deliberate: two
devices whose gloss order differs share one cached sentence, generated from whichever order asked
first.

## Why no TTL and no invalidation

A cached sentence is derived from the request, not from anything that later changes. The one thing
that could invalidate it — a changed translation — changes the key, so the next request misses and
generates. Regenerating the example a word already holds when its translation changes is a client
concern and is #415.

## Where backfill is decided and who triggers it

The decision is a use case (`use-cases.examples/backfill!`): it spans three repositories — words,
collections and examples — and belongs above all of them. The selection itself is a pure function
over what those repositories returned, so the rules are testable without a database.

`sync.cljs` is the engine and knows no repository. It calls a function it was handed, wired in
`main.cljs` as its own component over the three ports. That keeps the dependency pointing the way the
layering test describes and avoids the cycle that `:app/capabilities` -> `:sync/identity` would
otherwise create.

The hook runs after every completed pass, not only after a pass that pulled documents. Backfill
queues tasks into `device-db`, which nothing replicates, so a capped pass leaves a remainder that no
future pull would announce; running on every pass is what lets that remainder drain.

## Why the cap is 20

The task queue runs three tasks at a time and reads 50 due tasks per cycle. Twenty new tasks per pass
stay inside one page, keep at most three requests in flight, and bound what a first synchronisation
of a large vocabulary can turn into: a pass is throttled to one per 30 seconds, so the ceiling is
roughly 40 fetches a minute even when the user navigates constantly. A larger cap buys nothing — the
queue's own concurrency, not the queue's length, is what paces the provider — and a smaller one makes
a vocabulary of several hundred words take implausibly many passes.

Dedup against tasks already queued is what makes the cap safe to re-run: without it a pass repeated
before the queue drained would queue the same twenty again.

## What the backfill costs per pass

Four local reads: the vocabulary preview view, the collection documents, the examples of those words,
and the queued tasks. All of them are reads the app already makes elsewhere on a data page. They run
after the network part of the pass, so they add to a pass's duration but never to its latency budget.
