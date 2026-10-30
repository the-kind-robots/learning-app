## Why

Examples and the word hints of a lesson come from the backend, which asks the LLM for Russian and keeps only Russian glosses. Since #473 the learner speaks ru, en, tr or fr; a learner who does not speak Russian gets Russian sentences (#475).

## Changes

- An example request carries the learner's language; the backend asks for the sentence's translation and every word's gloss in it and accepts only those. A request without one is Russian, as every earlier client asked.
- The dictionary's glosses, which are Russian, pick the entry of a word only for a Russian request.
- The cache keys an example by its language too; earlier rows are Russian and are re-keyed once at startup.
- An example document remembers its language. The lesson, the word dialog and the backfill see only the examples of the learner's language, so a switch fetches the missing ones in the new language and keeps the old ones for a switch back. A document without a language is Russian.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `examples`: an example is generated in the language the request asks for.
- `example-cache`: the key and the stored row carry the language.
- `examples-schema`: an example document carries its language; the reads see the learner's.
- `example-backfill`: a pair misses an example when it has none in the learner's language.

## Decisions

- Old examples are kept and hidden while the learner speaks another language (the owner's call) — over showing them to everyone, which leaves Russian on a non-Russian screen.
- The language names in the prompt are the interface dictionaries' own (`Türkçe (tr)`) — over a second table of English names.
- The language is part of the cache key for Russian too, and the migration marks every row for the startup re-key — over keeping Russian keys as they are by a special case in the digest.
- The learner's own translations of their words stay `"ru"` here: storing and reading them in the chosen language is #474.
