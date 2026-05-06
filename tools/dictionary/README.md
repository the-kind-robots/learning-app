# Dictionary Tool

Standalone pipeline for building a German→Russian dictionary from open sources.

All commands run from `tools/dictionary/`.

---

## Stages

### 1. Word Frequencies — _optional, external data_

Skip if `data/frequency.tsv` already exists.

Download source: [SUBTLEX-DE on OSF](https://osf.io/py9ba/files/osfstorage) (`SUBTLEX-DE cleaned version with Zipf values.xlsx`). Do not commit it.

```bash
clojure -T:word-frequencies generate \
  :input '"/path/to/SUBTLEX-DE cleaned version with Zipf values.xlsx"' \
  :output '"data/frequency.tsv"'
```

Details: `word-frequencies/README.md`

---

### 2. Build Dictionary — _mandatory_

Requires `data/frequency.tsv` (Stage 1) or an existing file from a prior run.

```bash
clojure -T:build build \
  :output-dir '"<output-dir>"' \
  :frequency-file '"data/frequency.tsv"'
```

Writes to `<output-dir>`:
- `dictionary-entries.jsonl`
- `surface-forms.jsonl`
- `manifest.edn`

Commit/deploy generated dictionaries as gzip:

```bash
gzip -n -9 -c resources/dictionary/dictionary-entries.jsonl > resources/dictionary/dictionary-entries.jsonl.gz
gzip -n -9 -c resources/dictionary/surface-forms.jsonl > resources/dictionary/surface-forms.jsonl.gz
```

---

### 3. Enrich RU Translations — _optional, costs money_

Fills missing Russian translations via LLM (OpenRouter). Edits `dictionary-entries.jsonl` in-place. Idempotent — existing translations untouched.

**Build once:**

```bash
cd enrich-translations && go build -o enrich-translations . && cd ..
```

**Set credentials:**

```bash
export OPENROUTER_API_KEY=...
export OPENROUTER_MODEL=deepseek/deepseek-v4-flash
```

**Run** (temp files default to `enrich-translations/`):

```bash
enrich-translations/enrich-translations --input <dictionary-entries.jsonl>
```

Resume: rerun same command. Temp files are gitignored.

Cost estimate: depends on model and missing entry count. Use `--dry-run --limit 10` first.

Details: `enrich-translations/README.md`

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
