## Context
The app needs a predefined German dictionary to support autocomplete, canonical spelling, and best-effort error checking when users add vocabulary. Updates to base dictionary are rare and should not require polling. The app remains offline-first and avoids collecting user data.

## Goals / Non-Goals
- Goals:
  - Provide a base German dictionary (CEFR A1-C2) with RU translations and metadata.
  - Fast autocomplete that matches inflected forms and suggests canonical values.
  - Load dictionary from public CouchDB server with automatic retry on interruption.
  - Keep user data (vocab/reviews) syncable and separate from device-only data.

- Non-Goals:
  - Full morphological analysis or exhaustive conjugation tables.
  - Automatic translation frequency inference without source data.
  - UI for dictionary sync (happens silently in background).
  - Dictionary write access for clients (read-only for public content).

## Decisions
- Separate databases:
  - `user-db`: vocab + reviews (local only, syncable in future).
  - `device-db`: tasks + examples (local only).
  - `dictionary-db`: dictionary entries/forms/state (synced from public server).

- CouchDB infrastructure:
  - Install CouchDB latest stable on production server.
  - Admin credentials stored via systemd encrypted creds.
  - `dictionary-db` is public read-only (no auth required for clients).
  - User databases (future) use proxy auth via `/auth/check`.

- Nginx configuration:
  - Route `/db/*` to CouchDB.
  - Rate limiting: 10 req/s, burst 10.
  - No authentication for `dictionary-db`.
  - Proxy auth for user databases (future).

- Use two document types in `dictionary-db`:
  - `dictionary-entry` for canonical values and translations.
  - `surface-form` for fast prefix lookup of inflected forms.

- Document IDs encode lookup keys:
  - Entry docs use `_id: "lemma:<normalized-value>:<pos>"` (deterministic, handles homonyms via article).
  - Surface-form docs use `_id: "sf:<normalized-form>"`.
  - Enables `allDocs` key-range queries on the built-in primary B-tree index.

- Surface-form docs inline entry data:
  - Each `sf:` doc contains `entries[]` with denormalized `{lemma-id, lemma, rank}`.
  - Fast prefix lookup and deduplication happen from the surface-form query.
  - Translation enrichment uses a single batch `allDocs` lookup by `lemma-id` (no per-suggestion requests).
  - Safe because dictionary is server-generated, read-only on client — no consistency risk.

- CEFR-based ranking:
  - Each entry includes a `rank` field (integer).
  - Computed server-side from SUBTLEX-DE subtitle frequency when available, falling back to CEFR level (A1 highest) plus source richness.
  - Autocomplete results sorted with exact matches first, then by rank after deduplication.
  - Ensures common/beginner words appear first.

- Russian translation enrichment:
  - Ingestion keeps otherwise-valid German lemmas even when Kaikki has no Russian translation.
  - Missing RU translations are filled by a resumable server-side Go runner, not by client devices.
  - Compact source senses (`meta.senses[].glosses/tags`) are stored so the runner can ask the model to translate the intended German meaning instead of a nearby homonym.
  - The runner validates cheap formal constraints locally: JSON shape, Russian/Cyrillic output, and verb translations shaped like Russian infinitives.
  - The runner refreshes manifest stats for `dictionary-entries.jsonl` and records `enriched-at` when it rewrites manifest-managed artifacts.
  - Failed/no-parse rows stay unresolved and are logged for follow-up; they are not written as trusted translations.

- Import validation:
  - Empty `translation` is valid unresolved state.
  - Import validates artifact integrity and document shape, warns about unresolved translations, and does not drop or rewrite docs during import.

- Store `normalized-value` (and normalized form values) for indexed lookup; keep canonical `value` for display.

- Task documents use `data` payloads; `word-id` moves into `data` when needed.

- Prefill add-word input only on exact normalized match; exact match is a selectable default, not an automatic replacement.

- Autocomplete UI behavior:
  - `word-autocomplete` custom element manages suggestions, keyboard navigation, and reset.
  - Arrow keys move selection; Tab selects; Escape clears.
  - Translation hint appears as a ghost value when the translation input is empty.
  - Selecting a suggestion sets the German input to the lemma and fills translation when present.
  - UI uses the `/js/word-autocomplete.js` asset and standard HTMX swaps (no morph extension usage).

- Dictionary sync strategy:
  - Trigger `dictionary-sync/ensure-loaded!` from SW `activate` after migrations/task runner startup.
  - Also trigger `dictionary-sync/ensure-loaded!` from request interceptor when loader state is not ready.
  - Each invocation starts a one-shot `PouchDB/replicate.from` with `live: false`.
  - Sync runs in background, non-blocking.
  - After replication, verify `dictionary-meta` exists before marking sync state ready.
  - PouchDB handles interruptions, retries, and checkpoints automatically.
  - No UI feedback, dev-only logging.
  - Retries happen on subsequent `ensure-loaded!` invocations (next request and next startup).

- Service worker update strategy:
  - New SW probes active SW via `BroadcastChannel` to detect manual update support.
  - If the active SW responds (new code with responder), the waiting SW defers to user button click ("Обновить").
  - If no response within 500ms (old code without responder), the waiting SW calls `skipWaiting()` automatically.
  - Both probe and responder use a single shared `BroadcastChannel` per SW; `postMessage` excludes the sender object, preventing self-messaging.

- Dictionary import sets public read permissions:
  - After creating/resetting `dictionary-db`, the import tool sets `_security` with empty `members` to allow unauthenticated read access.
  - Prevents CouchDB's default behavior of restricting new databases to `_admin` members.

- Database migration:
  - Migrate `local-db` to `user-db` + `device-db`.
  - Keep `local-db` for safety (remove later).
  - Migration marker prevents repeated runs.

- Testing approach:
  - Automated tests cover `dictionary/suggest` behaviors.
  - Test documentation with manual QA scenarios for sync and UI behaviors.

## Alternatives considered
- Single database with `type` filters (rejected: sync bloat and tighter coupling).
- HTTP chunked loading with retry (rejected: CouchDB replication handles this natively with less complexity).
- Static token authentication (rejected: Overkill for public read-only dictionary).
- Per-install tokens (rejected: Unnecessary complexity for public content).
- CouchDB backup for dictionary (rejected: Re-import from source files is simpler).
- `find()` with secondary index on `normalized-value` for autocomplete (rejected: cold start problem — first query after replication triggers full index rebuild of ~150K docs; `allDocs` key-range uses the built-in primary B-tree with zero index overhead).
- Foreign-key reference (`base-id`) in form docs requiring per-suggestion lookup (rejected: N+1 performance cost during autocomplete; denormalized `entries[]` handles the primary lookup and a single batch fetch attaches translations).
- Two separate databases for dictionary entries and surface-forms (rejected: adds operational complexity with no benefit; single `dictionary-db` with `_id` prefix conventions provides equivalent query isolation).

## Risks / Trade-offs
- Dictionary size and index overhead increase storage usage; mitigated with sizing reports and measured import.
- Source licensing may restrict redistribution; mitigated by a source/license matrix before ingestion.
- More DBs add operational complexity; mitigated with clear module boundaries and naming.
- Public read-only access means dictionary can be scraped; mitigated by Nginx rate limiting.
- CouchDB server required (not previously present); mitigated by straightforward setup and alignment with future user sync.

## Migration Plan
- On first run after the change, migrate existing `local-db` content:
  - Move vocab + reviews to `user-db`.
  - Move tasks + examples to `device-db`.
  - Leave a marker doc to avoid repeated migration.
- Keep `local-db` for fallback until migration completes; delete later if safe.
