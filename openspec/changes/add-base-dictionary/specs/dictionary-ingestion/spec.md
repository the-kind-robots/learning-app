## Purpose
Defines how raw dictionary sources are transformed into dictionary-entry and surface-form documents by the ingestion pipeline.

## ADDED Requirements

### Requirement: Pipeline ingests two external sources
The system SHALL download and process two external data sources to build the dictionary.

#### Scenario: Kaikki provides lemmas, forms, and translations
- **WHEN** the pipeline runs
- **THEN** it downloads the Kaikki German extract (a gzipped JSONL file from wiktextract)
- **AND** Kaikki records supply lemmas, parts of speech, inflected forms, and translations

#### Scenario: Goethe provides CEFR level reference
- **WHEN** the pipeline runs
- **THEN** it downloads the Goethe-Institut A1/A2/B1 stems CSV
- **AND** the Goethe index maps lowercase stems to CEFR levels ("a1", "a2", "b1")

### Requirement: Only eligible entries become dictionary entries
The system SHALL filter Kaikki records so that valid German lemmas are ingested even when upstream Russian translations are absent.

#### Scenario: German language filter
- **WHEN** a Kaikki record has `lang_code` other than `"de"`
- **THEN** the record is skipped
- **AND** when `lang_code` is `"de"`, the word must use the standard German alphabet (letters, umlauts, eszett, common loanword accents, spaces, hyphens, apostrophes)

Example: `"der Hund"` passes; `"犬"` is skipped.

#### Scenario: Lemma entries only
- **WHEN** every sense of a Kaikki record carries a `form_of` tag (indicating it is an inflected-form redirect, not a headword)
- **THEN** the record is skipped
- **AND** records where at least one sense lacks `form_of` are kept as lemma entries

Example: The record for "ging" (past tense of "gehen") where all senses point to "gehen" is excluded; the record for "gehen" itself is kept.

#### Scenario: Missing Russian translation is allowed
- **WHEN** a Kaikki record has no translations with `lang_code` `"ru"`
- **THEN** the transformed dictionary entry is kept
- **AND** its `translation` vector is empty so the enrichment runner can fill it later

#### Scenario: Unsupported parts of speech are skipped
- **WHEN** an entry has POS outside the supported dictionary POS set
- **THEN** the entry is skipped
- **AND** proper names and unknown POS values are skipped even if other metadata is present

### Requirement: Duplicate entries are merged
The system SHALL merge multiple Kaikki records that share the same word and POS into a single entry.

#### Scenario: Same word+POS from multiple Kaikki records
- **WHEN** the pipeline encounters two or more Kaikki records with the same `[word, pos]` key
- **THEN** their translations are unioned (duplicates removed)
- **AND** their inflected forms are unioned (duplicates removed)
- **AND** their senses are unioned (duplicates removed)
- **AND** a single merged entry is carried forward

Example: Two records for "Bank" as a noun (one with financial senses, one with furniture senses) merge into one entry with both sense sets.

### Requirement: Each entry receives a CEFR level via the Goethe index
The system SHALL assign a CEFR level to entries by matching against the Goethe stem index.

#### Scenario: Longest-prefix stem matching
- **WHEN** a dictionary entry's word is looked up in the Goethe index
- **THEN** the system finds all stems that are a prefix of the lowercased word
- **AND** selects the longest matching stem
- **AND** assigns the corresponding CEFR level ("a1", "a2", or "b1")

Example: For "Hundefutter", stems "hund" (a1) and "hundefutter" would both match; the longest prefix wins. For "Hund", the stem "hund" matches, yielding "a1".

#### Scenario: No match yields no CEFR level
- **WHEN** no Goethe stem is a prefix of the lowercased word
- **THEN** the entry has no CEFR level (nil)

### Requirement: Entries are ranked for autocomplete ordering
The system SHALL compute a numeric rank for each dictionary entry so that empirical frequency drives autocomplete and enrichment ordering when available.

#### Scenario: SUBTLEX-DE subtitle frequency ranks highest
- **WHEN** an entry matches the optional SUBTLEX-DE frequency input
- **THEN** its rank is derived from the frequency rank
- **AND** more frequent words receive higher dictionary rank values
- **AND** the entry metadata records the frequency source, rank, and count when available

#### Scenario: CEFR and richness rank missing-frequency entries
- **WHEN** an entry does not match the frequency input but has a CEFR level
- **THEN** its fallback rank starts from a base value determined by its level (A1 highest, then A2, then B1)
- **AND** additional points are added based on sense count and translation count

#### Scenario: Non-CEFR entries are ranked by richness with a cap
- **WHEN** an entry has neither frequency data nor CEFR level
- **THEN** its rank is computed from sense count and translation count
- **AND** the rank is capped so it never exceeds the minimum possible CEFR rank

#### Scenario: Richer entries rank higher within a tier
- **WHEN** two entries share the same CEFR level (or both lack one)
- **THEN** the entry with more senses and translations receives a higher rank

### Requirement: Text is normalized for lookup
The system SHALL normalize text to enable case-insensitive, diacritic-insensitive matching.

#### Scenario: Case folding and German-character ASCII mapping
- **WHEN** text is normalized
- **THEN** the system lowercases the text
- **AND** maps German special characters to ASCII equivalents (ae, oe, ue, ss)
- **AND** collapses whitespace and trims

Example: `"der Hund"` normalizes to `"der hund"`; `"Straße"` normalizes to `"strasse"`; `"Ärger"` normalizes to `"aerger"`.

### Requirement: Surface forms are generated for autocomplete
The system SHALL produce surface-form entries for every text variant a user might type when searching for a word.

#### Scenario: Lemma value is a surface form
- **WHEN** a dictionary entry is created
- **THEN** the entry's display value becomes a surface form

