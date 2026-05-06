# Dictionary RU Enrichment

Fill missing Russian translations in `dictionary-entries.jsonl` via OpenRouter.

Existing RU translations stay unchanged.

## Build

```bash
go build -o enrich-translations .
```

## Env

```bash
export OPENROUTER_API_KEY=...
export OPENROUTER_MODEL=deepseek/deepseek-v4-flash
export OPENROUTER_MODELS='deepseek/deepseek-v4-flash-20260423@deepseek!'
```

- `OPENROUTER_API_KEY` required
- `OPENROUTER_API_URL` optional
- `OPENROUTER_MODEL` primary
- `OPENROUTER_MODELS` comma-separated fallbacks

Provider pin: `model@provider!`.

## Dry Run

```bash
./enrich-translations \
  --dry-run \
  --input ../../../resources/dictionary/dictionary-entries.jsonl \
  --limit 3 \
  --batch 3
```

No network. No writes.

## Run

```bash
./enrich-translations \
  --input ../../../resources/dictionary/dictionary-entries.jsonl \
  --checkpoint .enrich_checkpoint.json \
  --fail-log .enrich_failures.jsonl \
  --crash-log .enrich_crashes.jsonl \
  --recent-log .enrich_recent.jsonl \
  --batch 64 \
  --recent-limit 256 \
  --delay 0 \
  --max-delay 60 \
  --rate-limit-buffer 10 \
  --max-retries 2
```

Default output: in-place (`--output` omitted means `--input`).

## Resume

Rerun same command.

- output file is source of truth
- checkpoint keeps counters/state
- failed/no-parse rows stay unresolved and logged

## Behavior

- Selects entries with no RU translation.
- Prompt uses word, POS, `meta.senses`, machine tags.
- Validates JSON shape, Cyrillic/Russian-looking output, verb infinitives.
- Updates manifest when rewriting `resources/dictionary/dictionary-entries.jsonl`.
- Adds `enriched-at`; keeps original `generated-at`.

## Limits

- Reads `Retry-After`, `X-RateLimit-Reset`, OpenRouter reset metadata.
- Waits until reset plus `--rate-limit-buffer`.
- Raises delay after `429`, capped by `--max-delay`.
- Relaxes delay after successful batches.

Worst-case requests:

```text
ceil(limit / batch) * max-retries * model-count
```

No `--limit` means all missing RU entries.

## TUI

- total missing/enriched/remaining
- current model, delay, cooldown
- current batch/error/response snippet
- full-file progress from checkpoint `initial_missing`
- scrollable recent result table
