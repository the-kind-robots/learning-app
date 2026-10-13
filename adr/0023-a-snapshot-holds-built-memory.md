# 0023. A snapshot holds built memory; memory keeps no entries and no revisions

- Status: accepted
- Date: 2026-10-12
- Supersedes: ADR-0018 (in part)

## Context

ADR-0018 stored the entries memory took from each document, because built memory was measured slower to write and its shape changes with every optimisation. At 20k words and 200k reviews that is 220,038 entries, and taking them again through the add path cost about 2.9 s of a 3.2 s start from the snapshot (#508). What a snapshot holds and what restored memory must read as are stated in `openspec/specs/learner-data-memory/spec.md`, requirement "A repeat start reads memory from a snapshot". This records the choices behind it.

## Decision

- The body is memory as it stands, less the word list and the feed position (`memory/stored`). The word list is rebuilt from the cards on restore (`memory/restored`), the position comes from the header. A review history is written by a transit handler as its three columns and read back into typed arrays.
- The cost of the shape is paid by the format version: it changes whenever the shape of memory changes. The golden test names it.
- Memory keeps no entries. A change needs the version it holds to remove it, and memory finds that by the document's id, in the collections, the examples or the cards. A review is never removed or replaced: the app only creates reviews, so one already in its word's history is left as it is. One mechanism serves a fresh load, a restore, the feed and a catch-up, so nothing rebuilds in the background and nothing waits for it.
- Memory keeps no revisions. A revision memory holds already is removed and added again and gives an equal memory, at most one more render. Keeping an id-to-revision map was measured at 20k / 200k, release build, headless Chrome: the restore blocked the main thread about 300 ms more (595-718 ms against about 300 ms), the write about 670 ms more (1158 ms against 490 ms), and the snapshot doubled (24.6 M characters against 12.0 M). The start to home moved by 20-100 ms only, because the decode overlaps the wait for IndexedDB. That is over the 200 ms the restore could spend, so the map is not kept.
- The write stays one blocking task.

## Consequences

- A repeat start at 20k / 200k takes about 1.1 s, against 3.0-3.2 s; the snapshot is 12.0 M characters against 36.7 M; the write blocks about 0.5 s against 2.0 s.
- The format is tied to the shape of memory, so a change to memory sends every device to one full read, 15 s at this size. Optimising memory is no longer free.
- Reviews are immutable in memory. Whoever lets the app edit or delete a review must give that change a path of its own.
- The restore is a block of about 300 ms (decode, then the word list) before the catch-up.