Example: For the entry "gehen" (verb), `"gehen"` is a surface form.

#### Scenario: Nouns get article-prefixed and bare-word forms
- **WHEN** a dictionary entry is a noun with a gender
- **THEN** the article-prefixed value (e.g., `"der Hund"`) is a surface form
- **AND** the bare word without article (e.g., `"Hund"`) is also a surface form

Example: The noun entry with value `"der Hund"` produces surface forms for both `"der hund"` and `"hund"` (after normalization).

#### Scenario: Valid inflected forms become surface forms
- **WHEN** a Kaikki entry has inflected forms
- **THEN** each valid form becomes a surface form pointing back to the lemma entry
- **AND** each surface-form document's entries are sorted by rank descending

Example: The entry "der Hund" produces surface forms for inflections like `"hundes"`, `"hunde"`, `"hunden"`.

#### Scenario: Invalid forms are excluded
- **WHEN** an inflected form fails validation
- **THEN** it is not generated as a surface form
- **AND** validation excludes:
  - Forms shorter than 2 characters
  - Forms containing non-German characters
  - Auxiliary verb markers ("haben", "sein", "werden")
  - The lemma word itself (already covered by the lemma surface form)

### Requirement: Pipeline produces JSONL output with manifest
The system SHALL write output as JSONL files accompanied by a manifest for integrity verification.

#### Scenario: dictionary-entries.jsonl contains one document per lemma+POS
- **WHEN** the pipeline completes
- **THEN** `dictionary-entries.jsonl` is written with one JSON object per line
- **AND** each line represents a dictionary-entry document with `_id` following the `lemma:<lowercase-entry-value>:<pos>` convention
- **AND** normalized lookup value is stored separately in `meta.normalized_value`
- **AND** keys are serialized as snake_case (converted from internal kebab-case)
- **AND** entries are ordered by `rank` descending
- **AND** entries MAY include compact `meta.senses[]` with machine `tags[]` and source `glosses[]` for offline enrichment hints

Example line (abbreviated):
```json
{"_id":"lemma:die größe:noun","type":"dictionary-entry","value":"die Größe","pos":"noun","rank":30012,"translation":[{"lang":"ru","value":"размер"}],"forms":["Größen"],"meta":{"normalized_value":"die groesse","cefr_level":"a1","gender":"die","senses":[{"tags":["measurement"],"glosses":["size"]}],"sense_count":3,"source":"kaikki-enwiktionary"}}
```

### Requirement: Missing Russian translations are enriched offline
The system SHALL provide a resumable server-side enrichment runner for dictionary entries that still lack Russian translations.

#### Scenario: Enrichment selects only missing RU entries
- **WHEN** the enrichment runner processes `dictionary-entries.jsonl`
- **THEN** it selects only entries whose `translation` vector contains no Russian item
- **AND** existing Russian translations are left unchanged

#### Scenario: Source senses guide translation quality
- **WHEN** an entry has compact source senses in `meta.senses`
- **THEN** the enrichment prompt includes those German glosses and machine tags as meaning hints
- **AND** translations must match the entry part of speech and source senses

#### Scenario: Long runs are resumable and inspectable
- **WHEN** the enrichment runner is interrupted or an API request fails
- **THEN** it records checkpoint, failure, crash, and recent-result logs
- **AND** rerunning with the same input/output/checkpoint continues from the current output file state

#### Scenario: Enrichment refreshes manifest metadata
- **WHEN** the enrichment runner rewrites `dictionary-entries.jsonl`
- **THEN** it updates that file's count, byte size, and SHA-256 in `manifest.edn`
- **AND** it records `enriched-at` without changing the original `generated-at`

#### Scenario: Provider limits are respected
- **WHEN** OpenRouter returns retry or rate-limit metadata
- **THEN** the runner backs off or waits until the reset timestamp plus buffer
- **AND** provider-pinned fallback models can be tried after the primary model fails

### Requirement: Import accepts unresolved translations
The system SHALL treat empty translation vectors as valid unresolved dictionary state during import.

#### Scenario: Empty translations do not block import
- **WHEN** a dictionary entry has `translation: []`
- **THEN** pre-flight import validation accepts the entry
- **AND** import may warn about unresolved translation count
- **AND** import does not drop or rewrite unresolved entries

#### Scenario: surface-forms.jsonl contains one document per normalized form
- **WHEN** the pipeline completes
- **THEN** `surface-forms.jsonl` is written with one JSON object per line
- **AND** each line represents a surface-form document with `_id` following the `sf:<normalized-form>` convention
- **AND** entries within each document are sorted by rank descending

Example line (abbreviated):
```json
{"_id":"sf:hund","type":"surface-form","value":"hund","entries":[{"lemma_id":"lemma:der hund:noun","lemma":"der Hund","rank":30012}]}
```

#### Scenario: manifest.edn records counts, sizes, and checksums
- **WHEN** the pipeline completes
- **THEN** `manifest.edn` is written containing:
  - A generation timestamp
  - An enrichment timestamp after enrichment rewrites manifest-managed artifacts
  - For each output file: document count, byte size, and SHA-256 checksum

### Requirement: Import writes dictionary metadata
The system SHALL store a dictionary meta document after a successful import.

#### Scenario: Dictionary meta document
- **WHEN** the import completes and data is uploaded
- **THEN** the pipeline writes a document in `dictionary-db` with:
  - `_id: "dictionary-meta"`
  - `type: "dictionary-meta"`
  - `schema-version` (integer)
  - `dictionary-epoch` (string)
  - `generated-at` (from the manifest)
  - `enriched-at` when present in the manifest
  - `manifest-sha256` (SHA-256 of the manifest)
  - `files` (the manifest file stats map)
