## Why

Every screen entry and every keystroke in the words filter reads PouchDB again and recomputes from nothing (#494). On a vocabulary of 1500 words and 7100 reviews, home → lesson takes over a second, a filter keystroke 0.7 s, and home, words and lesson switch only after their reads, so the reader keeps looking at the screen they left — and a read that lands after the reader left brings the old screen back over home (#486). The learner's data is well under a megabyte; the owner's budget is every screen, with its data, within one animation frame of the tap.

## What Changes

- The learner's data — words with their search text normalised once, reviews by word, collections, examples — is held in app state as a projection of PouchDB. PouchDB stays the only source: memory takes a write only after PouchDB accepted it, and everything written elsewhere (another tab, a sync pull) arrives through the local change feed.
- Home, words (and its filter), lesson and themes compute their content from memory, synchronously, in the task of the tap; closing a screen renders home in that task too, not after `popstate`.
- What a screen change starts but the screen does not need — the lesson document write, ending the lesson, the sync pull on entry — runs after the screen is painted.
- A screen opened before memory is loaded opens at once and fills in when memory is ready; it claims nothing about the data until then.
- The words filter loses its 400 ms debounce: filtering memory fits a frame.
- **BREAKING (internal)**: the reads a page used to issue on entry, the read tokens that ordered them, and the post-pull page reload are gone; a screen follows memory instead.

## Capabilities

### New Capabilities
- `learner-data-memory`: the learner's data held in app state as a projection of PouchDB, and screens that answer from it within one frame.

### Modified Capabilities
- `vocabulary-list-paging`: the list is cut from memory; the page-read, overtaken-read and "rows arrive later" requirements give way to immediate answers.
- `collections-navigation`: the themes screen shows its loading state only while memory is not ready, and reads no storage on entry.
- `push-sync`: a pull changes the open screen through memory, not by reloading it.
- `main-thread-runtime`: a route entry computes its page from memory instead of loading it.

## Impact

- Client engine (`db.pouch`): the change feed and write notification the projection is built on.
- A new adapter holding the projection; the ports and use cases reading it.
- Pages home, words, lesson, collections; `application` (routes, close to home); `ports.navigation`.
- Browser suite: one-frame specs per transition, consistency specs, the #486 race.
- Stacks on nothing; supersedes the closed #488 and covers #486. Overlaps #504 (words screen shape).
