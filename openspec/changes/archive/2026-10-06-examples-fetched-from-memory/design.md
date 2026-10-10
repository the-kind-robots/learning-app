## Context

Since #528 (ADR-0020) examples replicate through user-db and memory holds them. The stored task queue in device-db was kept only to drive example fetching. What the system must do is stated in the delta specs; this file says how it is built. ADR-0021 records the decision.

## Goals / Non-Goals

**Goals:** a fetcher a reader can follow in one sitting; a failure policy that ends every retry; a read of memory that costs well under a frame on a large vocabulary.

**Non-Goals:** the server's statuses (the server PR under #523); a batch endpoint; ordering the pairs.

## Decisions

**The rule.** `domain.examples` holds `visible-in` and `missing-pairs`: given an entry, the collections that name it and its examples, the pairs of that entry with no example. A lesson reads `visible-in`; the fetcher reads `missing-pairs`. Memory knows nothing about example fetching.

**The loop.** `use-cases.examples` is one async loop with one request at a time. Each turn it takes the first missing pair in memory's own order, 200 entries per task, asks for it, saves the answer, and sleeps 2 s or the pause a failure set. With nothing to ask, or while the tab is hidden or offline, it sleeps 30 s. `wake!` ends that sleep early; `vocabulary/add!` and `vocabulary/update!` call it. Memory is not watched: a word that arrives by replication is found at the next look.

**State.** One atom: the failed subjects, the run of outages, stopped, the request in flight. `after-response` is the failure table as a pure function.

**Readiness.** `ready` awaits the load, the tidying and `Promise.race` of `:sync/first-pass` and a ceiling, then one catch-up of memory. A device without an account does not start the fetcher.

**The browser.** `adapters.example-fetch` builds the URL before the network try, so a subject that cannot be encoded is a pair failure, not a network one; reads `Retry-After` as whole seconds only; tells a body that is not JSON from one that broke off. `on-online` and `on-visibility-change` add the listener they are given and return the function that removes it.

**Saving.** `:learner/save-examples!` builds the document with `documents/example-doc`, writes it with `insert-all-if-absent`, catches memory up and resolves with what was not held. One example, one write.

**Tidying.** `tidy-device-db!` reads device-db pages of examples and tasks, moves the examples of each page, deletes its task tombstones, and removes the queue's index and the old `by-type` index. It goes in a follow-up once the owner's devices have run this build.

## Risks / Trade-offs

- [Every look starts at the first entry, so a long run of answered entries is read again] → 20k entries take about 150 ms of CPU in chunks of a few milliseconds, once per request.
- [Two visible tabs both fetch] → accepted; content ids and the server's single-flight make it a cache hit.
- [A tab hidden mid-request leaves a generation running on the server] → the server caches it; the next request is a cache hit.
- [No failures are remembered across reloads] → intended.

## Migration Plan

No migration: leftover tasks and indexes are deleted at start. Rollback to a build with the queue re-creates its index on its first start.
