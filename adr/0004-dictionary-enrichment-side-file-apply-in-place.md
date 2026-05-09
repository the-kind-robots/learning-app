# 0004. Dictionary translation enrichment: side-file authoritative, apply in-place

- Status: accepted
- Date: 2026-05-08

## Context

The Kaikki dump contains Russian translations for many German lemmas but not all. An LLM-based enrichment step fills the gaps. The enrichment run takes approximately a week on a dedicated machine and is expensive in API cost. The pipeline needs to handle:

- Incremental enrichment (resume after interruption)
- Kaikki rebuilds (new dump, same translations) without re-running the API
- The case where kaikki one day ships complete translations (enrichment becomes a no-op)
- Cross-machine workflow (build runs locally, enrichment runs on a separate long-lived host)

An earlier design used a `dictionary-raw.sqlite` intermediate artifact that was mutated by the enrichment tool and then exported to the client-facing `dictionary.sqlite`. This conflated the parse cache with the translation store and made the boundary between "what kaikki provides" and "what enrichment adds" implicit.

## Decision

`dictionary.sqlite` is the single **client** artifact. It is built directly from the kaikki dump, contains a `translations` table (empty rows = untranslated), and is deployable as-is without enrichment.

The build also emits `enrichment-meta.jsonl` — a **build-machine support file** containing `{id, value, pos, senses, cefr_level, sense_count, forms_count}` for every lemma. This file provides LLM context and priority data to the enrichment tool. It is regenerated on every build and is not shipped to clients.

The enrichment tool queries `dictionary.sqlite` for lemmas absent from the `translations` table, loads senses context from `enrichment-meta.jsonl`, and writes results to `enrichment-output.jsonl` — the authoritative translation record. The tool has no write access to `dictionary.sqlite`.

`apply-enrichment!` applies the side-file to `dictionary.sqlite` in a single transaction using gap-fill semantics: INSERTs translation rows only for lemmas with no existing translations. Kaikki-sourced translations are never overwritten. Patch records whose `text_id` is absent from the current build are silently skipped.

There is no `dictionary-raw.sqlite`. That name was a faulty conceptualisation that made a regenerable parse cache look like a first-class artifact.

## Consequences

- `enrichment-output.jsonl` survives kaikki rebuilds. If the dump is regenerated, the patch is reapplied with no API calls. `enrichment-meta.jsonl` does not need to survive rebuilds — it is regenerated each time.
- The `text_id` column on `dictionary.sqlite.lemmas` (e.g., `lemma:der-hund:noun`) serves as the stable cross-build key that links `enrichment-output.jsonl` records to lemma rows. It is not used by client queries.
- If kaikki ships complete translations, the enrichment step is not run — no pipeline changes required.
- The enrichment tool has no write access to `dictionary.sqlite`; it only reads. The apply step is a separate, explicit operation.
- `dictionary.sqlite` can be shipped to users before enrichment completes; lemmas with no translation rows render without a Russian gloss.
- The build pipeline has one fewer artifact (`dictionary-raw.sqlite` removed) and one step changed (`emit-enrichment-input!` replaced by `emit-enrichment-meta!`).
