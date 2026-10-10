# 0021. Examples are fetched from memory, with no stored queue

- Status: accepted, supersedes ADR-0020
- Date: 2026-10-07
- Supersedes: ADR-0020 (only its decisions that the example fetches are stored tasks held back until the move and the first pass, and that a stored fetch re-checks its pair by a read of id prefixes; its decisions on where examples live, their ids, the move and memory following user-db stand)

## Context

ADR-0020 left the task queue in place: one task document per pair of entry and collection in device-db, an index to select due tasks, a replay of what each replication pass brought, and a re-check of user-db before each request, because the decision to fetch was made when the task was queued. Every one of those writes and index updates ran on the main thread (#518). A failing endpoint kept every task retried for the life of the device.

Since examples replicate, memory already holds what the device is missing. The stored queue only restated it, with a delay.

What the device must do is stated in the specs `example-backfill`, `example-fetch-error-clarity`, `learner-data-memory` and `data-model`. This records why.

## Decision

- No stored queue. A loop in the page asks for the pairs memory has no example for, one request at a time. It keeps what it is doing in an atom; a reload starts it afresh.
- The loop reads memory in its own order, a chunk per task, and asks for the first pair without an example. Examples may arrive at any pace and in any order; what matters is that every pair is asked for in the end. Memory knows nothing about fetching.
- The loop looks again after each request. With nothing to ask it sleeps until memory's words or collections change or the page is active again (shown, online). A new review or example changes neither, so it ends no wait. There is no periodic look, and the vocabulary use case does not know the fetcher exists.
- An unexpected exception in a step is an outage: it is logged and paused like any other.
- A tab fetches while it is visible and online, and only then. Two visible tabs may both fetch: an example has one id on every device and the server generates one subject once at a time, so a second request costs a cache hit.
- Every failure has one fixed effect, and nothing retries forever: a failure that says something about the pair marks its subject for the page's life; a failure of the server, the provider or the network says nothing about the pair, marks nothing and pauses, honouring `Retry-After`, and a run of them never stops the tab; an authentication refusal stops the tab until the page is reloaded.
- Each received example is saved at once.
- Leftover tasks and the indexes earlier builds kept in device-db are deleted by the start-time tidying that already moves device-db's examples.

Rejected: keeping the queue and fixing its retry policy. The writes and the index are the cost (#518), and the queue would still duplicate what memory holds.

Rejected: a Web Lock so that one tab of a profile fetches. It added a turn to request, wait for, hand over and give up, for a case — two visible tabs — whose cost is a duplicate request the server answers from its cache.

Rejected: a grace window before asking for a pair that arrived by replication without its example. The duplicate request costs one cache hit; a window would need its own timer and state.

Rejected: an event-driven fetcher with several requests in flight, a walk newest first, batched saves. It was a state machine for a background job that may take any time; the loop is shorter and each rule is a line of it.

## What the fetcher waits for

Each wait has one thing that ends it, and the step that chose the wait carries it. After a request the fetcher pauses for the spacing or the back-off. With nothing missing it waits until memory's words or collections differ from the snapshot it looked at (a word added, a translation edited, replication bringing either); a review or an example is no difference. When the page is not active (hidden or offline) it waits until it is. There is no periodic look. The log line `:examples/waiting` names which wait it is: `:wait/pause`, `:wait/memory-change` or `:wait/active`. What the fetcher then does is stated in [example-backfill](../openspec/specs/example-backfill/spec.md).

## Consequences

- device-db holds the identity and the migration records only; no index anywhere.
- `sync` no longer publishes passes; it exposes the first pass of a start instead.
- Memory is unchanged by example fetching. Each look reads memory from its first entry, in chunks of a few milliseconds.
- A device without an account fetches nothing.
- Failures are not remembered across reloads; a reload asks again.
