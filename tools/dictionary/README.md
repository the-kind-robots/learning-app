# Dictionary Tool

Standalone pipeline for building a German→Russian dictionary from open sources.

All commands run from `tools/dictionary/`.

---

## Stages

### 1. Word Frequencies — _optional, external data_

Skip if `../../resources/dictionary/frequency.tsv` already exists.

Download source: [SUBTLEX-DE on OSF](https://osf.io/py9ba/files/osfstorage) (`SUBTLEX-DE cleaned version with Zipf values.xlsx`). Place it in `../../resources/dictionary/`. Do not commit it.

```bash
clojure -T:word-frequencies generate \
  :input '"../../resources/dictionary/SUBTLEX-DE cleaned version with Zipf values.xlsx"'
```

Details: `word-frequencies/README.md`

---

### 2. Build Dictionary — _mandatory_

Requires `frequency.tsv` (Stage 1) or an existing file from a prior run. All files read and written to `../../resources/dictionary/` by default.

```bash
clojure -T:build build
```

Writes:
- `dictionary.sqlite` — client dictionary
- `enrichment-meta.jsonl` — lemma metadata for enrichment (Stage 3 input)
- `manifest.edn`

---

### 3. Enrich RU Translations — _optional, costs money_

Fills missing Russian translations via LLM (OpenRouter). Writes results to a side file; does not modify `dictionary.sqlite` directly (that's Stage 4). Idempotent — existing translations untouched.

**Build once:**

```bash
cd enrich-translations && go build -o enrich-translations . && cd ..
```

**Set credentials:**

```bash
export OPENROUTER_API_KEY=...
export OPENROUTER_MODEL=deepseek/deepseek-v4-flash
```

**Run:**

```bash
enrich-translations/enrich-translations \
  --input ../../resources/dictionary/dictionary.sqlite \
  --enrichment-output ../../resources/dictionary/enrichment-output.jsonl
```

`enrichment-meta.jsonl` must be in the same directory as `dictionary.sqlite`.

Resume: rerun same command. Temp files are gitignored.

Cost estimate: depends on model and missing entry count. Use `--dry-run --limit 10` first.

Details: `enrich-translations/README.md`

---

### 4. Apply Enrichment — _mandatory after Stage 3_

Patches `dictionary.sqlite` with translations from `enrichment-output.jsonl`. Gap-fill only: lemmas that already have translations are skipped.

```bash
clojure -T:build apply-enrichment!
```

---

## Tests

```bash
clojure -M:test
```

```bash
cd enrich-translations && go test ./...
```

---

## Docs

- `docs/glossary.md`
- `word-frequencies/README.md`
- `enrich-translations/README.md`
